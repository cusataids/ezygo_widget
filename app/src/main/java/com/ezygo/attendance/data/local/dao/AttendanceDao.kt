package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ezygo.attendance.data.local.entity.AttendanceRecordEntity
import com.ezygo.attendance.data.local.entity.CourseEntity
import com.ezygo.attendance.data.local.entity.EnrolledCourseEntity
import com.ezygo.attendance.data.local.entity.PeriodEntity
import com.ezygo.attendance.data.local.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    // ---- reads (always period-scoped) -----------------------------------

    @Query("SELECT * FROM attendance_records WHERE periodKey = :periodKey ORDER BY dateKey DESC, sessionId ASC")
    fun recordsFor(periodKey: String): Flow<List<AttendanceRecordEntity>>

    @Query("SELECT * FROM enrolled_courses WHERE periodKey = :periodKey ORDER BY name ASC")
    fun enrolledCoursesFor(periodKey: String): Flow<List<EnrolledCourseEntity>>

    @Query("SELECT * FROM sessions WHERE periodKey = :periodKey ORDER BY viewOrder ASC, sessionId ASC")
    fun sessionsFor(periodKey: String): Flow<List<SessionEntity>>

    @Query("SELECT * FROM periods WHERE periodKey = :periodKey")
    fun periodFor(periodKey: String): Flow<PeriodEntity?>

    @Query("SELECT COUNT(*) FROM periods WHERE periodKey = :periodKey")
    suspend fun hasSnapshot(periodKey: String): Int

    // ---- one-shot reads --------------------------------------------------
    //
    // The widget renders once per update and then the process may die, so it has
    // no use for Room's InvalidationTracker. These plain suspend queries skip
    // registering an observer, which matters when the work is happening inside a
    // broadcast's limited window on a cold process.

    @Query("SELECT * FROM attendance_records WHERE periodKey = :periodKey ORDER BY dateKey DESC, sessionId ASC")
    suspend fun recordsOnce(periodKey: String): List<AttendanceRecordEntity>

    @Query("SELECT * FROM enrolled_courses WHERE periodKey = :periodKey ORDER BY name ASC")
    suspend fun enrolledCoursesOnce(periodKey: String): List<EnrolledCourseEntity>

    @Query("SELECT * FROM sessions WHERE periodKey = :periodKey ORDER BY viewOrder ASC, sessionId ASC")
    suspend fun sessionsOnce(periodKey: String): List<SessionEntity>

    @Query("SELECT * FROM periods WHERE periodKey = :periodKey")
    suspend fun periodOnce(periodKey: String): PeriodEntity?

    // ---- writes ----------------------------------------------------------

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeriod(period: PeriodEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCourses(courses: List<CourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEnrolledCourses(courses: List<EnrolledCourseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<SessionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecords(records: List<AttendanceRecordEntity>)

    @Query("DELETE FROM attendance_records WHERE periodKey = :periodKey")
    suspend fun deleteRecords(periodKey: String)

    @Query("DELETE FROM courses WHERE periodKey = :periodKey")
    suspend fun deleteCourses(periodKey: String)

    @Query("DELETE FROM enrolled_courses WHERE periodKey = :periodKey")
    suspend fun deleteEnrolledCourses(periodKey: String)

    @Query("DELETE FROM sessions WHERE periodKey = :periodKey")
    suspend fun deleteSessions(periodKey: String)

    /**
     * Replaces one period's snapshot atomically. Scoped deletes mean a refresh can
     * never disturb another semester's cached data, and a crash mid-write cannot
     * leave a half-updated period visible.
     */
    @Transaction
    suspend fun replaceSnapshot(
        period: PeriodEntity,
        courses: List<CourseEntity>,
        enrolled: List<EnrolledCourseEntity>,
        sessions: List<SessionEntity>,
        records: List<AttendanceRecordEntity>,
    ) {
        deleteRecords(period.periodKey)
        deleteCourses(period.periodKey)
        deleteEnrolledCourses(period.periodKey)
        deleteSessions(period.periodKey)
        upsertPeriod(period)
        insertCourses(courses)
        insertEnrolledCourses(enrolled)
        insertSessions(sessions)
        insertRecords(records)
    }

    @Query("DELETE FROM attendance_records")
    suspend fun clearAllRecords()

    @Query("DELETE FROM periods")
    suspend fun clearAllPeriods()

    @Query("DELETE FROM courses")
    suspend fun clearAllCourses()

    @Query("DELETE FROM enrolled_courses")
    suspend fun clearAllEnrolled()

    @Query("DELETE FROM sessions")
    suspend fun clearAllSessions()
}
