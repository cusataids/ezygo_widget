package com.ezygo.attendance.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.repo.CalendarRepository
import com.ezygo.attendance.data.repo.TaskRepository
import com.ezygo.attendance.domain.model.EventSource
import com.ezygo.attendance.domain.model.FeedSource
import com.ezygo.attendance.domain.model.TaskSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

/**
 * Tags every reminder this app schedules, so the whole queue can be dropped in one
 * call when the settings change. Without a shared tag there is no way to reach a
 * reminder whose source has since been switched off.
 */
internal const val REMINDER_TAG = "ezygo_reminder"

/**
 * Posts one reminder, at the time it was scheduled for.
 *
 * A worker rather than an alarm: WorkManager already ships with this app for Glance,
 * it persists its queue across reboots without a boot receiver, and it needs none of
 * the exact-alarm permissions Android 12 put behind a user prompt. The cost is that
 * delivery is approximate, which for "your exam is tomorrow" is the right trade.
 *
 * Deliberately not a `@HiltWorker`: that needs a custom WorkerFactory installed through
 * `Configuration.Provider`, which means taking over WorkManager's initialisation — and
 * Glance's own SessionWorker runs through the same WorkManager. Everything this worker
 * needs arrives in its input data, so the default factory can build it.
 */
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val entryPoint = applicationContext.reminderEntryPoint()

        // Re-asked here rather than trusted from scheduling time, because the gap
        // between the two is hours. Cancelling the queue when a switch is turned off
        // is the first line of defence and this is the second: a cancel can be lost
        // to process death or to an OEM that prunes WorkManager's queue and restores
        // it later, and a notification the user has switched off must not arrive
        // because of either.
        val sourceKey = inputData.getString(KEY_SOURCE)
        if (sourceKey != null && sourceKey !in entryPoint.preferencesStore().currentReminderSources()) {
            return Result.success()
        }

        // A task done before its reminder comes due has nothing left to remind about.
        // Completing one reschedules, which cancels this — but the same caveat as
        // above applies, so the state is checked rather than assumed.
        val taskId = inputData.getString(KEY_TASK_ID)
        if (taskId != null && !entryPoint.taskRepository().isPending(taskId)) {
            return Result.success()
        }

        Reminders.post(
            context = applicationContext,
            id = inputData.getInt(KEY_ID, title.hashCode()),
            title = title,
            body = inputData.getString(KEY_BODY),
        )
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_ID = "id"

        /** Which switch this reminder answers to, re-checked when it fires. */
        const val KEY_SOURCE = "source"

        /** Set only for task reminders, so the worker can skip a completed one. */
        const val KEY_TASK_ID = "task_id"
    }
}

/**
 * Hilt reaches the worker through an entry point, for the same reason the widgets
 * use one: WorkManager's default factory constructs this worker, and everything it
 * needs beyond its input data has to be fetched rather than injected.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ReminderEntryPoint {
    fun preferencesStore(): PreferencesStore
    fun taskRepository(): TaskRepository
}

internal fun Context.reminderEntryPoint(): ReminderEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, ReminderEntryPoint::class.java)

/**
 * Decides what to remind about, and when.
 *
 * Rescheduled wholesale rather than incrementally: the calendar and the task feed both
 * change by being replaced, so working out which individual reminders moved would cost
 * more than simply asking again. Every request is unique-named by its source and id, so
 * re-running this replaces rather than duplicates.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val calendarRepository: CalendarRepository,
    private val taskRepository: TaskRepository,
    private val preferences: PreferencesStore,
) {

    suspend fun rescheduleAll() {
        val manager = WorkManager.getInstance(context)

        // Everything already queued goes first, unconditionally.
        //
        // This is the fix for reminders that kept arriving after every switch was
        // turned off. Rescheduling only ever *enqueued*, relying on REPLACE against a
        // unique name to update each one — which does nothing at all for a reminder
        // that is no longer being re-enqueued. Turning a source off simply stopped
        // rewriting its reminders and left the ones already in the queue to fire.
        //
        // Before the permission check as well, so losing notification permission (or
        // revoking it deliberately) clears the queue rather than stranding it.
        manager.cancelAllWorkByTag(REMINDER_TAG)

        if (!Reminders.canPost(context)) return
        val enabled = preferences.currentReminderSources()
        if (enabled.isEmpty()) return
        val now = System.currentTimeMillis()

        calendarRepository.events.first()
            .asSequence()
            .mapNotNull { event ->
                FeedSource.fromKey(event.source.storageKey)?.let { event to it }
            }
            .filter { (_, source) -> source.key in enabled }
            .filter { (event, _) -> event.start.toEpochMilli() > now }
            .take(MAX_PER_SOURCE)
            .forEach { (event, source) ->
                schedule(
                    manager = manager,
                    name = "event-" + event.id,
                    title = event.title,
                    body = event.description ?: event.location,
                    dueEpochMs = event.start.toEpochMilli(),
                    lead = LEAD_TIME,
                    sourceKey = source.key,
                    taskId = null,
                    now = now,
                )
            }

        // Both kinds of task come from one table now, so they are filtered by source
        // rather than fetched from two places. Only dated ones can be scheduled at
        // all: a task with no deadline has no moment to be reminded at.
        val allowedSources = buildMap {
            if (FeedSource.TASKS_SYNCED.key in enabled) put(TaskSource.SYNCED, FeedSource.TASKS_SYNCED.key)
            if (FeedSource.TASKS_OWN.key in enabled) put(TaskSource.OWN, FeedSource.TASKS_OWN.key)
        }
        if (allowedSources.isNotEmpty()) {
            taskRepository.tasks.first()
                .asSequence()
                .filterNot { it.done }
                .mapNotNull { task -> allowedSources[task.source]?.let { task to it } }
                .mapNotNull { (task, key) -> task.dueEpochMs?.let { Triple(task, key, it) } }
                .filter { (_, _, due) -> due > now }
                .take(MAX_PER_SOURCE)
                .forEach { (task, sourceKey, due) ->
                    // Two reminders, not one: half a day out to plan around, and an
                    // hour out to actually do it. Both carry the task id, so either
                    // is dropped at fire time if it has been ticked off since.
                    schedule(
                        manager = manager,
                        name = "task-${task.id}-early",
                        title = task.title,
                        body = task.detail,
                        dueEpochMs = due,
                        lead = LEAD_TIME,
                        sourceKey = sourceKey,
                        taskId = task.id,
                        now = now,
                    )
                    schedule(
                        manager = manager,
                        name = "task-${task.id}-final",
                        title = task.title,
                        body = task.detail ?: "Due in an hour",
                        dueEpochMs = due,
                        lead = FINAL_TASK_LEAD,
                        sourceKey = sourceKey,
                        taskId = task.id,
                        now = now,
                    )
                }
        }
    }

    private fun schedule(
        manager: WorkManager,
        name: String,
        title: String,
        body: String?,
        dueEpochMs: Long,
        lead: Duration,
        sourceKey: String,
        taskId: String?,
        now: Long,
    ) {
        val delay = dueEpochMs - lead.toMillis() - now
        // Already past. Covers both a deadline inside the lead window and, for the
        // one-hour task reminder, anything more than an hour gone.
        if (delay <= 0) return

        manager.enqueueUniqueWork(
            name,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInitialDelay(Duration.ofMillis(delay))
                .addTag(REMINDER_TAG)
                .setInputData(
                    Data.Builder()
                        .putString(ReminderWorker.KEY_TITLE, title)
                        .putString(ReminderWorker.KEY_BODY, body)
                        .putInt(ReminderWorker.KEY_ID, name.hashCode().absoluteValue)
                        .putString(ReminderWorker.KEY_SOURCE, sourceKey)
                        .putString(ReminderWorker.KEY_TASK_ID, taskId)
                        .build(),
                )
                .build(),
        )
    }

    private companion object {
        /**
         * Half a day, for everything.
         *
         * Applied to all-day entries too, which previously got a fixed 08:00 on the
         * morning of. Twelve hours before a midnight start lands at noon the day
         * before — still a useful "this is tomorrow", and one rule rather than two.
         */
        val LEAD_TIME: Duration = Duration.ofHours(12)

        /** The second task reminder: close enough that it is now or not at all. */
        val FINAL_TASK_LEAD: Duration = Duration.ofHours(1)

        /** A guard on a feed with hundreds of dated rows, not a policy about them. */
        const val MAX_PER_SOURCE = 50
    }
}

/** The storage key behind a source, so a reminder can be matched to its switch. */
internal val EventSource.storageKey: String
    get() = when (this) {
        EventSource.BATCH -> FeedSource.BATCH.key
        EventSource.ACADEMIC -> FeedSource.ACADEMIC.key
        EventSource.LOCAL -> FeedSource.LOCAL.key
    }
