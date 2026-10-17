package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ezygo.attendance.data.local.entity.ScheduleEntity
import com.ezygo.attendance.data.local.entity.ClassCountsMetaEntity
import com.ezygo.attendance.data.local.entity.SessionCountEntity
import com.ezygo.attendance.data.local.entity.TimetableMetaEntity
import com.ezygo.attendance.data.local.entity.TimetableSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Query("SELECT * FROM timetable_meta WHERE periodKey = :periodKey")
    fun meta(periodKey: String): Flow<TimetableMetaEntity?>

    @Query("SELECT * FROM timetable_sessions WHERE periodKey = :periodKey ORDER BY viewOrder ASC, sessionId ASC")
    fun sessions(periodKey: String): Flow<List<TimetableSessionEntity>>

    /** Dates are ISO text, so a lexicographic BETWEEN is a correct date range. */
    @Query(
        "SELECT * FROM schedules WHERE periodKey = :periodKey AND date BETWEEN :fromDate AND :toDate " +
            "ORDER BY date ASC, sessionId ASC",
    )
    fun schedulesBetween(periodKey: String, fromDate: String, toDate: String): Flow<List<ScheduleEntity>>

    /**
     * One-shot reads for the home-screen widget, which renders a single frame and
     * then goes away. Combining three observed Flows and waiting for each to emit is
     * both slower and one more thing that can fail to complete inside a broadcast's
     * window — the reason a widget could previously hang before drawing anything.
     */
    @Query("SELECT * FROM timetable_meta WHERE periodKey = :periodKey")
    suspend fun metaOnce(periodKey: String): TimetableMetaEntity?

    @Query(
        "SELECT * FROM timetable_sessions WHERE periodKey = :periodKey " +
            "ORDER BY viewOrder ASC, sessionId ASC",
    )
    suspend fun sessionsOnce(periodKey: String): List<TimetableSessionEntity>

    @Query(
        "SELECT * FROM schedules WHERE periodKey = :periodKey AND date BETWEEN :fromDate AND :toDate " +
            "ORDER BY date ASC, sessionId ASC",
    )
    suspend fun schedulesBetweenOnce(
        periodKey: String,
        fromDate: String,
        toDate: String,
    ): List<ScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMeta(meta: TimetableMetaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<TimetableSessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<ScheduleEntity>)

    @Query("DELETE FROM timetable_sessions WHERE periodKey = :periodKey")
    suspend fun deleteSessions(periodKey: String)

    @Query("DELETE FROM schedules WHERE periodKey = :periodKey")
    suspend fun deleteSchedules(periodKey: String)

    /** Replaces one period's timetable atomically, leaving other periods alone. */
    @Transaction
    suspend fun replaceTimetable(
        meta: TimetableMetaEntity,
        sessions: List<TimetableSessionEntity>,
        schedules: List<ScheduleEntity>,
    ) {
        deleteSessions(meta.periodKey)
        deleteSchedules(meta.periodKey)
        upsertMeta(meta)
        insertSessions(sessions)
        insertSchedules(schedules)
    }

    @Query("DELETE FROM timetable_meta")
    suspend fun clearAllMeta()

    @Query("DELETE FROM timetable_sessions")
    suspend fun clearAllSessions()

    @Query("DELETE FROM schedules")
    suspend fun clearAllSchedules()

    // ---- cohort attendance counts ----------------------------------------

    @Query(
        "SELECT * FROM session_attendance_counts " +
            "WHERE periodKey = :periodKey AND date = :date ORDER BY sessionId ASC",
    )
    fun countsOn(periodKey: String, date: String): Flow<List<SessionCountEntity>>

    @Query("SELECT * FROM session_attendance_counts WHERE periodKey = :periodKey")
    fun countsForPeriod(periodKey: String): Flow<List<SessionCountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCounts(rows: List<SessionCountEntity>)

    @Query("DELETE FROM session_attendance_counts WHERE periodKey = :periodKey")
    suspend fun deleteCounts(periodKey: String)

    @Query("DELETE FROM session_attendance_counts")
    suspend fun clearAllCounts()

    // ---- when those counts were last read ---------------------------------

    @Query("SELECT * FROM class_counts_meta WHERE periodKey = :periodKey")
    fun classCountsMeta(periodKey: String): Flow<ClassCountsMetaEntity?>

    @Query("SELECT * FROM class_counts_meta WHERE periodKey = :periodKey")
    suspend fun classCountsMetaOnce(periodKey: String): ClassCountsMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertClassCountsMeta(row: ClassCountsMetaEntity)

    @Query("DELETE FROM class_counts_meta")
    suspend fun clearAllClassCountsMeta()
}
