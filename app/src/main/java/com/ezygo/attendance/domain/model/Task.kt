package com.ezygo.attendance.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Who put the task there, which is the only thing that behaves differently. */
enum class TaskSource {
    /** Written in this app. Fully editable, and exists nowhere else. */
    OWN,

    /**
     * From the tasks calendar feed. Its wording and deadline belong upstream, so
     * neither can be edited here — ticking it off and hiding it are the student's,
     * and both survive a sync.
     */
    SYNCED,
}

/** Where a task falls relative to now, which is how the list is ordered and grouped. */
enum class TaskGroup(val label: String) {
    OVERDUE("Overdue"),
    TODAY("Today"),
    THIS_WEEK("This week"),
    LATER("Later"),
    NO_DEADLINE("No deadline"),
}

/**
 * One thing to do.
 *
 * A deadline is optional: something with no date is still owed, it simply cannot claim
 * a place in the queue. That is why [dueEpochMs] is nullable here and why the list has
 * a group for exactly that case rather than dropping them.
 */
data class Task(
    val id: String,
    val source: TaskSource,
    val title: String,
    val detail: String?,
    val dueEpochMs: Long?,
    val isAllDay: Boolean,
    val done: Boolean,
    val completedEpochMs: Long?,
    val createdEpochMs: Long,
) {
    val isEditable: Boolean get() = source == TaskSource.OWN

    fun isOverdue(nowEpochMs: Long): Boolean =
        !done && dueEpochMs != null && dueEpochMs < nowEpochMs

    /**
     * Which section this belongs in.
     *
     * "This week" runs to the end of the seventh day from today rather than to Sunday:
     * a deadline on Monday is not "later" merely because a week happened to turn over.
     */
    fun group(nowEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): TaskGroup {
        val due = dueEpochMs ?: return TaskGroup.NO_DEADLINE
        if (due < nowEpochMs) return TaskGroup.OVERDUE
        val today = Instant.ofEpochMilli(nowEpochMs).atZone(zone).toLocalDate()
        val dueDate = Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
        return when {
            dueDate == today -> TaskGroup.TODAY
            dueDate <= today.plusDays(WEEK_AHEAD) -> TaskGroup.THIS_WEEK
            else -> TaskGroup.LATER
        }
    }

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return title.contains(needle, ignoreCase = true) ||
            detail?.contains(needle, ignoreCase = true) == true
    }

    companion object {
        private const val WEEK_AHEAD = 7L

        /**
         * How long a finished task stays on screen.
         *
         * Long enough to see what was just ticked and to undo a mistake, short enough
         * that the list does not turn into a record of everything ever done.
         */
        const val DONE_VISIBLE_MS = 24 * 60 * 60 * 1000L

        fun isRecentlyDone(completedEpochMs: Long?, nowEpochMs: Long): Boolean =
            completedEpochMs != null && nowEpochMs - completedEpochMs < DONE_VISIBLE_MS
    }
}

/** One semester's result, as read off a grade card. */
data class SemesterResult(
    val semester: Int,
    val sgpa: Double,
    val credits: Int?,
)

/** The day a task falls on, for a screen that groups by date. */
fun Task.dueDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate? =
    dueEpochMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
