package com.ezygo.attendance

import com.ezygo.attendance.data.remote.dto.AttendanceCellDto
import com.ezygo.attendance.data.remote.dto.AttendanceDateDto
import com.ezygo.attendance.data.remote.dto.AttendanceTypeDto
import com.ezygo.attendance.data.remote.dto.CourseDto
import com.ezygo.attendance.data.remote.dto.ParsedReport
import com.ezygo.attendance.data.remote.dto.SessionDto

/** Shared builders so each test only states the part it cares about. */
object Fixtures {

    const val TYPE_PRESENT = 1
    const val TYPE_ABSENT = 2
    const val TYPE_DUTY_LEAVE = 3

    const val COURSE_FDS = 96189
    const val COURSE_OS = 96190

    val attendanceTypes = mapOf(
        TYPE_PRESENT to AttendanceTypeDto(TYPE_PRESENT, "Present", "P", 1),
        TYPE_ABSENT to AttendanceTypeDto(TYPE_ABSENT, "Absent", "AB", 0),
        TYPE_DUTY_LEAVE to AttendanceTypeDto(TYPE_DUTY_LEAVE, "Duty Leave", "DL", 1),
    )

    val courses = mapOf(
        COURSE_FDS to CourseDto(COURSE_FDS, 1, "Fundamentals of Data Science", "24-813-0502", "2026-27", "odd"),
        COURSE_OS to CourseDto(COURSE_OS, 2, "Operating System", "24-813-0503", "2026-27", "odd"),
    )

    val sessions = listOf(
        SessionDto(1, "I", "09:00", "09:55", 1, "forenoon"),
        SessionDto(2, "II", "10:00", "10:55", 2, "forenoon"),
        SessionDto(3, "III", "11:00", "11:55", 3, "forenoon"),
        SessionDto(4, "IV", "12:00", "12:55", 4, "forenoon"),
        SessionDto(5, "V", "14:00", "14:55", 5, "afternoon"),
        SessionDto(6, "VI", "15:00", "15:55", 6, "afternoon"),
    )

    fun cell(courseId: Int?, attendanceTypeId: Int?) =
        AttendanceCellDto(courseId, attendanceTypeId, null, "Lecture", null)

    fun report(
        attendanceData: Map<String, Map<Int, AttendanceCellDto>>,
        courses: Map<Int, CourseDto> = Fixtures.courses,
        types: Map<Int, AttendanceTypeDto> = attendanceTypes,
    ) = ParsedReport(
        courses = courses,
        sessions = sessions,
        attendanceTypes = types,
        attendanceData = attendanceData,
        dates = attendanceData.keys.associateWith { AttendanceDateDto(it, "19.8.26", "Wednesday") },
    )
}
