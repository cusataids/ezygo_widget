package com.ezygo.attendance

import com.ezygo.attendance.domain.AttendanceCalculator
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.RiskBand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AttendanceCalculatorTest {

    private val settings = AttendanceSettings(targetPercent = 75, watchBufferPoints = 5)

    private fun record(code: String, positive: Boolean, courseId: Int = 1) = AttendanceRecord(
        dateKey = "20260819",
        displayDate = "19.8.26",
        dayName = "Wednesday",
        sessionId = 1,
        sessionName = "I",
        courseId = courseId,
        courseName = "Course $courseId",
        courseCode = "C$courseId",
        attendanceTypeId = 1,
        attendanceName = code,
        attendanceCode = code,
        isPositive = positive,
    )

    private fun records(present: Int, absent: Int, dutyLeave: Int = 0, courseId: Int = 1) =
        List(present) { record("P", true, courseId) } +
            List(absent) { record("AB", false, courseId) } +
            List(dutyLeave) { record("DL", true, courseId) }

    // ---- counting --------------------------------------------------------

    @Test
    fun `duty leave counts toward the percentage but stays separately visible`() {
        val stats = AttendanceCalculator.statsFor(records(present = 30, absent = 10, dutyLeave = 6), settings)
        assertEquals(46, stats.total)
        assertEquals(36, stats.positive)
        assertEquals(30, stats.present)
        assertEquals(6, stats.dutyLeave)
        assertEquals(10, stats.absent)
        assertEquals(36.0 * 100 / 46, stats.percentage!!, 0.0001)
    }

    @Test
    fun `no recorded classes yields null percentage and NO_DATA rather than zero`() {
        val stats = AttendanceCalculator.statsFor(emptyList(), settings)
        assertNull(stats.percentage)
        assertEquals(RiskBand.NO_DATA, stats.band)
        assertEquals(0, stats.total)
        assertEquals(false, stats.hasData)
    }

    // ---- risk bands ------------------------------------------------------

    @Test
    fun `bands respect the configurable target and buffer`() {
        assertEquals(RiskBand.AT_RISK, AttendanceCalculator.bandFor(74.9, settings))
        assertEquals(RiskBand.WATCH, AttendanceCalculator.bandFor(75.0, settings))
        assertEquals(RiskBand.WATCH, AttendanceCalculator.bandFor(79.9, settings))
        assertEquals(RiskBand.SAFE, AttendanceCalculator.bandFor(80.0, settings))
        assertEquals(RiskBand.NO_DATA, AttendanceCalculator.bandFor(null, settings))
    }

    @Test
    fun `a different target moves the bands`() {
        val strict = AttendanceSettings(targetPercent = 85, watchBufferPoints = 5)
        assertEquals(RiskBand.AT_RISK, AttendanceCalculator.bandFor(80.0, strict))
        assertEquals(RiskBand.SAFE, AttendanceCalculator.bandFor(90.0, strict))
    }

    // ---- classes needed --------------------------------------------------

    @Test
    fun `classes needed lands exactly on the target`() {
        // 36/50 = 72%. Attending 6 more gives 42/56 = exactly 75%.
        assertEquals(6, AttendanceCalculator.classesNeededForTarget(36, 50, 75))
    }

    @Test
    fun `classes needed is zero when already at or above target`() {
        assertEquals(0, AttendanceCalculator.classesNeededForTarget(39, 50, 75))
        assertEquals(0, AttendanceCalculator.classesNeededForTarget(75, 100, 75))
    }

    @Test
    fun `classes needed is null when the target can never be reached`() {
        // A single past absence makes 100% unreachable no matter how many are attended.
        assertNull(AttendanceCalculator.classesNeededForTarget(49, 50, 100))
        assertEquals(0, AttendanceCalculator.classesNeededForTarget(50, 50, 100))
    }

    @Test
    fun `classes needed is null when nothing has been recorded`() {
        assertNull(AttendanceCalculator.classesNeededForTarget(0, 0, 75))
    }

    @Test
    fun `classes needed rounds up rather than reporting a fractional class`() {
        // 1/2 = 50%, target 75 -> needs 2: (1+2)/(2+2) = 75%. One would give 66.7%.
        assertEquals(2, AttendanceCalculator.classesNeededForTarget(1, 2, 75))
    }

    // ---- classes missable ------------------------------------------------

    @Test
    fun `classes missable keeps attendance at or above target`() {
        // 39/50 = 78%. Missing 2 gives 39/52 = exactly 75%; a third would drop below.
        assertEquals(2, AttendanceCalculator.classesMissableAtTarget(39, 50, 75))
    }

    @Test
    fun `classes missable is zero when already below target`() {
        assertEquals(0, AttendanceCalculator.classesMissableAtTarget(36, 50, 75))
    }

    @Test
    fun `classes missable is null when nothing has been recorded`() {
        assertNull(AttendanceCalculator.classesMissableAtTarget(0, 0, 75))
    }

    @Test
    fun `needed and missable are never both positive`() {
        for (total in 1..60) {
            for (positive in 0..total) {
                val needed = AttendanceCalculator.classesNeededForTarget(positive, total, 75) ?: 0
                val missable = AttendanceCalculator.classesMissableAtTarget(positive, total, 75) ?: 0
                assert(needed == 0 || missable == 0) {
                    "positive=$positive total=$total needed=$needed missable=$missable"
                }
            }
        }
    }

    // ---- projection ------------------------------------------------------

    @Test
    fun `projection is plain arithmetic on the current counts`() {
        val projection = AttendanceCalculator.project(positive = 36, total = 50, n = 10)
        assertEquals(46.0 * 100 / 60, projection.ifAttendAllPercent, 0.0001)
        assertEquals(36.0 * 100 / 60, projection.ifMissAllPercent, 0.0001)
    }

    // ---- per course ------------------------------------------------------

    @Test
    fun `course stats group by course and sort worst first`() {
        val all = records(present = 9, absent = 1, courseId = 1) + // 90%
            records(present = 5, absent = 5, courseId = 2) // 50%
        val stats = AttendanceCalculator.courseStats(all, settings)
        assertEquals(listOf(2, 1), stats.map { it.courseId })
        assertEquals(50.0, stats.first().stats.percentage!!, 0.0001)
        assertEquals(RiskBand.AT_RISK, stats.first().stats.band)
        assertEquals(RiskBand.SAFE, stats.last().stats.band)
    }
}
