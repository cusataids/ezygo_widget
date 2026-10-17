package com.ezygo.attendance

import com.ezygo.attendance.domain.AttendedDays
import com.ezygo.attendance.domain.model.AttendanceDay
import com.ezygo.attendance.domain.model.AttendanceRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The best days, and the order they come in.
 *
 * The ordering is the whole feature and it has three levels, which is two more than
 * anyone can check by eye on a screen: fraction first, then the length of the day,
 * then the date.
 */
class AttendedDaysTest {

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

    /** A day written as a string of marks: "PPA" is present, present, absent. */
    private fun day(dateKey: String, marks: String) = AttendanceDay(
        dateKey = dateKey,
        displayDate = dateKey,
        dayName = "Monday",
        records = marks.mapIndexed { i, c -> record(dateKey, i + 1, c == 'P') },
    )

    @Test
    fun `ranks by the fraction attended, best first`() {
        val ranked = AttendedDays.rank(
            listOf(
                day("20261101", "PA"),
                day("20261102", "PPPP"),
                day("20261103", "PPA"),
            ),
        )
        assertEquals(listOf("20261102", "20261103", "20261101"), ranked.map { it.dateKey })
    }

    @Test
    fun `a longer day wins a tie on fraction`() {
        // Both perfect; one of them was a day's work.
        val ranked = AttendedDays.rank(listOf(day("20261101", "PP"), day("20261102", "PPPPPP")))
        assertEquals(listOf("20261102", "20261101"), ranked.map { it.dateKey })
        assertEquals(listOf(6, 2), ranked.map { it.recorded })
    }

    @Test
    fun `the more recent day wins a tie on fraction and length`() {
        val ranked = AttendedDays.rank(listOf(day("20261101", "PPP"), day("20261130", "PPP")))
        assertEquals(listOf("20261130", "20261101"), ranked.map { it.dateKey })
    }

    @Test
    fun `days with nothing attended are left out`() {
        val ranked = AttendedDays.rank(listOf(day("20261101", "AAA"), day("20261102", "PA")))
        assertEquals(listOf("20261102"), ranked.map { it.dateKey })
    }

    @Test
    fun `percent and whole-day are read off the marks`() {
        val full = AttendedDays.rank(listOf(day("20261101", "PPPP"))).single()
        assertEquals(100.0, full.percent, 1e-9)
        assertTrue(full.wholeDay)

        val partial = AttendedDays.rank(listOf(day("20261102", "PPPA"))).single()
        assertEquals(75.0, partial.percent, 1e-9)
        assertEquals(3, partial.attended)
        assertEquals(4, partial.recorded)
        assertEquals(false, partial.wholeDay)
    }

    @Test
    fun `duty leave counts as attended`() {
        // Positive in the portal's own reckoning, so a day of it is a full day.
        val ranked = AttendedDays.rank(listOf(day("20261101", "PP"))).single()
        assertTrue(ranked.wholeDay)
    }

    @Test
    fun `everything is returned unless a limit says otherwise`() {
        val many = (1..20).map { day("202611%02d".format(it), "PP") }
        assertEquals(20, AttendedDays.rank(many).size)
        assertEquals(5, AttendedDays.rank(many, limit = 5).size)
    }

    @Test
    fun `no records at all gives no rows`() {
        assertTrue(AttendedDays.rank(emptyList()).isEmpty())
        assertTrue(AttendedDays.rank(listOf(day("20261101", ""))).isEmpty())
    }
}
