package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.entity.CalendarEventEntity
import com.ezygo.attendance.data.local.entity.CalendarSyncEntity
import com.ezygo.attendance.data.remote.IcsClient
import com.ezygo.attendance.data.remote.IcsResponse
import com.ezygo.attendance.data.remote.ics.IcsParser
import com.ezygo.attendance.domain.model.CalendarEvent
import com.ezygo.attendance.domain.model.EventSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The academic calendar: the institution's published feed, plus whatever the
 * student has noted down themselves.
 *
 * Cache-first throughout. Every reader observes Room, so events render instantly
 * and offline; [sync] writes into the same tables and the screens update
 * themselves. A failed sync leaves the cached calendar exactly as it was.
 */
@Singleton
class CalendarRepository @Inject constructor(
    private val database: AppDatabase,
    private val icsClient: IcsClient,
) {

    // One sync at a time: a pull-to-refresh landing on top of the startup sync
    // would otherwise have both writing the same rows.
    private val syncMutex = Mutex()

    /** Every event, feed and local alike, oldest first. */
    val events: Flow<List<CalendarEvent>> =
        database.calendarDao().events().map { rows -> rows.map { it.toDomain() } }

    /**
     * How fresh the calendar is as a whole: the *oldest* of the feeds' last
     * successful syncs, so the figure on screen never claims more freshness than
     * the stalest thing being shown. Null only when nothing has ever synced.
     */
    val lastSyncEpochMs: Flow<Long?> =
        database.calendarDao().syncStates().map { states ->
            states.minOfOrNull { it.lastSyncEpochMs }
        }

    /**
     * The next [limit] events for the home screen, counted from the start of today
     * so that something happening later today still counts as upcoming.
     *
     * The academic feed is excluded here by design. It is a long reference list of
     * institutional dates, and letting it in would push the batch's own events and
     * the student's own reminders off a card that only has room for four rows.
     */
    fun upcomingForHome(
        limit: Int,
        sources: Collection<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Flow<List<CalendarEvent>> {
        // An empty allow-list means the user turned every calendar off, which SQL's
        // `IN ()` cannot express — so it is answered here rather than by a query that
        // would return everything.
        if (sources.isEmpty()) return flowOf(emptyList())
        val startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        return database.calendarDao()
            .upcomingFrom(startOfToday, sources.toList(), limit)
            .map { rows -> rows.map { it.toDomain() } }
    }

    /**
     * Brings every feed up to date.
     *
     * Each is fetched independently and one failing does not stop the others from
     * updating; the first failure is rethrown afterwards so the screen can still
     * report that something went wrong.
     */
    suspend fun sync(force: Boolean = false): Unit = syncMutex.withLock {
        var failure: Throwable? = null
        for (feed in Feed.entries) {
            try {
                syncFeed(feed, force)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (failure == null) failure = throwable
            }
        }
        failure?.let { throw it }
    }

    /**
     * Skipped entirely when this feed's cache is younger than [SYNC_INTERVAL_MS]
     * and [force] is not set, so opening the app repeatedly costs nothing. When it
     * does run, a stored ETag turns it into a 304 with no body — though neither of
     * these feeds currently returns one.
     */
    private suspend fun syncFeed(feed: Feed, force: Boolean) {
        val dao = database.calendarDao()
        val previous = dao.syncOnce(feed.syncRowId)
        val now = System.currentTimeMillis()

        val isFresh = previous != null && now - previous.lastSyncEpochMs < SYNC_INTERVAL_MS
        if (!force && isFresh) return

        when (val response = icsClient.fetch(feed.url, previous?.etag)) {
            is IcsResponse.Unchanged ->
                dao.upsertSync(
                    CalendarSyncEntity(
                        id = feed.syncRowId,
                        lastSyncEpochMs = now,
                        etag = previous?.etag,
                    ),
                )

            is IcsResponse.Content -> {
                val parsed = IcsParser.parse(response.body).map { event ->
                    CalendarEventEntity(
                        // Prefixed by feed, so the same UID appearing in both
                        // calendars stays two rows rather than one overwriting
                        // the other.
                        id = feed.sourceKey + ":" + event.uid,
                        source = feed.sourceKey,
                        title = event.title,
                        description = event.description,
                        location = event.location,
                        startEpochMs = event.startEpochMs,
                        endEpochMs = event.endEpochMs,
                        isAllDay = event.isAllDay,
                    )
                }
                dao.replaceSynced(
                    source = feed.sourceKey,
                    events = parsed,
                    sync = CalendarSyncEntity(
                        id = feed.syncRowId,
                        lastSyncEpochMs = now,
                        etag = response.etag,
                    ),
                )
            }
        }
    }

    /**
     * Adds one of the student's own events.
     *
     * [time] null means all-day. Nothing about a local event is ever uploaded — it
     * lives on this device only, which is exactly why signing out warns about it.
     */
    suspend fun addLocalEvent(
        title: String,
        date: LocalDate,
        time: LocalTime?,
        note: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ) = saveLocalEvent(
        id = LOCAL_PREFIX + UUID.randomUUID(),
        title = title,
        date = date,
        time = time,
        note = note,
        zone = zone,
    )

    /**
     * Rewrites one of the student's own events in place.
     *
     * Keeping the id is what makes this an edit rather than a second event: the
     * write is an upsert, so the existing row is replaced. Feed events are rejected
     * outright — the next sync would overwrite any change to one.
     */
    suspend fun updateLocalEvent(
        id: String,
        title: String,
        date: LocalDate,
        time: LocalTime?,
        note: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (!id.startsWith(LOCAL_PREFIX)) return
        saveLocalEvent(
            id = id,
            title = title,
            date = date,
            time = time,
            note = note,
            zone = zone,
        )
    }

    private suspend fun saveLocalEvent(
        id: String,
        title: String,
        date: LocalDate,
        time: LocalTime?,
        note: String?,
        zone: ZoneId,
    ) {
        val start = date.atTime(time ?: LocalTime.MIDNIGHT).atZone(zone).toInstant()
        database.calendarDao().upsertEvents(
            listOf(
                CalendarEventEntity(
                    id = id,
                    source = CalendarEventEntity.SOURCE_LOCAL,
                    title = title.trim(),
                    description = note?.trim()?.takeIf { it.isNotBlank() },
                    location = null,
                    startEpochMs = start.toEpochMilli(),
                    endEpochMs = null,
                    isAllDay = time == null,
                ),
            ),
        )
    }

    /**
     * Removes one of the student's own events. Feed events are not deletable: the
     * next sync would bring them straight back, so offering it would be a lie.
     */
    suspend fun deleteLocalEvent(id: String): DeleteOutcome {
        if (!id.startsWith(LOCAL_PREFIX)) return DeleteOutcome.syncedEvent()
        database.calendarDao().deleteEvent(id)
        return DeleteOutcome.Deleted
    }

    /**
     * Drops everything, local events included. Only sign-out calls this, and only
     * after the user has been told what it destroys.
     */
    suspend fun clear() {
        with(database.calendarDao()) {
            clearAllEvents()
            clearSync()
        }
    }

    private fun CalendarEventEntity.toDomain() = CalendarEvent(
        id = id,
        source = when (source) {
            CalendarEventEntity.SOURCE_LOCAL -> EventSource.LOCAL
            CalendarEventEntity.SOURCE_ACADEMIC -> EventSource.ACADEMIC
            // Anything unrecognised is treated as the batch feed, which is what
            // rows written by earlier versions are.
            else -> EventSource.BATCH
        },
        title = title,
        description = description,
        location = location,
        start = Instant.ofEpochMilli(startEpochMs),
        end = endEpochMs?.let(Instant::ofEpochMilli),
        isAllDay = isAllDay,
    )

    /**
     * The calendars this app reads.
     *
     * Both are Google "secret address" feeds: they carry no login, so possession of
     * the URL is itself the credential. They ship inside the APK and can be
     * extracted from it — acceptable for calendars shared with a whole class, but
     * they are not private links, and rotating one means shipping an update.
     *
     * Both are strictly read-only. The server answers `Allow: HEAD, GET` and
     * rejects POST and PUT with 405, so nothing can be written back to them.
     */
    private enum class Feed(val sourceKey: String, val syncRowId: Int, val url: String) {
        /** The batch's own calendar. */
        BATCH(
            sourceKey = CalendarEventEntity.SOURCE_BATCH,
            syncRowId = 0,
            url = "https://calendar.google.com/calendar/ical/" +
                "9ad39df027e05fcfe6db9aca648fb4a515bada6937328735c0409cf0bee1c852" +
                "%40group.calendar.google.com/private-d96e469d2a40c2d85feb11f76b0b99c7/basic.ics",
        ),

        /** The institution's academic calendar. Never shown on the home screen. */
        ACADEMIC(
            sourceKey = CalendarEventEntity.SOURCE_ACADEMIC,
            syncRowId = 1,
            url = "https://calendar.google.com/calendar/ical/" +
                "adfba890cefd166872d8fd463139126fba8640952f62cc47f9224a01394ab323" +
                "%40group.calendar.google.com/private-e0d52fe7fe41f02daa801f18e8f3e023/basic.ics",
        ),
    }

    private companion object {
        /** Marks a row as the user's own; also the guard on edit and delete. */
        const val LOCAL_PREFIX = CalendarEventEntity.SOURCE_LOCAL + ":"

        /** Six hours: an academic calendar changes on the order of days. */
        const val SYNC_INTERVAL_MS = 6L * 60 * 60 * 1000
    }
}
