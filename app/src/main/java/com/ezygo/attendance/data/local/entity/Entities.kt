package com.ezygo.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Everything cached is scoped by [periodKey] (academic year + semester). Reads are
 * always filtered by it, so two semesters can never appear in the same figures.
 */

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey val periodKey: String,
    val academicYear: String,
    val semester: String,
    /** When this period's attendance was last successfully refreshed. */
    val lastUpdatedEpochMs: Long,
)

@Entity(tableName = "courses", primaryKeys = ["periodKey", "courseId"])
data class CourseEntity(
    val periodKey: String,
    val courseId: Int,
    val name: String,
    val code: String?,
    val serialNo: Int?,
)

/**
 * A course the student holds a role in. Deliberately separate from [CourseEntity]:
 * being enrolled does not imply any attendance was ever recorded, and the two lists
 * genuinely differ in the live data.
 */
@Entity(tableName = "enrolled_courses", primaryKeys = ["periodKey", "courseId"])
data class EnrolledCourseEntity(
    val periodKey: String,
    val courseId: Int,
    val name: String,
)

@Entity(tableName = "sessions", primaryKeys = ["periodKey", "sessionId"])
data class SessionEntity(
    val periodKey: String,
    val sessionId: Int,
    val name: String,
    val timeFrom: String?,
    val timeTo: String?,
    val viewOrder: Int?,
    val type: String?,
)

@Entity(
    tableName = "attendance_records",
    primaryKeys = ["periodKey", "dateKey", "sessionId"],
    indices = [Index("periodKey", "courseId"), Index("periodKey", "dateKey")],
)
data class AttendanceRecordEntity(
    val periodKey: String,
    val dateKey: String,
    val sessionId: Int,
    val displayDate: String,
    val dayName: String,
    val sessionName: String,
    val courseId: Int,
    val courseName: String,
    val courseCode: String?,
    val attendanceTypeId: Int,
    val attendanceName: String,
    val attendanceCode: String,
    val isPositive: Boolean,
    val lessonPlans: String?,
    val deliveryMethods: String?,
    val classType: String?,
)

/** Single-row snapshot of the signed-in user, so Profile can render from cache. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val username: String?,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val institutionName: String?,
    val institutionRole: String?,
    val enrollStatus: String?,
    val defaultAcademicYear: String?,
    val defaultSemester: String?,
    val lastUpdatedEpochMs: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
