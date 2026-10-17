package com.ezygo.attendance.domain.model

/**
 * One actually-recorded class: a (date, session) cell that carried both a course
 * and an attendance status. Cells with a null course or a null attendance status
 * are dropped during normalization and never become records — a missing record is
 * not an absence.
 */
data class AttendanceRecord(
    /** Raw API date key, "YYYYMMDD". The API date is the source of truth. */
    val dateKey: String,
    /** Display string straight from attendanceDatesArray, e.g. "19.8.26". */
    val displayDate: String,
    /** e.g. "Wednesday". */
    val dayName: String,
    val sessionId: Int,
    /** Session label, e.g. "III". */
    val sessionName: String,
    val courseId: Int,
    val courseName: String,
    val courseCode: String?,
    val attendanceTypeId: Int,
    /** e.g. "Present", "Absent", "Duty Leave". */
    val attendanceName: String,
    /** e.g. "P", "AB", "DL". */
    val attendanceCode: String,
    /**
     * Taken from the attendance type's positive_report_value, never inferred from
     * the code string. Present and Duty Leave are both positive.
     */
    val isPositive: Boolean,
    val lessonPlans: String? = null,
    val deliveryMethods: String? = null,
    val classType: String? = null,
) {
    /** Sortable numeric form of [dateKey]; 0 if the key is not the expected shape. */
    val sortableDate: Int get() = dateKey.toIntOrNull() ?: 0
}
