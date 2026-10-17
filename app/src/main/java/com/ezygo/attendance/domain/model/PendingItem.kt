package com.ezygo.attendance.domain.model

/**
 * Something with a deadline that is not done yet, as the home screen shows it.
 *
 * Both sources arrive here flattened: what is being asked is "what is still owed", and
 * for that question a synced deadline and one the student wrote are the same kind of
 * thing. [fromTasks] survives only to mark which is which on the row.
 */
data class PendingItem(
    val id: String,
    val title: String,
    val dueEpochMs: Long?,
    val isAllDay: Boolean,
    val fromTasks: Boolean,
) {
    fun isOverdue(nowEpochMs: Long): Boolean = dueEpochMs != null && dueEpochMs < nowEpochMs
}
