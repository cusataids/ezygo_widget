package com.ezygo.attendance.ui.miniapps.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.DeleteOutcome
import com.ezygo.attendance.data.repo.TaskRepository
import com.ezygo.attendance.domain.model.Task
import com.ezygo.attendance.domain.model.TaskGroup
import com.ezygo.attendance.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One heading and the tasks under it. Empty groups never reach the screen. */
data class TaskSection(
    val group: TaskGroup,
    val tasks: List<Task>,
)

data class TasksUiState(
    val sections: List<TaskSection> = emptyList(),
    /** Ticked recently enough to still be worth showing — see [Task.DONE_VISIBLE_MS]. */
    val recentlyDone: List<Task> = emptyList(),
    val hasAnyTask: Boolean = false,
    val isSyncing: Boolean = false,
    val error: String? = null,
) {
    val openCount: Int get() = sections.sumOf { it.tasks.size }
}

/**
 * The tasks app.
 *
 * Tasks are grouped by when they are due rather than by where they came from, because
 * that is the question being asked: an overdue essay from the shared calendar and an
 * overdue one you typed yourself are equally overdue. The source is a mark on the row,
 * not a division of the list.
 *
 * The feed is synced on open as well as on demand — these are deadlines, a list a week
 * out of date is worse than useless, and it is one conditional GET.
 */
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    private val sync = MutableStateFlow(SyncState())

    val state: StateFlow<TasksUiState> = combine(
        repository.tasks,
        sync,
    ) { tasks, syncState ->
        val now = System.currentTimeMillis()
        val (done, open) = tasks.partition { it.done }

        TasksUiState(
            // Built in the fixed group order rather than sorted by it, so the headings
            // always read Overdue, Today, This week, Later, No deadline.
            sections = TaskGroup.entries.mapNotNull { group ->
                val inGroup = open.filter { it.group(now) == group }
                if (inGroup.isEmpty()) null else TaskSection(group, inGroup)
            },
            recentlyDone = done
                .filter { Task.isRecentlyDone(it.completedEpochMs, now) }
                .sortedByDescending { it.completedEpochMs },
            hasAnyTask = tasks.isNotEmpty(),
            isSyncing = syncState.isSyncing,
            error = syncState.error,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    init {
        sync()
    }

    fun sync() = viewModelScope.launch {
        sync.value = SyncState(isSyncing = true)
        try {
            repository.sync()
            sync.value = SyncState()
            // Deadlines that just arrived or moved need their reminders to follow.
            reminderScheduler.rescheduleAll()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            // Whatever synced before is still on screen; only the message is new.
            sync.value = SyncState(error = "Could not sync tasks. Check your connection.")
        }
    }

    fun add(title: String, detail: String?, dueEpochMs: Long?, isAllDay: Boolean) =
        viewModelScope.launch {
            repository.add(title, detail, dueEpochMs, isAllDay)
            // A task added with a deadline should be reminded about without waiting
            // for the next sync to be the thing that notices it.
            if (dueEpochMs != null) reminderScheduler.rescheduleAll()
        }

    fun edit(id: String, title: String, detail: String?, dueEpochMs: Long?, isAllDay: Boolean) =
        viewModelScope.launch {
            repository.edit(id, title, detail, dueEpochMs, isAllDay)
            reminderScheduler.rescheduleAll()
        }

    fun setDone(id: String, done: Boolean) = viewModelScope.launch {
        repository.setDone(id, done)
        // Ticking a task off has to drop its reminders, and un-ticking one has to put
        // them back. Neither happened before, so a task completed in the morning still
        // announced itself that evening.
        reminderScheduler.rescheduleAll()
    }

    /** Why the last delete was refused, or null. */
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun dismissDeleteError() {
        _deleteError.value = null
    }

    fun delete(id: String) = viewModelScope.launch {
        val outcome = repository.delete(id)
        if (outcome is DeleteOutcome.Refused) {
            _deleteError.value = outcome.message
            return@launch
        }
        // A deleted task's reminders outlive it otherwise.
        reminderScheduler.rescheduleAll()
    }

    /**
     * Deletes several at once.
     *
     * Synced tasks among them are refused by the repository rather than filtered here,
     * because that rule belongs with the sync that would bring them straight back.
     */
    fun delete(ids: Set<String>) = viewModelScope.launch {
        // Counted rather than reported one by one: a selection of ten synced tasks
        // should produce one explanation, not ten identical ones. Whatever in the
        // batch *could* be deleted still is, so a mixed selection does as much as it
        // is allowed to rather than failing whole.
        val refused = ids.count { repository.delete(it) is DeleteOutcome.Refused }
        if (refused > 0) {
            _deleteError.value = if (refused == ids.size) {
                DeleteOutcome.syncedTask().message
            } else {
                "$refused of these come from your synced tasks calendar and cannot be " +
                    "deleted here. The rest have been deleted."
            }
        }
        // Once, after the whole batch, rather than per task: rescheduling reads the
        // entire feed and rewrites the queue, so doing it inside the loop would repeat
        // that work for every row deleted.
        reminderScheduler.rescheduleAll()
    }

    /** The sync half of the state, kept apart so a fetch cannot disturb the lists. */
    private data class SyncState(val isSyncing: Boolean = false, val error: String? = null)
}
