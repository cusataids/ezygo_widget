package com.ezygo.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A note the user wrote.
 *
 * Text and nothing else. The checklist that used to live in here as JSON is gone:
 * tasks are their own table now, so a note no longer has to be created before a task
 * can exist, and a task no longer has to be found inside a note to be edited.
 *
 * Like the local calendar events and unlike everything else in this database, these
 * are the user's own writing rather than a cache — nothing can refetch them, which is
 * why every schema change from here ships a real migration.
 */
@Entity(tableName = "notes", indices = [Index("updatedEpochMs")])
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val createdEpochMs: Long,
    val updatedEpochMs: Long,
)
