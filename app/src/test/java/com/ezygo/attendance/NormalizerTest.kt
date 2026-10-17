package com.ezygo.attendance

import com.ezygo.attendance.Fixtures.COURSE_FDS
import com.ezygo.attendance.Fixtures.TYPE_ABSENT
import com.ezygo.attendance.Fixtures.TYPE_DUTY_LEAVE
import com.ezygo.attendance.Fixtures.TYPE_PRESENT
import com.ezygo.attendance.Fixtures.cell
import com.ezygo.attendance.domain.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizerTest {

    @Test
    fun `empty session with null course and null attendance is dropped`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(null, null), 3 to cell(COURSE_FDS, TYPE_PRESENT))),
        )
        val records = Normalizer.normalize(report)
        assertEquals(1, records.size)
        assertEquals(3, records.first().sessionId)
    }

    @Test
    fun `course present but attendance not marked is not counted as absent`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(COURSE_FDS, null))),
        )
        assertTrue(Normalizer.normalize(report).isEmpty())
    }

    @Test
    fun `attendance marked but no course is dropped`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(null, TYPE_PRESENT))),
        )
        assertTrue(Normalizer.normalize(report).isEmpty())
    }

    @Test
    fun `duty leave is positive and keeps its own code`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(COURSE_FDS, TYPE_DUTY_LEAVE))),
        )
        val record = Normalizer.normalize(report).single()
        assertTrue(record.isPositive)
        assertEquals("DL", record.attendanceCode)
        assertEquals("Duty Leave", record.attendanceName)
    }

    @Test
    fun `absent is not positive`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(COURSE_FDS, TYPE_ABSENT))),
        )
        assertEquals(false, Normalizer.normalize(report).single().isPositive)
    }

    @Test
    fun `course id missing from metadata still produces a labelled record`() {
        val ghostCourse = 999999
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(ghostCourse, TYPE_PRESENT))),
        )
        val record = Normalizer.normalize(report).single()
        assertEquals(ghostCourse, record.courseId)
        assertTrue(record.courseName.contains("999999"))
        assertEquals(null, record.courseCode)
    }

    @Test
    fun `attendance type missing from metadata is skipped rather than guessed`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(1 to cell(COURSE_FDS, 4242))),
        )
        assertTrue(Normalizer.normalize(report).isEmpty())
    }

    @Test
    fun `records are sorted newest date first then by session order`() {
        val report = Fixtures.report(
            mapOf(
                "20260818" to mapOf(4 to cell(COURSE_FDS, TYPE_PRESENT), 1 to cell(COURSE_FDS, TYPE_PRESENT)),
                "20260819" to mapOf(3 to cell(COURSE_FDS, TYPE_PRESENT)),
            ),
        )
        val records = Normalizer.normalize(report)
        assertEquals(listOf("20260819", "20260818", "20260818"), records.map { it.dateKey })
        assertEquals(listOf(3, 1, 4), records.map { it.sessionId })
    }

    @Test
    fun `session name and date display come from the api payload`() {
        val report = Fixtures.report(
            mapOf("20260819" to mapOf(3 to cell(COURSE_FDS, TYPE_PRESENT))),
        )
        val record = Normalizer.normalize(report).single()
        assertEquals("III", record.sessionName)
        assertEquals("19.8.26", record.displayDate)
        assertEquals("Wednesday", record.dayName)
        assertNotNull(record.courseCode)
    }

    @Test
    fun `a semester with no recorded classes normalizes to nothing`() {
        assertTrue(Normalizer.normalize(Fixtures.report(emptyMap())).isEmpty())
    }
}
