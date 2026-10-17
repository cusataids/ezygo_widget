package com.ezygo.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A task, whoever it came from.
 *
 * One table for both sources rather than two, because a deadline is a deadline: the
 * student wants one ordered list of what is owed, and keeping the feed's rows in a
 * separate table only meant every screen had to merge them again. [source] is what the
 * sync scopes itself to — it replaces the synced rows and never touches the user's.
 *
 * Tasks are first-class here. They were previously JSON inside a note, which meant a
 * task could not exist without one being written first, and ticking one off silently
 * rewrote a note the reader could not see.
 */
@Entity(
    tableName = "tasks",
    indices = [Index("dueEpochMs"), Index("source")],
)
data class TaskEntity(
    /** A UUID for the user's own; the feed's own UID for synced, so an edit updates. */
    @PrimaryKey val id: String,
    /** [TaskSourceKeys]. Stored as text so a third source needs no migration. */
    val source: String,
    val title: String,
    val detail: String?,
    /** Null for a task with no deadline, which is a normal task and not a broken one. */
    val dueEpochMs: Long?,
    val isAllDay: Boolean,
    /** Ticking one off is the student's, not the feed's, so it survives a sync. */
    val done: Boolean,
    /** When it was ticked, so the done list can retire what is no longer news. */
    val completedEpochMs: Long?,
    val createdEpochMs: Long,
    val updatedEpochMs: Long,
)

/** The values [TaskEntity.source] holds, in one place. */
object TaskSourceKeys {
    const val OWN = "own"
    const val SYNCED = "synced"
}

/**
 * One semester's result, read off a downloaded grade card.
 *
 * Keyed by semester so a card downloaded twice cannot count twice, which is what
 * keeps the CGPA an average over semesters rather than over files.
 */
@Entity(tableName = "semester_results")
data class SemesterResultEntity(
    @PrimaryKey val semester: Int,
    val sgpa: Double,
    val credits: Int?,
    val parsedEpochMs: Long,
)
