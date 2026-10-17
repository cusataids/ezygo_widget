package com.ezygo.attendance

import com.ezygo.attendance.data.repo.SessionInfo
import com.ezygo.attendance.domain.model.AttendanceDay
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.ClaimDay
import com.ezygo.attendance.domain.model.ClaimSessionType
import com.ezygo.attendance.domain.model.countByCourse
import com.ezygo.attendance.domain.model.resolveClaimedSessions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What a claim is worth.
 *
 * A claim only ever converts an absence that is already recorded, so these tests are
 * mostly about what it must *not* reach: a period already attended, a day with nothing
 * on it, and the same period arrived at twice from the two entry routes.
 */
class AttendanceClaimTest {

    // The institution's real shape: I-IV forenoon, V-VI afternoon.
    private val sessions = listOf(
        SessionInfo(1, "I", null, null, "forenoon"),
        SessionInfo(2, "II", null, null, "forenoon"),
        SessionInfo(3, "III", null, null, "forenoon"),
        SessionInfo(4, "IV", null, null, "forenoon"),
        SessionInfo(5, "V", null, null, "afternoon"),
        SessionInfo(6, "VI", null, null, "afternoon"),
    )

    private fun record(
        dateKey: String,
        sessionId: Int,
        courseId: Int,
        positive: Boolean,
    ) = AttendanceRecord(
        dateKey = dateKey,
        displayDate = "10.3.26",
        dayName = "Tuesday",
        sessionId = sessionId,
        sessionName = "S$sessionId",
        courseId = courseId,
        courseName = "Course $courseId",
        courseCode = "C$courseId",
        attendanceTypeId = if (positive) 1 else 2,
        attendanceName = if (positive) "Present" else "Absent",
        attendanceCode = if (positive) "P" else "AB",
        isPositive = positive,
    )

    private val tenth = "20260310"
    private val eleventh = "20260311"

    private val days = listOf(
        AttendanceDay(
            dateKey = tenth,
            displayDate = "10.3.26",
            dayName = "Tuesday",
            records = listOf(
                record(tenth, 1, courseId = 11, positive = false),
                record(tenth, 2, courseId = 11, positive = false),
                record(tenth, 5, courseId = 22, positive = false),
                record(tenth, 6, courseId = 22, positive = true),
            ),
        ),
        AttendanceDay(
            dateKey = eleventh,
            displayDate = "11.3.26",
            dayName = "Wednesday",
            records = listOf(record(eleventh, 1, courseId = 11, positive = false)),
        ),
    )

    private fun claim(
        date: String,
        type: ClaimSessionType,
        custom: Set<Int> = emptySet(),
    ): ClaimDay {
        val day = LocalDate.of(
            date.substring(0, 4).toInt(),
            date.substring(4, 6).toInt(),
            date.substring(6, 8).toInt(),
        )
        return ClaimDay(day, type, custom)
    }

    private fun resolve(vararg claims: ClaimDay) =
        resolveClaimedSessions(days, sessions, claims.toList())

    // ---- what a claim reaches -------------------------------------------

    @Test
    fun `a full day claims every absence on it and nothing else`() {
        val resolved = resolve(claim(tenth, ClaimSessionType.FULL_DAY))
        assertEquals(3, resolved.size)
        assertEquals(mapOf(11 to 2, 22 to 1), resolved.countByCourse())
    }

    @Test
    fun `a period already attended is never claimed`() {
        val resolved = resolve(claim(tenth, ClaimSessionType.FULL_DAY))
        assertTrue(resolved.none { it.sessionId == 6 })
    }

    @Test
    fun `forenoon and afternoon follow the portal's own session type`() {
        assertEquals(2, resolve(claim(tenth, ClaimSessionType.FORENOON)).size)
        assertEquals(1, resolve(claim(tenth, ClaimSessionType.AFTERNOON)).size)
    }

    @Test
    fun `an afternoon claim reaches no course that only had morning classes`() {
        val resolved = resolve(claim(tenth, ClaimSessionType.AFTERNOON))
        assertEquals(mapOf(22 to 1), resolved.countByCourse())
    }

    @Test
    fun `a custom claim reaches only the ticked periods`() {
        val resolved = resolve(claim(tenth, ClaimSessionType.CUSTOM, setOf(1)))
        assertEquals(1, resolved.size)
        assertEquals(mapOf(11 to 1), resolved.countByCourse())
    }

    @Test
    fun `a claimed session is attributed to the course the record names`() {
        val resolved = resolve(claim(tenth, ClaimSessionType.CUSTOM, setOf(5)))
        assertEquals(mapOf(22 to 1), resolved.countByCourse())
    }

    // ---- what a claim must not do ---------------------------------------

    @Test
    fun `a day with no records claims nothing`() {
        assertTrue(resolve(claim("20991231", ClaimSessionType.FULL_DAY)).isEmpty())
    }

    @Test
    fun `no claims means no claimed sessions`() {
        assertTrue(resolveClaimedSessions(days, sessions, emptyList()).isEmpty())
    }

    @Test
    fun `the same period reached twice is counted once`() {
        // The forenoon of the 10th and period I of the 10th overlap. Arriving at the
        // same session from the calendar and from the subject list must not let it
        // count twice, which is what the set of (date, session) pairs guarantees.
        val resolved = resolve(
            claim(tenth, ClaimSessionType.FORENOON),
            claim(tenth, ClaimSessionType.CUSTOM, setOf(1)),
        )
        assertEquals(2, resolved.size)
        assertEquals(mapOf(11 to 2), resolved.countByCourse())
    }

    @Test
    fun `claims across two days add up`() {
        val resolved = resolve(
            claim(tenth, ClaimSessionType.FULL_DAY),
            claim(eleventh, ClaimSessionType.FULL_DAY),
        )
        assertEquals(4, resolved.size)
        assertEquals(mapOf(11 to 3, 22 to 1), resolved.countByCourse())
    }

    // ---- the projection --------------------------------------------------

    @Test
    fun `a claim raises attendance without inventing a class`() {
        // Course 11 at 6 of 10. Claiming both of its absences on the 10th re-marks
        // two classes that are already in the total, so the total must not move.
        val claimed = resolve(claim(tenth, ClaimSessionType.FULL_DAY)).countByCourse()[11] ?: 0
        val attended = 6
        val total = 10
        assertEquals(2, claimed)
        assertEquals(80.0, (attended + claimed) * 100.0 / total, 0.0001)
    }
}
