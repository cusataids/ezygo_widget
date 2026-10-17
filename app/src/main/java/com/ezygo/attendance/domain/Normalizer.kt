package com.ezygo.attendance.domain

import com.ezygo.attendance.data.remote.dto.ParsedReport
import com.ezygo.attendance.domain.model.AttendanceRecord

/**
 * Flattens the date x session grid into a flat list of genuinely recorded classes.
 *
 * The single most important rule: a cell only becomes a record when it has BOTH a
 * course and an attendance status. A null course means no class was scheduled; a
 * null attendance means nothing was marked. Neither is an absence, and neither may
 * enter the statistics.
 */
object Normalizer {

    fun normalize(report: ParsedReport): List<AttendanceRecord> {
        val sessionNames = report.sessions.associate { it.id to it.name }
        // Preserve the session ordering from the API for stable within-day sorting.
        val sessionOrder = report.sessions.withIndex().associate { (index, s) -> s.id to index }

        val records = ArrayList<AttendanceRecord>()
        // Guards against the same (date, session) arriving twice in one payload.
        val seen = HashSet<Pair<String, Int>>()

        for ((dateKey, cellsBySession) in report.attendanceData) {
            val dateInfo = report.dates[dateKey]
            for ((sessionId, cell) in cellsBySession) {
                val courseId = cell.courseId ?: continue
                val attendanceTypeId = cell.attendanceTypeId ?: continue
                if (!seen.add(dateKey to sessionId)) continue

                val type = report.attendanceTypes[attendanceTypeId]
                // An attendance id with no metadata cannot be scored honestly, so it
                // is skipped rather than guessed at as present or absent.
                    ?: continue
                val course = report.courses[courseId]

                records += AttendanceRecord(
                    dateKey = dateKey,
                    displayDate = dateInfo?.date ?: formatDateKey(dateKey),
                    dayName = dateInfo?.day.orEmpty(),
                    sessionId = sessionId,
                    sessionName = sessionNames[sessionId] ?: sessionId.toString(),
                    courseId = courseId,
                    // A course present in attendance but missing from course metadata
                    // still counts; it is just labelled honestly.
                    courseName = course?.name ?: "Unknown course #$courseId",
                    courseCode = course?.code,
                    attendanceTypeId = attendanceTypeId,
                    attendanceName = type.name,
                    attendanceCode = type.code,
                    isPositive = type.positiveReportValue == 1,
                    lessonPlans = cell.lessonPlans,
                    deliveryMethods = cell.deliveryMethods,
                    classType = cell.classType,
                )
            }
        }

        return records.sortedWith(
            compareByDescending<AttendanceRecord> { it.sortableDate }
                .thenBy { sessionOrder[it.sessionId] ?: it.sessionId },
        )
    }

    /** Fallback display for a "YYYYMMDD" key when attendanceDatesArray has no entry. */
    private fun formatDateKey(dateKey: String): String {
        if (dateKey.length != 8 || dateKey.toIntOrNull() == null) return dateKey
        val year = dateKey.substring(2, 4)
        val month = dateKey.substring(4, 6).trimStart('0')
        val day = dateKey.substring(6, 8).trimStart('0')
        return "$day.$month.$year"
    }
}
