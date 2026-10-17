package com.ezygo.attendance.domain.model

/**
 * A note: a title and some writing.
 *
 * The checklist it used to carry is gone — see [Task], which is now a row of its own.
 * A note that had to be created before a task could exist was the wrong shape for both
 * of them, and it meant a task had no deadline, no editor and no identity outside the
 * note it happened to be typed into.
 */
data class Note(
    val id: String,
    val title: String,
    val body: String,
    val createdEpochMs: Long,
    val updatedEpochMs: Long,
) {
    /** What the list shows when the note has no title of its own. */
    val displayTitle: String
        get() = title.ifBlank {
            body.lineSequence().firstOrNull { it.isNotBlank() }?.take(60).orEmpty()
        }

    /** Nothing written at all, which is not worth keeping. */
    val isEmpty: Boolean get() = title.isBlank() && body.isBlank()

    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return title.contains(needle, ignoreCase = true) ||
            body.contains(needle, ignoreCase = true)
    }
}
