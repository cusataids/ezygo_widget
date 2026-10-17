package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * The detailed attendance report.
 *
 * The keyed sections arrive as JSON *objects* when populated. PHP-backed APIs
 * commonly serialise an empty associative array as `[]` instead of `{}`, which
 * would blow up strict Map decoding, so these stay as raw [JsonElement] and are
 * decoded tolerantly by [ReportParser].
 */
@Serializable
data class DetailedReportDto(
    val courses: JsonElement? = null,
    val sessions: JsonElement? = null,
    val attendanceTypes: JsonElement? = null,
    val studentAttendanceData: JsonElement? = null,
    val attendanceDatesArray: JsonElement? = null,
)

data class CourseDto(
    val id: Int,
    val serialNo: Int?,
    val name: String,
    val code: String?,
    val academicYear: String?,
    val academicSemester: String?,
)

data class SessionDto(
    val id: Int,
    val name: String,
    val timeFrom: String?,
    val timeTo: String?,
    val viewOrder: Int?,
    val type: String?,
)

data class AttendanceTypeDto(
    val id: Int,
    val name: String,
    val code: String,
    /** 1 means the type counts toward attendance. Present and Duty Leave are both 1. */
    val positiveReportValue: Int,
)

/**
 * One (date, session) cell. Both fields are nullable and a null in either one
 * means "no class recorded here" — never "absent".
 */
data class AttendanceCellDto(
    val courseId: Int?,
    val attendanceTypeId: Int?,
    val lessonPlans: String?,
    val deliveryMethods: String?,
    val classType: String?,
)

data class AttendanceDateDto(
    val key: String,
    val date: String?,
    val day: String?,
)

/** Fully decoded report, ready for the domain normalizer. */
data class ParsedReport(
    val courses: Map<Int, CourseDto>,
    val sessions: List<SessionDto>,
    val attendanceTypes: Map<Int, AttendanceTypeDto>,
    /** dateKey -> (sessionId -> cell) */
    val attendanceData: Map<String, Map<Int, AttendanceCellDto>>,
    val dates: Map<String, AttendanceDateDto>,
) {
    companion object {
        val EMPTY = ParsedReport(emptyMap(), emptyList(), emptyMap(), emptyMap(), emptyMap())
    }
}
