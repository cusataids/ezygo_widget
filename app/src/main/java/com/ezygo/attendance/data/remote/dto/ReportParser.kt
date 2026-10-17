package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Tolerant decoding of the detailed attendance report.
 *
 * Every accessor here is deliberately defensive about shape: a section may be a
 * keyed object, an indexed array, an empty array standing in for an empty object,
 * or absent entirely. Numbers may arrive as JSON numbers or as strings. None of
 * those variations should crash the app or silently invent data.
 */
object ReportParser {

    fun parse(dto: DetailedReportDto): ParsedReport = ParsedReport(
        courses = parseCourses(dto.courses),
        sessions = parseSessions(dto.sessions),
        attendanceTypes = parseAttendanceTypes(dto.attendanceTypes),
        attendanceData = parseAttendanceData(dto.studentAttendanceData),
        dates = parseDates(dto.attendanceDatesArray),
    )

    // ---- section parsers -------------------------------------------------

    private fun parseCourses(element: JsonElement?): Map<Int, CourseDto> =
        element.values().mapNotNull { entry ->
            val obj = entry.asObject() ?: return@mapNotNull null
            val id = obj.int("id") ?: return@mapNotNull null
            id to CourseDto(
                id = id,
                serialNo = obj.int("si_no"),
                name = obj.string("name") ?: "Course #$id",
                code = obj.string("code"),
                academicYear = obj.string("academic_year"),
                academicSemester = obj.string("academic_semester"),
            )
        }.toMap()

    private fun parseSessions(element: JsonElement?): List<SessionDto> =
        element.values().mapNotNull { entry ->
            val obj = entry.asObject() ?: return@mapNotNull null
            val id = obj.int("id") ?: return@mapNotNull null
            SessionDto(
                id = id,
                name = obj.string("name") ?: "$id",
                timeFrom = obj.string("time_from"),
                timeTo = obj.string("time_to"),
                viewOrder = obj.int("view_order"),
                type = obj.string("type"),
            )
        }.sortedBy { it.viewOrder ?: it.id }

    private fun parseAttendanceTypes(element: JsonElement?): Map<Int, AttendanceTypeDto> =
        element.values().mapNotNull { entry ->
            val obj = entry.asObject() ?: return@mapNotNull null
            val id = obj.int("id") ?: return@mapNotNull null
            id to AttendanceTypeDto(
                id = id,
                name = obj.string("name") ?: "Type #$id",
                code = obj.string("code") ?: "?",
                // Absent metadata is treated as non-positive rather than guessed at.
                positiveReportValue = obj.int("positive_report_value") ?: 0,
            )
        }.toMap()

    private fun parseAttendanceData(element: JsonElement?): Map<String, Map<Int, AttendanceCellDto>> {
        val root = element.asObject() ?: return emptyMap()
        return root.mapNotNull { (dateKey, sessionsElement) ->
            val sessionsObj = sessionsElement.asObject() ?: return@mapNotNull null
            val cells = sessionsObj.mapNotNull inner@{ (sessionKey, cellElement) ->
                val sessionId = sessionKey.toIntOrNull() ?: return@inner null
                val cellObj = cellElement.asObject() ?: return@inner null
                sessionId to AttendanceCellDto(
                    courseId = cellObj.int("course"),
                    attendanceTypeId = cellObj.int("attendance"),
                    lessonPlans = cellObj.string("lesson_plans"),
                    deliveryMethods = cellObj.string("delivery_methods"),
                    classType = cellObj.string("class_type"),
                )
            }.toMap()
            dateKey to cells
        }.toMap()
    }

    private fun parseDates(element: JsonElement?): Map<String, AttendanceDateDto> =
        element.values().mapNotNull { entry ->
            val obj = entry.asObject() ?: return@mapNotNull null
            val key = obj.string("key") ?: return@mapNotNull null
            key to AttendanceDateDto(
                key = key,
                date = obj.string("date"),
                day = obj.string("day"),
            )
        }.toMap()

    // ---- shape helpers ---------------------------------------------------

    /**
     * Values of a section regardless of whether it came through as a keyed object
     * or an indexed array (including the empty-array-for-empty-object case).
     */
    private fun JsonElement?.values(): List<JsonElement> = when (this) {
        is JsonObject -> values.toList()
        is JsonArray -> toList()
        else -> emptyList()
    }

    private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.toIntOrNull() ?: primitive.content.toDoubleOrNull()?.toInt()
    }

    private fun JsonObject.string(key: String): String? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return primitive.content.takeIf { it.isNotBlank() && it != "null" }
    }
}
