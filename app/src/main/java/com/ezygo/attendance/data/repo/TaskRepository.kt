package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.entity.TaskEntity
import com.ezygo.attendance.data.local.entity.TaskSourceKeys
import com.ezygo.attendance.data.remote.IcsClient
import com.ezygo.attendance.data.remote.IcsResponse
import com.ezygo.attendance.data.remote.ics.IcsParser
import com.ezygo.attendance.domain.model.Task
import com.ezygo.attendance.domain.model.TaskSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Every task, from both sources.
 *
 * The user's own are written here and exist nowhere else. The feed's are the feed's:
 * a sync rewrites them wholesale and deletes any whose event has gone, and it is
 * scoped by source so it can never reach a task the student wrote. The one thing a
 * sync never touches on either is whether it is ticked off.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val database: AppDatabase,
    private val icsClient: IcsClient,
) {
    private val syncMutex = Mutex()

    val tasks: Flow<List<Task>> =
        database.taskDao().tasks().map { rows -> rows.map { it.toDomain() } }

    /** Dated and unfinished, soonest first — overdue included — for the home screen. */
    fun pending(limit: Int): Flow<List<Task>> =
        database.taskDao().pending(limit).map { rows -> rows.map { it.toDomain() } }

    /**
     * Adds one of the user's own.
     *
     * A title is all it takes — the quick-add field is the point, and everything else
     * is something the task can be given later rather than a question asked up front.
     */
    suspend fun add(
        title: String,
        detail: String? = null,
        dueEpochMs: Long? = null,
        isAllDay: Boolean = false,
    ): String? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val now = System.currentTimeMillis()
        val id = "own:" + UUID.randomUUID()
        database.taskDao().upsert(
            TaskEntity(
                id = id,
                source = TaskSourceKeys.OWN,
                title = clean,
                detail = detail?.trim()?.takeIf { it.isNotEmpty() },
                dueEpochMs = dueEpochMs,
                isAllDay = isAllDay,
                done = false,
                completedEpochMs = null,
                createdEpochMs = now,
                updatedEpochMs = now,
            ),
        )
        return id
    }

    /**
     * Edits one of the user's own.
     *
     * A synced task is left exactly as it arrived: its wording and its deadline belong
     * upstream, and an edit here would be silently undone by the next sync.
     */
    suspend fun edit(
        id: String,
        title: String,
        detail: String?,
        dueEpochMs: Long?,
        isAllDay: Boolean,
    ) {
        val existing = database.taskDao().taskOnce(id) ?: return
        if (existing.source != TaskSourceKeys.OWN) return
        val clean = title.trim().ifEmpty { return }
        database.taskDao().upsert(
            existing.copy(
                title = clean,
                detail = detail?.trim()?.takeIf { it.isNotEmpty() },
                dueEpochMs = dueEpochMs,
                isAllDay = isAllDay,
                updatedEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    /**
     * Whether this task is still owed, as of right now.
     *
     * Read directly rather than from the [tasks] flow because the reminder worker asks
     * this in a cold process, where nothing is collecting and the flow would have to
     * build the whole list to answer a question about one row.
     */
    suspend fun isPending(id: String): Boolean =
        database.taskDao().taskOnce(id)?.done == false

    suspend fun setDone(id: String, done: Boolean) {
        val now = System.currentTimeMillis()
        database.taskDao().setDone(
            id = id,
            done = done,
            completedEpochMs = if (done) now else null,
            nowEpochMs = now,
        )
    }

    /**
     * Only the user's own can be deleted; a synced one comes back on the next sync.
     *
     * The refusal is returned rather than swallowed so the screen can say why. It used
     * to return quietly, which looks exactly like a delete that worked — right up until
     * the row reappeared.
     */
    suspend fun delete(id: String): DeleteOutcome {
        val existing = database.taskDao().taskOnce(id) ?: return DeleteOutcome.Deleted
        if (existing.source != TaskSourceKeys.OWN) return DeleteOutcome.syncedTask()
        database.taskDao().delete(id)
        return DeleteOutcome.Deleted
    }

    /**
     * Brings the synced half level with the feed.
     *
     * Three outcomes per event, all from the same pass: a new UID is inserted, a known
     * UID is rewritten with whatever changed, and a UID that has stopped appearing is
     * deleted. Done and its timestamp are carried across rather than reset, which is
     * why the existing rows are read before the write.
     */
    suspend fun sync() = syncMutex.withLock {
        val response = icsClient.fetch(FEED_URL, etag = null)
        val body = when (response) {
            is IcsResponse.Content -> response.body
            // Nothing changed upstream, so nothing here has to change either.
            IcsResponse.Unchanged -> return@withLock
        }

        val events = IcsParser.parse(body)
        val before = database.taskDao().bySourceOnce(TaskSourceKeys.SYNCED).associateBy { it.id }
        val now = System.currentTimeMillis()

        val rows = events.map { event ->
            val existing = before[event.uid]
            TaskEntity(
                id = event.uid,
                source = TaskSourceKeys.SYNCED,
                title = event.title,
                detail = event.description,
                dueEpochMs = event.startEpochMs,
                isAllDay = event.isAllDay,
                done = existing?.done == true,
                completedEpochMs = existing?.completedEpochMs,
                createdEpochMs = existing?.createdEpochMs ?: now,
                updatedEpochMs = now,
            )
        }

        database.taskDao().upsert(rows)
        if (rows.isEmpty()) {
            // An empty feed means every synced task is gone, which is a legitimate
            // state. The NOT IN form cannot express it, so it is said directly.
            database.taskDao().deleteBySource(TaskSourceKeys.SYNCED)
        } else {
            database.taskDao().deleteMissing(TaskSourceKeys.SYNCED, rows.map { it.id })
        }
    }

    suspend fun clear() = database.taskDao().clearAll()

    private fun TaskEntity.toDomain() = Task(
        id = id,
        source = if (source == TaskSourceKeys.SYNCED) TaskSource.SYNCED else TaskSource.OWN,
        title = title,
        detail = detail,
        dueEpochMs = dueEpochMs,
        isAllDay = isAllDay,
        done = done,
        completedEpochMs = completedEpochMs,
        createdEpochMs = createdEpochMs,
    )

    private companion object {
        const val FEED_URL = "https://calendar.google.com/calendar/ical/" +
            "99e7e031b741626eed363d099755413111c8eb824d761d47ffaeb61739f95a61" +
            "%40group.calendar.google.com/private-bc3aa445fd3e5feca01a975aa23aa4f6/basic.ics"
    }
}
