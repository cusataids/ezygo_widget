package com.ezygo.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Academic calendar cache.
 *
 * Unlike every other table in this database, this one is not purely a cache:
 * [CalendarEventEntity.SOURCE_LOCAL] rows are the user's own events and exist
 * nowhere else. A sync replaces only the synced rows, and the destructive
 * database fallback is why the schema version must be bumped rather than
 * migrated away — see AppDatabase.
 */
@Entity(tableName = "calendar_events", indices = [Index("startEpochMs"), Index("source")])
data class CalendarEventEntity(
    /** `google:<uid>` for feed events, `local:<uuid>` for the user's own. */
    @PrimaryKey val id: String,
    val source: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startEpochMs: Long,
    /** Null when the feed gave no DTEND, which is normal for all-day entries. */
    val endEpochMs: Long?,
    val isAllDay: Boolean,
) {
    companion object {
        /**
         * Still "google" rather than something clearer, because rows cached by
         * earlier versions carry that value and renaming it would silently orphan
         * them until the next sync.
         */
        const val SOURCE_BATCH = "google"
        const val SOURCE_ACADEMIC = "academic"
        const val SOURCE_LOCAL = "local"
    }
}

/** One row per feed, tracking how fresh that feed's events are. */
@Entity(tableName = "calendar_sync")
data class CalendarSyncEntity(
    /** Identifies the feed. The column already existed, so a second feed needs no schema change. */
    @PrimaryKey val id: Int,
    val lastSyncEpochMs: Long,
    /**
     * The feed's ETag, replayed as `If-None-Match` so an unchanged calendar costs
     * a 304 rather than a full download.
     */
    val etag: String?,
)
