package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.entity.AttendanceRecordEntity
import com.ezygo.attendance.data.local.entity.SessionEntity
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.Period

/** Entity <-> domain translation, kept in one place so neither layer leaks. */

fun AttendanceRecord.toEntity(period: Period) = AttendanceRecordEntity(
    periodKey = period.key,
    dateKey = dateKey,
    sessionId = sessionId,
    displayDate = displayDate,
    dayName = dayName,
    sessionName = sessionName,
    courseId = courseId,
    courseName = courseName,
    courseCode = courseCode,
    attendanceTypeId = attendanceTypeId,
    attendanceName = attendanceName,
    attendanceCode = attendanceCode,
    isPositive = isPositive,
    lessonPlans = lessonPlans,
    deliveryMethods = deliveryMethods,
    classType = classType,
)

fun AttendanceRecordEntity.toDomain() = AttendanceRecord(
    dateKey = dateKey,
    displayDate = displayDate,
    dayName = dayName,
    sessionId = sessionId,
    sessionName = sessionName,
    courseId = courseId,
    courseName = courseName,
    courseCode = courseCode,
    attendanceTypeId = attendanceTypeId,
    attendanceName = attendanceName,
    attendanceCode = attendanceCode,
    isPositive = isPositive,
    lessonPlans = lessonPlans,
    deliveryMethods = deliveryMethods,
    classType = classType,
)

/**
 * One period of the institution's day.
 *
 * [type] is the portal's own "forenoon" / "afternoon" mark. It is the only reliable
 * way to tell the two halves of the day apart: [timeFrom] and [timeTo] are declared by
 * the API but arrive null for every session on this institution, so anything derived
 * from clock times would be derived from nothing.
 */
data class SessionInfo(
    val id: Int,
    val name: String,
    val timeFrom: String?,
    val timeTo: String?,
    val type: String?,
)

fun SessionEntity.toInfo() = SessionInfo(sessionId, name, timeFrom, timeTo, type)
