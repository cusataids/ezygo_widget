package com.ezygo.attendance.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import com.ezygo.attendance.data.local.entity.CourseShortCodeEntity
import com.ezygo.attendance.data.local.entity.HiddenFromHomeEntity
import com.ezygo.attendance.data.local.entity.SavedCourseEntity
import com.ezygo.attendance.data.local.entity.SemesterCourseEntity
import com.ezygo.attendance.data.local.entity.TimetableOverrideEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CourseShortCodeDao {

    @Query("SELECT * FROM course_short_codes")
    fun all(): Flow<List<CourseShortCodeEntity>>

    @Query("SELECT * FROM course_short_codes")
    suspend fun allOnce(): List<CourseShortCodeEntity>

    @Upsert
    suspend fun upsert(code: CourseShortCodeEntity)

    @Query("DELETE FROM course_short_codes WHERE courseKey = :courseKey")
    suspend fun delete(courseKey: String)

    @Query("DELETE FROM course_short_codes")
    suspend fun clearAll()
}

@Dao
interface TimetableOverrideDao {

    @Query("SELECT * FROM timetable_overrides WHERE periodKey = :periodKey")
    fun forPeriod(periodKey: String): Flow<List<TimetableOverrideEntity>>

    @Query("SELECT * FROM timetable_overrides WHERE periodKey = :periodKey")
    suspend fun forPeriodOnce(periodKey: String): List<TimetableOverrideEntity>

    @Upsert
    suspend fun upsert(override: TimetableOverrideEntity)

    @Delete
    suspend fun delete(override: TimetableOverrideEntity)

    @Query(
        "DELETE FROM timetable_overrides WHERE periodKey = :periodKey " +
            "AND dayOfWeek = :dayOfWeek AND sessionId = :sessionId",
    )
    suspend fun deleteAt(periodKey: String, dayOfWeek: Int, sessionId: Int)

    @Query("DELETE FROM timetable_overrides")
    suspend fun clearAll()
}

@Dao
interface HiddenFromHomeDao {

    /** Most recently hidden first, which is the order they are looked for in. */
    @Query("SELECT * FROM hidden_from_home ORDER BY hiddenEpochMs DESC")
    fun all(): Flow<List<HiddenFromHomeEntity>>

    @Upsert
    suspend fun hide(entry: HiddenFromHomeEntity)

    @Query("DELETE FROM hidden_from_home WHERE id = :id")
    suspend fun show(id: String)

    @Query("DELETE FROM hidden_from_home")
    suspend fun clearAll()
}

@Dao
interface SemesterCourseDao {

    @Query("SELECT * FROM semester_courses ORDER BY semester ASC, code ASC")
    fun all(): Flow<List<SemesterCourseEntity>>

    @Upsert
    suspend fun upsert(courses: List<SemesterCourseEntity>)

    /** A re-read of one card replaces that semester rather than adding to it. */
    @Query("DELETE FROM semester_courses WHERE semester = :semester")
    suspend fun deleteSemester(semester: Int)

    @Query("DELETE FROM semester_courses")
    suspend fun clearAll()
}

@Dao
interface SavedCourseDao {

    @Query("SELECT * FROM saved_courses ORDER BY name ASC")
    fun all(): Flow<List<SavedCourseEntity>>

    @Upsert
    suspend fun upsert(course: SavedCourseEntity)

    @Query("DELETE FROM saved_courses WHERE courseKey = :courseKey")
    suspend fun delete(courseKey: String)

    @Query("DELETE FROM saved_courses")
    suspend fun clearAll()
}

/** Not a real DAO — the identifiers the hidden table stores, in one place. */
object HiddenKeys {
    private const val EVENT = "event:"
    private const val TASK = "task:"

    fun event(id: String) = EVENT + id
    fun task(id: String) = TASK + id

    /** True for a hidden key that names a calendar event rather than a task. */
    fun isEvent(key: String) = key.startsWith(EVENT)
}

/** The key a saved course is filed under; see [SavedCourseEntity]. */
fun savedCourseKey(code: String?, name: String): String =
    code?.trim()?.takeIf { it.isNotEmpty() } ?: name.trim().lowercase()

/** The key a short code is filed under; see [CourseShortCodeEntity]. */
fun courseShortCodeKey(courseCode: String?, courseId: Int?): String? = when {
    !courseCode.isNullOrBlank() -> courseCode.trim()
    courseId != null -> "id:$courseId"
    else -> null
}
