package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.dao.courseShortCodeKey
import android.util.Log
import com.ezygo.attendance.data.local.entity.ScheduleEntity
import com.ezygo.attendance.data.local.entity.ClassCountsMetaEntity
import com.ezygo.attendance.data.local.entity.SessionCountEntity
import com.ezygo.attendance.data.local.entity.TimetableMetaEntity
import com.ezygo.attendance.data.local.entity.TimetableOverrideEntity
import com.ezygo.attendance.data.local.entity.TimetableSessionEntity
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.remote.EzygoApi
import com.ezygo.attendance.data.remote.dto.MonitoringRequest
import com.ezygo.attendance.data.remote.dto.MonitoringResponse
import com.ezygo.attendance.data.remote.dto.ScheduleDto
import com.ezygo.attendance.data.remote.dto.UserSubgroupDto
import com.ezygo.attendance.data.remote.dto.asIntOrNull
import com.ezygo.attendance.data.remote.dto.asStringOrNull
import com.ezygo.attendance.domain.model.DayStatus
import com.ezygo.attendance.domain.model.Period
import com.ezygo.attendance.domain.model.CourseShortCode
import com.ezygo.attendance.domain.model.ClassSessionKey
import com.ezygo.attendance.domain.model.ScheduleSlot
import com.ezygo.attendance.domain.model.SessionCount
import com.ezygo.attendance.domain.model.TimetableOverride
import com.ezygo.attendance.domain.model.TimetableDay
import com.ezygo.attendance.domain.model.UpcomingTimetable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What came of asking for the cohort report.
 *
 * [NoCohort] is separated from [Failed] because they need different words on screen:
 * one is "the timetable has not loaded yet", which a pull on the timetable fixes, and
 * the other is "the request did not work", which retrying might.
 */
sealed interface ClassCountsResult {
    /** The cache was young enough; nothing was requested. */
    data object UpToDate : ClassCountsResult

    data object Updated : ClassCountsResult

    /** No subgroup or no start date cached yet, so there is nothing to ask about. */
    data object NoCohort : ClassCountsResult

    data class Failed(val message: String) : ClassCountsResult
}

/**
 * How long a cohort report stays good for.
 *
 * Twelve hours: the register changes when staff mark a class, which happens through
 * the day, but nobody is watching it minute to minute. Long enough that opening the
 * class screens twice in an afternoon costs one request, short enough that tomorrow
 * morning's figures are tomorrow morning's.
 */
const val CLASS_COUNTS_MAX_AGE_MS = 12L * 60 * 60 * 1000

/**
 * Whether a cohort report read at [fetchedAtEpochMs] still counts as current.
 *
 * A free function so the rule can be tested without a database and a network behind
 * it — it is the whole policy this feature turns on, and "did we ask too often" is
 * not a question worth answering by hand.
 *
 * Never fetched is never fresh. A timestamp in the future is treated as fresh rather
 * than as an error: a clock that has gone backwards is not a reason to re-ask for a
 * term of data, and it corrects itself within the window.
 */
internal fun classCountsAreFresh(fetchedAtEpochMs: Long?, nowMs: Long): Boolean {
    val fetchedAt = fetchedAtEpochMs ?: return false
    return nowMs - fetchedAt < CLASS_COUNTS_MAX_AGE_MS
}

@Singleton
class TimetableRepository @Inject constructor(
    private val api: EzygoApi,
    private val database: AppDatabase,
    private val shortCodes: CourseShortCodeRepository,
    private val json: Json,
) {

    /**
     * A suspension or holiday. [sessionIds] empty means the whole day is off; the
     * portal can suspend just a few periods, and its `session_id` is an array.
     */
    @Serializable
    private data class NonWorkingDay(
        val date: String,
        val reason: String,
        val sessionIds: List<Int> = emptyList(),
    )

    /**
     * The next [dayCount] days of the cohort's timetable, rebuilt from the cache
     * whenever it changes. Renders offline exactly like the attendance screens.
     */
    fun observeUpcoming(
        period: Period,
        from: LocalDate,
        dayCount: Int = DEFAULT_DAY_COUNT,
    ): Flow<UpcomingTimetable> {
        val dao = database.timetableDao()
        val lastDay = from.plusDays((dayCount - 1).toLong())
        // The user's own two layers are observed alongside the cache, so renaming a
        // course or placing a class redraws the timetable at once rather than waiting
        // for the next refresh to bring the schedule back through here.
        return combine(
            dao.meta(period.key),
            dao.sessions(period.key),
            dao.schedulesBetween(period.key, from.toString(), lastDay.toString()),
            database.timetableOverrideDao().forPeriod(period.key),
            shortCodes.codes,
        ) { meta, sessions, schedules, overrides, codes ->
            UpcomingTimetable(
                subgroupName = meta?.subgroupName,
                days = buildDays(from, dayCount, meta, sessions, schedules, overrides, codes),
                lastUpdatedEpochMs = meta?.lastUpdatedEpochMs,
            )
        }
    }

    /** Every class the user has placed on this period's grid themselves. */
    fun overrides(period: Period): Flow<List<TimetableOverride>> =
        database.timetableOverrideDao().forPeriod(period.key).map { rows -> rows.map { it.toDomain() } }

    suspend fun setOverride(period: Period, override: TimetableOverride) {
        database.timetableOverrideDao().upsert(
            TimetableOverrideEntity(
                periodKey = period.key,
                dayOfWeek = override.dayOfWeek.value,
                sessionId = override.sessionId,
                courseId = override.courseId,
                courseName = override.courseName,
                courseCode = override.courseCode,
                shortCode = override.shortCode,
                instructor = override.instructor,
                deliveryMethod = override.deliveryMethod,
                classType = override.classType,
            ),
        )
    }

    suspend fun removeOverride(period: Period, dayOfWeek: DayOfWeek, sessionId: Int) =
        database.timetableOverrideDao().deleteAt(period.key, dayOfWeek.value, sessionId)

    /** The institution's periods, for a screen that has to offer them as choices. */
    fun periods(period: Period): Flow<List<Pair<Int, String>>> =
        database.timetableDao().sessions(period.key).map { rows ->
            rows.take(TIMETABLE_PERIOD_COUNT).map { it.sessionId to it.name }
        }

    /**
     * The same view as [observeUpcoming], read once.
     *
     * For the widget, which draws one frame from the cache and then stops existing.
     */
    suspend fun upcomingOnce(
        period: Period,
        from: LocalDate,
        dayCount: Int = DEFAULT_DAY_COUNT,
    ): UpcomingTimetable {
        val dao = database.timetableDao()
        val lastDay = from.plusDays((dayCount - 1).toLong())
        val meta = dao.metaOnce(period.key)
        return UpcomingTimetable(
            subgroupName = meta?.subgroupName,
            days = buildDays(
                from = from,
                dayCount = dayCount,
                meta = meta,
                sessions = dao.sessionsOnce(period.key),
                schedules = dao.schedulesBetweenOnce(
                    period.key,
                    from.toString(),
                    lastDay.toString(),
                ),
                overrides = database.timetableOverrideDao().forPeriodOnce(period.key),
                shortCodes = shortCodes.codesOnce(),
            ),
            lastUpdatedEpochMs = meta?.lastUpdatedEpochMs,
        )
    }

    /**
     * Pulls every piece the timetable is assembled from and replaces the cached
     * snapshot for [period].
     *
     * The cohort is picked by matching the period, because a student can belong to
     * subgroups from earlier semesters too and those carry a different timetable.
     */
    suspend fun refresh(period: Period) {
        val subgroups = apiCall { api.userSubgroups() }
        val subgroup = pickSubgroup(subgroups, period) ?: throw ApiError.NoTimetable
        val subgroupId = subgroup.id ?: throw ApiError.NoTimetable

        val definitions = apiCall { api.subgroupTimetables(subgroupId) }
        // Prefer the cohort's default timetable; fall back to the only one there is.
        val definition = definitions.firstOrNull { it.defaultTimetable.asIntOrNull() == 1 }
            ?: definitions.firstOrNull()

        val workdays = apiCall { api.workdays() }.mapNotNull { it.name?.takeIf(String::isNotBlank) }
        val sessions = apiCall { api.sessions() }
        val response = apiCall { api.subgroupSchedules(subgroupId) }

        // The schedule rows carry ids for these; the embedded objects were null in
        // the observed payload, so the lookup tables fill the gap.
        val deliveryNames = apiCall { api.deliveryMethods() }
            .mapNotNull { method -> method.id?.let { it to method.name.orEmpty() } }.toMap()
        val classTypeNames = apiCall { api.classTypes() }
            .mapNotNull { type -> type.id?.let { it to type.name.orEmpty() } }.toMap()

        val nonWorking = extractDatedNotes(response.holidays, "Holiday") +
            extractDatedNotes(response.suspends, "Classes suspended")

        val meta = TimetableMetaEntity(
            periodKey = period.key,
            subgroupId = subgroupId,
            subgroupName = subgroup.name,
            excludedWeekdays = definition?.excludedWeeks.orEmpty().joinToString(SEPARATOR),
            workdays = workdays.joinToString(SEPARATOR),
            startDate = definition?.startDate,
            uptoDate = definition?.uptoDate,
            subgroupStartDate = subgroup.startDate,
            // Stored as JSON: a reason is free text and could contain any delimiter
            // a hand-rolled format might pick.
            nonWorkingDates = json.encodeToString(nonWorking),
            lastUpdatedEpochMs = System.currentTimeMillis(),
        )

        val sessionRows = sessions.mapNotNull { session ->
            val id = session.id ?: return@mapNotNull null
            TimetableSessionEntity(
                periodKey = period.key,
                sessionId = id,
                name = session.name ?: id.toString(),
                viewOrder = session.viewOrder.asIntOrNull() ?: id,
            )
        }

        val scheduleRows = response.schedules.mapNotNull { schedule ->
            toEntity(period, schedule, deliveryNames, classTypeNames)
        }

        database.timetableDao().replaceTimetable(meta, sessionRows, scheduleRows)
    }

    // ---- mapping ---------------------------------------------------------

    private fun toEntity(
        period: Period,
        schedule: ScheduleDto,
        deliveryNames: Map<Int, String>,
        classTypeNames: Map<Int, String>,
    ): ScheduleEntity? {
        val id = schedule.id ?: return null
        val date = schedule.date?.takeIf { it.isNotBlank() } ?: return null
        val sessionId = schedule.sessionId ?: schedule.session?.id ?: return null
        val course = schedule.courseUser?.course
        val instructor = schedule.courseUser?.institutionUser?.let { user ->
            listOfNotNull(user.firstName, user.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .takeIf { it.isNotBlank() }
        }
        // The period's display name is resolved from the sessions table at read
        // time rather than copied onto every row, so it stays consistent.
        return ScheduleEntity(
            periodKey = period.key,
            scheduleId = id,
            date = date,
            sessionId = sessionId,
            courseId = course?.id ?: schedule.courseUser?.courseId,
            courseName = course?.name,
            courseCode = course?.code,
            instructor = instructor,
            deliveryMethod = schedule.deliveryMethod?.name
                ?: schedule.deliveryMethodId?.let(deliveryNames::get),
            classType = schedule.classType?.name
                ?: schedule.classTypeId?.let(classTypeNames::get),
        )
    }

    private fun buildDays(
        from: LocalDate,
        dayCount: Int,
        meta: TimetableMetaEntity?,
        sessions: List<TimetableSessionEntity>,
        schedules: List<ScheduleEntity>,
        overrides: List<TimetableOverrideEntity>,
        shortCodes: Map<String, CourseShortCode>,
    ): List<TimetableDay> {
        // An override is a standing weekly change, so it is filed by weekday and
        // applies to that period on that weekday for as long as it exists.
        val byWeekday = overrides.groupBy { it.dayOfWeek }
        val excluded = meta?.excludedWeekdays.orEmpty().splitList().map { it.lowercase(Locale.ROOT) }.toSet()
        val workdays = meta?.workdays.orEmpty().splitList().map { it.lowercase(Locale.ROOT) }.toSet()
        val nonWorking = decodeNonWorking(meta?.nonWorkingDates).groupBy { it.date }
        val termStart = meta?.startDate?.toLocalDateOrNull()
        val termEnd = meta?.uptoDate?.toLocalDateOrNull()
        val byDate = schedules.groupBy { it.date }

        // The institution's session list runs one period longer than the teaching
        // day actually does. The trailing period is never scheduled, so leaving it
        // in would add a "Free" row to every single day of the timetable for a
        // period that does not exist. The rows arrive ordered by view order, so the
        // teaching day is the first [TIMETABLE_PERIOD_COUNT] of them.
        val periods = sessions.take(TIMETABLE_PERIOD_COUNT)

        return (0 until dayCount).map { offset ->
            val date = from.plusDays(offset.toLong())
            val isoDate = date.toString()
            val weekday = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
            val weekdayKey = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH).lowercase(Locale.ROOT)

            val suspensions = nonWorking[isoDate].orEmpty()
            val slots = buildSlots(
                sessions = periods,
                daySchedules = byDate[isoDate].orEmpty(),
                suspensions = suspensions,
                overrides = byWeekday[date.dayOfWeek.value].orEmpty().associateBy { it.sessionId },
                shortCodes = shortCodes,
            )

            // Suspensions win over the schedule. The portal generates the dated
            // schedule from the weekly timetable and lays holidays on top, so classes
            // still appear on days that were later called off — showing them as going
            // ahead would tell the student to turn up to a cancelled class.
            val note = suspensions.firstOrNull()?.reason
            val status = when {
                slots.any { !it.isFree && !it.isCancelled } -> DayStatus.CLASSES
                suspensions.isNotEmpty() -> DayStatus.HOLIDAY
                excluded.contains(weekdayKey) -> DayStatus.WEEKEND
                workdays.isNotEmpty() && !workdays.contains(weekdayKey) -> DayStatus.WEEKEND
                termStart != null && date < termStart -> DayStatus.OUT_OF_TERM
                termEnd != null && date > termEnd -> DayStatus.OUT_OF_TERM
                else -> DayStatus.NO_CLASSES
            }

            TimetableDay(
                date = date,
                weekdayName = weekday,
                status = status,
                note = note,
                slots = slots,
            )
        }
    }

    private fun decodeNonWorking(encoded: String?): List<NonWorkingDay> {
        if (encoded.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<NonWorkingDay>>(encoded) }.getOrDefault(emptyList())
    }

    /**
     * Every period for the day, free ones included, so the grid reads as a real
     * timetable. Rows sharing a period are merged: the portal emits one row per
     * instructor, so a class with two teachers arrives twice.
     */
    private fun buildSlots(
        sessions: List<TimetableSessionEntity>,
        daySchedules: List<ScheduleEntity>,
        suspensions: List<NonWorkingDay>,
        overrides: Map<Int, TimetableOverrideEntity>,
        shortCodes: Map<String, CourseShortCode>,
    ): List<ScheduleSlot> {
        val bySession = daySchedules.groupBy { it.sessionId }
        return sessions.map { session ->
            val rows = bySession[session.sessionId].orEmpty()
            val published = rows.firstOrNull { it.courseId != null } ?: rows.firstOrNull()
            // An entry with no session ids suspends the whole day.
            val cancelledBy = suspensions.firstOrNull {
                it.sessionIds.isEmpty() || session.sessionId in it.sessionIds
            }
            // The override replaces the published row rather than merging with it: it
            // exists precisely because the published one is wrong for this student.
            // The cancellation is applied afterwards either way, so a suspended day
            // strikes an overridden class through exactly like any other.
            val override = overrides[session.sessionId]
            val courseId = override?.courseId ?: published?.courseId.takeIf { override == null }
            val courseCode = if (override != null) override.courseCode else published?.courseCode
            val courseName = if (override != null) override.courseName else published?.courseName
            ScheduleSlot(
                sessionId = session.sessionId,
                sessionName = session.name,
                courseId = courseId,
                courseName = courseName,
                courseCode = courseCode,
                instructors = if (override != null) {
                    listOfNotNull(override.instructor?.takeIf { it.isNotBlank() })
                } else {
                    rows.mapNotNull { it.instructor }.distinct()
                },
                deliveryMethod = if (override != null) {
                    override.deliveryMethod
                } else {
                    rows.firstNotNullOfOrNull { it.deliveryMethod }
                },
                classType = if (override != null) {
                    override.classType
                } else {
                    rows.firstNotNullOfOrNull { it.classType }
                },
                cancelledReason = cancelledBy?.reason,
                // An override's own short code wins, because it may name a course the
                // mapping table has never seen. Otherwise: by course code first and by
                // id second, since a course with no recorded attendance reaches the
                // mapping screen without a code and is filed under its id while the
                // timetable knows its code. Either key finds the same short code.
                shortCode = override?.shortCode?.takeIf { it.isNotBlank() }
                    ?: (
                        courseShortCodeKey(courseCode, null)?.let { shortCodes[it] }
                            ?: courseShortCodeKey(null, courseId)?.let { shortCodes[it] }
                        )?.shortCode,
                isOverride = override != null,
            )
        }
    }

    private fun TimetableOverrideEntity.toDomain() = TimetableOverride(
        dayOfWeek = DayOfWeek.of(dayOfWeek),
        sessionId = sessionId,
        courseId = courseId,
        courseName = courseName,
        courseCode = courseCode,
        shortCode = shortCode,
        instructor = instructor,
        deliveryMethod = deliveryMethod,
        classType = classType,
    )

    // ---- helpers ---------------------------------------------------------


    /**
     * Reads the cohort report, if it is worth reading.
     *
     * This is the only thing in the app that calls the monitoring endpoint, and it is
     * called only from the screens that show its figures. It used to run inside every
     * timetable refresh, which meant a whole term's register was fetched on each app
     * start and each pull — paid for by everyone, read by the few who opened the class
     * screens. Now nobody pays for it until they ask.
     *
     * Skipped when the cache is younger than [CLASS_COUNTS_MAX_AGE_MS] unless [force],
     * which is what the refresh control on those screens passes.
     *
     * Rows are replaced rather than merged, because a register re-marked by staff
     * changes an existing count and a merge would keep the stale one.
     *
     * Always asked for from the cohort's first day to today, as the report is defined.
     * The response is the same shape whatever the range, and the wider answer is the
     * one worth keeping.
     */
    suspend fun loadClassCounts(period: Period, force: Boolean = false): ClassCountsResult {
        val dao = database.timetableDao()
        val fetchedAt = dao.classCountsMetaOnce(period.key)?.fetchedEpochMs
        if (!force && classCountsAreFresh(fetchedAt, System.currentTimeMillis())) {
            return ClassCountsResult.UpToDate
        }

        val meta = dao.metaOnce(period.key) ?: return ClassCountsResult.NoCohort
        val subgroupId = meta.subgroupId ?: return ClassCountsResult.NoCohort
        val from = meta.subgroupStartDate?.takeIf { it.isNotBlank() }
            ?: return ClassCountsResult.NoCohort

        return try {
            val report = apiCall {
                api.subgroupMonitoring(
                    MonitoringRequest(
                        subgroups = listOf(subgroupId),
                        startDate = from,
                        uptoDate = LocalDate.now().toString(),
                    ),
                )
            }
            val rows = report.toCountRows(period.key, subgroupId)
            with(dao) {
                deleteCounts(period.key)
                if (rows.isNotEmpty()) upsertCounts(rows)
                // Stamped even when the report was empty, so a cohort with nothing
                // recorded yet is not re-asked on every open.
                upsertClassCountsMeta(
                    ClassCountsMetaEntity(period.key, System.currentTimeMillis()),
                )
            }
            ClassCountsResult.Updated
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.w(TAG, "Could not read the cohort attendance counts", throwable)
            ClassCountsResult.Failed(ApiError.from(throwable).userMessage)
        }
    }

    /** When the cohort report was last read for [period], or null if it never was. */
    fun observeClassCountsFetchedAt(period: Period): Flow<Long?> =
        database.timetableDao().classCountsMeta(period.key).map { it?.fetchedEpochMs }

    /**
     * The counts for one day, observed so a dialog opened before a fetch lands fills
     * in rather than staying empty.
     */
    fun observeSessionCounts(period: Period, date: LocalDate): Flow<Map<Int, SessionCount>> =
        database.timetableDao().countsOn(period.key, date.toString()).map { rows ->
            rows.associate { it.sessionId to SessionCount(it.present, it.absent) }
        }

    /**
     * Every cohort count for the period, keyed to match attendance records.
     *
     * The counts table speaks ISO dates and the report speaks "YYYYMMDD"; the two are
     * reconciled here, once, rather than at each of the places that joins them.
     */
    fun observeAllSessionCounts(period: Period): Flow<Map<ClassSessionKey, SessionCount>> =
        database.timetableDao().countsForPeriod(period.key).map { rows ->
            rows.associate { row ->
                ClassSessionKey(row.date.replace("-", ""), row.sessionId) to
                    SessionCount(row.present, row.absent)
            }
        }

    /**
     * Chooses the cohort for [period]. Subgroups from other semesters are rejected
     * outright rather than silently used, since their timetable is a different one.
     */
    private fun pickSubgroup(subgroups: List<UserSubgroupDto>, period: Period): UserSubgroupDto? =
        subgroups.firstOrNull {
            it.academicYear == period.academicYear && it.academicSemester == period.semester
        } ?: subgroups.singleOrNull()

    /**
     * Reads holidays and suspensions.
     *
     * Verified against a real payload: a suspension looks like
     * `{"name":"Onam Holidays","date":"2026-08-24","session_id":[895,...]}`. Holidays
     * were empty in the capture, so they go through the same tolerant reader rather
     * than being assumed to match. Anything without a usable date is skipped.
     */
    private fun extractDatedNotes(
        elements: List<JsonElement>,
        fallbackReason: String,
    ): List<NonWorkingDay> = elements.mapNotNull { element ->
        when (element) {
            is JsonPrimitive ->
                element.content
                    .takeIf { it.looksLikeIsoDate() }
                    ?.let { NonWorkingDay(it.take(10), fallbackReason) }

            is JsonObject -> {
                val date = DATE_KEYS.firstNotNullOfOrNull { key ->
                    (element[key] as? JsonPrimitive)?.content?.takeIf { it.looksLikeIsoDate() }
                } ?: return@mapNotNull null
                val reason = REASON_KEYS.firstNotNullOfOrNull { key -> element[key].asStringOrNull() }
                NonWorkingDay(
                    date = date.take(10),
                    reason = reason ?: fallbackReason,
                    sessionIds = element["session_id"].asIntList(),
                )
            }

            else -> null
        }
    }

    /** `session_id` is an array in the observed data, but tolerate a lone value. */
    private fun JsonElement?.asIntList(): List<Int> = when (this) {
        is JsonArray -> mapNotNull { it.asIntOrNull() }
        is JsonPrimitive -> listOfNotNull(asIntOrNull())
        else -> emptyList()
    }

    private fun String.looksLikeIsoDate(): Boolean =
        length >= 10 && take(10).toLocalDateOrNull() != null

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { LocalDate.parse(take(10)) }.getOrNull()

    private fun String.splitList(): List<String> =
        split(SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        const val DEFAULT_DAY_COUNT = 7

        /** Periods in a teaching day. See [buildDays] for why the rest are dropped. */
        const val TIMETABLE_PERIOD_COUNT = 6

        const val SEPARATOR = ","
        private const val TAG = "TimetableRepository"

        val DATE_KEYS = listOf("date", "holiday_date", "start_date", "from_date")
        val REASON_KEYS = listOf("name", "reason", "description", "title")
    }
}


/**
 * The monitoring report, flattened to the rows the app keeps.
 *
 * Only this subgroup's entries are taken. The request only ever asks for one, but the
 * response is keyed by subgroup and reading whatever happens to be there would be
 * trusting the shape rather than checking it.
 */
private fun MonitoringResponse.toCountRows(
    periodKey: String,
    subgroupId: Int,
): List<SessionCountEntity> {
    val key = subgroupId.toString()
    return attendanceData.flatMap { (date, bySubgroup) ->
        bySubgroup[key].orEmpty().mapNotNull { (sessionId, counts) ->
            val id = sessionId.toIntOrNull() ?: return@mapNotNull null
            SessionCountEntity(
                periodKey = periodKey,
                date = date,
                sessionId = id,
                present = counts.present,
                absent = counts.absent,
            )
        }
    }
}
