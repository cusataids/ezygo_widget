package com.ezygo.attendance

import com.ezygo.attendance.domain.AttendanceCalculator
import com.ezygo.attendance.domain.AttendancePlanner
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.CourseStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shortest route back to the target.
 *
 * The count has to be the real minimum, not merely a workable answer, so the central
 * test is the tight one: the plan reaches the target, and removing any single class
 * from it no longer does.
 */
class AttendancePlannerTest {

    private val settings = AttendanceSettings(targetPercent = 75, watchBufferPoints = 0)

    private fun record(courseId: Int, positive: Boolean) = AttendanceRecord(
        dateKey = "20260310",
        displayDate = "10.3.26",
        dayName = "Tuesday",
        sessionId = 1,
        sessionName = "I",
        courseId = courseId,
        courseName = "Course $courseId",
        courseCode = "C$courseId",
        attendanceTypeId = if (positive) 1 else 2,
        attendanceName = if (positive) "Present" else "Absent",
        attendanceCode = if (positive) "P" else "AB",
        isPositive = positive,
    )

    /** A course with [attended] of [total] recorded, built through the real calculator. */
    private fun course(courseId: Int, attended: Int, total: Int): CourseStats {
        val records = List(attended) { record(courseId, true) } +
            List(total - attended) { record(courseId, false) }
        return CourseStats(
            courseId = courseId,
            courseName = "Course $courseId",
            courseCode = "C$courseId",
            stats = AttendanceCalculator.statsFor(records, settings),
        )
    }

    private fun averageOf(pairs: List<Pair<Int, Int>>): Double =
        pairs.map { (a, t) -> a * 100.0 / t }.average()

    // ---- the count -------------------------------------------------------

    @Test
    fun `an average already at the target needs nothing`() {
        val plan = AttendancePlanner.plan(
            listOf(course(1, 18, 20), course(2, 19, 20), course(3, 17, 20)),
            targetPercent = 75,
        )
        assertTrue(plan.alreadyMet)
        assertEquals(0, plan.classesNeeded)
        assertTrue(plan.perCourse.isEmpty())
    }

    @Test
    fun `one weak subject is the one that gets the classes`() {
        // 25 / 95 / 90 averages 70. The weak subject is both furthest down and
        // cheapest to move, so every class goes there.
        val plan = AttendancePlanner.plan(
            listOf(course(1, 5, 20), course(2, 19, 20), course(3, 18, 20)),
            targetPercent = 75,
        )
        assertEquals(5, plan.classesNeeded)
        assertEquals(1, plan.perCourse.size)
        assertEquals(1, plan.perCourse.first().courseId)
        assertEquals(5, plan.perCourse.first().classes)
    }

    @Test
    fun `evenly short subjects are evenly topped up`() {
        val plan = AttendancePlanner.plan(
            listOf(course(1, 14, 20), course(2, 14, 20), course(3, 14, 20)),
            targetPercent = 75,
        )
        assertEquals(12, plan.classesNeeded)
        assertEquals(listOf(4, 4, 4), plan.perCourse.map { it.classes })
    }

    @Test
    fun `the breakdown always sums to the count`() {
        val plan = AttendancePlanner.plan(
            listOf(course(1, 30, 50), course(2, 3, 5), course(3, 8, 12)),
            targetPercent = 75,
        )
        assertEquals(plan.classesNeeded, plan.perCourse.sumOf { it.classes })
    }

    // ---- minimality ------------------------------------------------------

    @Test
    fun `the plan reaches the target and one class fewer does not`() {
        val start = listOf(30 to 50, 3 to 5, 8 to 12)
        val plan = AttendancePlanner.plan(
            listOf(course(1, 30, 50), course(2, 3, 5), course(3, 8, 12)),
            targetPercent = 75,
        )
        val added = IntArray(start.size)
        plan.perCourse.forEach { need -> added[need.courseId - 1] = need.classes }

        val after = start.mapIndexed { i, (a, t) -> (a + added[i]) to (t + added[i]) }
        assertTrue("the plan must reach the target", averageOf(after) >= 75.0)

        // Drop one class from each subject that got any; none of those may still pass.
        after.indices.filter { added[it] > 0 }.forEach { i ->
            val lighter = after.toMutableList()
            val (a, t) = lighter[i]
            lighter[i] = (a - 1) to (t - 1)
            assertTrue(
                "one class fewer in course ${i + 1} should miss the target",
                averageOf(lighter) < 75.0,
            )
        }
    }

    // ---- exclusions and edges -------------------------------------------

    @Test
    fun `a course with no recorded classes is ignored entirely`() {
        val withZero = AttendancePlanner.plan(
            listOf(course(1, 10, 20), course(2, 0, 0), course(3, 16, 20)),
            targetPercent = 75,
        )
        val without = AttendancePlanner.plan(
            listOf(course(1, 10, 20), course(3, 16, 20)),
            targetPercent = 75,
        )
        assertEquals(without.classesNeeded, withZero.classesNeeded)
        assertEquals(2, withZero.courseCount)
        assertTrue(withZero.perCourse.none { it.courseId == 2 })
    }

    @Test
    fun `nothing recorded at all is not a plan`() {
        val plan = AttendancePlanner.plan(listOf(course(1, 0, 0)), targetPercent = 75)
        assertFalse(plan.reachable)
        assertFalse(plan.hasPlan)
        assertEquals(0, plan.courseCount)
    }

    @Test
    fun `an empty course list is not a plan`() {
        val plan = AttendancePlanner.plan(emptyList(), targetPercent = 75)
        assertFalse(plan.hasPlan)
        assertEquals(0, plan.courseCount)
    }

    @Test
    fun `the target is honoured rather than assumed to be 75`() {
        val courses = listOf(course(1, 14, 20), course(2, 14, 20), course(3, 14, 20))
        assertEquals(0, AttendancePlanner.plan(courses, targetPercent = 70).classesNeeded)
        assertTrue(AttendancePlanner.plan(courses, targetPercent = 90).classesNeeded > 12)
    }

    @Test
    fun `a single subject is driven to the target on its own`() {
        val plan = AttendancePlanner.plan(listOf(course(1, 10, 20)), targetPercent = 75)
        assertEquals(20, plan.classesNeeded)
        assertTrue(plan.reachable)
    }
}
