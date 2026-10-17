package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ezygo.attendance.data.local.entity.CalendarEventEntity
import com.ezygo.attendance.data.local.entity.CalendarSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendar_events ORDER BY startEpochMs ASC")
    fun events(): Flow<List<CalendarEventEntity>>

    /**
     * The next few events from the given sources, counted from the start of today
     * rather than from now: an all-day event starts at midnight, so "after now"
     * would drop today's exam the moment the day began.
     *
     * [sources] is an allow-list rather than an exclusion, so a feed added later has
     * to be named before it can reach a screen that filters. Which sources those are is
     * the user's choice now, not this layer's.
     */
    @Query(
        "SELECT * FROM calendar_events WHERE startEpochMs >= :fromEpochMs " +
            "AND source IN (:sources) ORDER BY startEpochMs ASC LIMIT :limit",
    )
    fun upcomingFrom(
        fromEpochMs: Long,
        sources: List<String>,
        limit: Int,
    ): Flow<List<CalendarEventEntity>>

    /** One row per feed; the caller decides how to combine them. */
    @Query("SELECT * FROM calendar_sync")
    fun syncStates(): Flow<List<CalendarSyncEntity>>

    @Query("SELECT * FROM calendar_sync WHERE id = :id")
    suspend fun syncOnce(id: Int): CalendarSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSync(sync: CalendarSyncEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<CalendarEventEntity>)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM calendar_events WHERE source = :source")
    suspend fun deleteBySource(source: String)

    /**
     * Swaps in one freshly synced feed, atomically.
     *
     * Only that feed's rows are replaced: the user's own events exist nowhere but
     * this device and a sync must never be able to lose them, and the other feed
     * is not this sync's business either.
     */
    @Transaction
    suspend fun replaceSynced(
        source: String,
        events: List<CalendarEventEntity>,
        sync: CalendarSyncEntity,
    ) {
        deleteBySource(source)
        upsertEvents(events)
        upsertSync(sync)
    }

    @Query("DELETE FROM calendar_events")
    suspend fun clearAllEvents()

    @Query("DELETE FROM calendar_sync")
    suspend fun clearSync()
}
