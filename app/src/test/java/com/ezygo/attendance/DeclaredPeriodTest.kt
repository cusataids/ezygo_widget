package com.ezygo.attendance

import com.ezygo.attendance.data.remote.dto.CourseDto
import com.ezygo.attendance.data.repo.AttendanceRepository
import com.ezygo.attendance.domain.model.Period
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The report is cached under the period its own course metadata declares, so a
 * default changed elsewhere (the web portal, another device) cannot cause one
 * semester's attendance to be stored under another semester's key.
 */
class DeclaredPeriodTest {

    private fun course(id: Int, year: String?, semester: String?) =
        CourseDto(id, id, "Course $id", "C$id", year, semester)

    @Test
    fun `a consistent report declares its own period`() {
        val courses = listOf(
            course(1, "2025-26", "even"),
            course(2, "2025-26", "even"),
        )
        assertEquals(Period("2025-26", "even"), AttendanceRepository.declaredPeriod(courses))
    }

    @Test
    fun `courses disagreeing about the period declare nothing`() {
        val courses = listOf(
            course(1, "2025-26", "even"),
            course(2, "2026-27", "odd"),
        )
        assertNull(AttendanceRepository.declaredPeriod(courses))
    }

    @Test
    fun `courses without period metadata declare nothing`() {
        assertNull(AttendanceRepository.declaredPeriod(listOf(course(1, null, null))))
        assertNull(AttendanceRepository.declaredPeriod(listOf(course(1, "2025-26", null))))
        assertNull(AttendanceRepository.declaredPeriod(listOf(course(1, "", "odd"))))
    }

    @Test
    fun `an empty report declares nothing`() {
        assertNull(AttendanceRepository.declaredPeriod(emptyList()))
    }
}
