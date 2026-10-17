package com.ezygo.attendance

import com.ezygo.attendance.domain.AbsenceDays
import com.ezygo.attendance.domain.model.AttendanceDay
import com.ezygo.attendance.domain.model.AttendanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The worst days, and what the filter does to them.
 *
 * The ordering and the filter are the whole feature, and both are easy to get subtly
 * wrong in ways nobody would notice on screen: a tie broken the wrong way, duty leave
 * counted as an absence, or a filter that shortens the list without reordering it.
 */
class AbsenceDaysTest {

    private fun record(dateKey: String, session: Int, positive: Boolean) = AttendanceRecord(
        dateKey = dateKey,
        displayDate = dateKey,
        dayName = "Monday",
        sessionId = session,
        sessionName = "S$session",
        courseId = session,
        courseName = "Course $session",
        courseCode = null,
        attendanceTypeId = if (positive) 1 else 2,
        attendanceName = if (positive) "Present" else "Absent",
        attendanceCode = if (positive) "P" else "AB",
        isPositive = positive,
    )

    /** A day written as a string of marks: "AAP" is absent, absent, present. */
    private fun day(dateKey: String, marks: String) = AttendanceDay(
        dateKey = dateKey,
        displayDate = dateKey,
        dayName = "Monday",
        records = marks.mapIndexed { i, c -> record(dateKey, i + 1, c == 'P') },
    )

    @Test
    fun `ranks by absences, worst first`() {
        val ranked = AbsenceDays.rank(
            listOf(
                day("20261101", "AP"),
                day("20261102", "AAAA"),
                day("20261103", "AAP"),
            ),
        )
        assertEquals(listOf("20261102", "20261103", "20261101"), ranked.map { it.dateKey })
        assertEquals(listOf(4, 2, 1), ranked.map { it.absences })
    }

    @Test
    fun `days with nothing missed are left out entirely`() {
        val ranked = AbsenceDays.rank(listOf(day("20261101", "PPP"), day("20261102", "AP")))
        assertEquals(listOf("20261102"), ranked.map { it.dateKey })
    }

    @Test
    fun `a tie goes to the more recent day`() {
        val ranked = AbsenceDays.rank(listOf(day("20261101", "AA"), day("20261130", "AA")))
        assertEquals(listOf("20261130", "20261101"), ranked.map { it.dateKey })
    }

    @Test
    fun `at most ten rows`() {
        val many = (1..20).map { day("202611%02d".format(it), "AAA") }
        assertEquals(AbsenceDays.TOP_N, AbsenceDays.rank(many).size)
        assertEquals(10, AbsenceDays.TOP_N)
    }

    @Test
    fun `the filter keeps only days with nothing attended`() {
        val ranked = AbsenceDays.rank(
            listOf(
                day("20261101", "AAAAP"),
                day("20261102", "AA"),
                day("20261103", "AAA"),
            ),
            wholeDaysOnly = true,
        )
        assertEquals(listOf("20261103", "20261102"), ranked.map { it.dateKey })
        assertTrue(ranked.all { it.wholeDay })
    }

    @Test
    fun `the filter reorders, it does not merely shorten`() {
        // The worst day unfiltered has a present mark on it, so filtering promotes a
        // day that was second. A filter implemented as "take the same top ten, then
        // drop some" would still show the four-absence day first.
        val days = listOf(day("20261101", "AAAAP"), day("20261102", "AAA"))
        assertEquals("20261101", AbsenceDays.rank(days).first().dateKey)
        assertEquals("20261102", AbsenceDays.rank(days, wholeDaysOnly = true).first().dateKey)
    }

    @Test
    fun `duty leave counts as attended, so the day is not a whole-day absence`() {
        // Duty leave is positive in the portal's own reckoning. A day spent
        // representing the college is not a day you went missing.
        val ranked = AbsenceDays.rank(listOf(day("20261101", "AAP")), wholeDaysOnly = true)
        assertTrue(ranked.isEmpty())

        val unfiltered = AbsenceDays.rank(listOf(day("20261101", "AAP")))
        assertEquals(2, unfiltered.single().absences)
        assertEquals(3, unfiltered.single().recorded)
        assertFalse(unfiltered.single().wholeDay)
    }

    @Test
    fun `no records at all gives no rows`() {
        assertTrue(AbsenceDays.rank(emptyList()).isEmpty())
        assertTrue(AbsenceDays.rank(listOf(day("20261101", ""))).isEmpty())
    }
}
