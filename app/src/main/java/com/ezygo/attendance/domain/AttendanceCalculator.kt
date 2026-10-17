package com.ezygo.attendance.domain

import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.AttendanceStats
import com.ezygo.attendance.domain.model.CourseStats
import com.ezygo.attendance.domain.model.Projection
import com.ezygo.attendance.domain.model.RiskBand

/**
 * Every attendance figure in the app comes from here. Pure functions, no Android
 * dependencies, so the arithmetic is unit-testable in isolation.
 *
 * Positivity always comes from the attendance type's positive_report_value, which
 * normalization has already resolved into [AttendanceRecord.isPositive]. Present
 * and Duty Leave are both positive; Absent is not.
 *
 * All target arithmetic is done in integer percentage points rather than floating
 * point fractions, so boundary cases land exactly where they should.
 */
object AttendanceCalculator {

    const val CODE_PRESENT = "P"
    const val CODE_DUTY_LEAVE = "DL"

    fun statsFor(
        records: List<AttendanceRecord>,
        settings: AttendanceSettings,
    ): AttendanceStats {
        if (records.isEmpty()) return AttendanceStats.EMPTY

        val total = records.size
        val positive = records.count { it.isPositive }
        val byCode = records.groupingBy { it.attendanceCode }.eachCount()

        // Absent is "everything not positive" rather than a literal AB match, so an
        // unrecognised negative type is still counted against the student honestly.
        val absent = total - positive
        val dutyLeave = records.count { it.attendanceCode == CODE_DUTY_LEAVE }
        val present = records.count { it.attendanceCode == CODE_PRESENT }

        val percentage = positive.toDouble() * 100.0 / total.toDouble()

        return AttendanceStats(
            total = total,
            positive = positive,
            present = present,
            absent = absent,
            dutyLeave = dutyLeave,
            byCode = byCode,
            percentage = percentage,
            band = bandFor(percentage, settings),
            classesNeededForTarget = classesNeededForTarget(positive, total, settings.targetPercent),
            classesMissableAtTarget = classesMissableAtTarget(positive, total, settings.targetPercent),
        )
    }

    fun courseStats(
        records: List<AttendanceRecord>,
        settings: AttendanceSettings,
    ): List<CourseStats> =
        records.groupBy { it.courseId }
            .map { (courseId, courseRecords) ->
                val first = courseRecords.first()
                CourseStats(
                    courseId = courseId,
                    courseName = first.courseName,
                    courseCode = first.courseCode,
                    stats = statsFor(courseRecords, settings),
                )
            }
            // Worst attendance first: the student needs to see problems immediately.
            .sortedWith(
                compareBy<CourseStats> { it.stats.percentage ?: Double.MAX_VALUE }
                    .thenBy { it.courseName },
            )

    fun bandFor(percentage: Double?, settings: AttendanceSettings): RiskBand {
        if (percentage == null) return RiskBand.NO_DATA
        val target = settings.targetPercent.toDouble()
        val watchCeiling = target + settings.watchBufferPoints.toDouble()
        return when {
            percentage < target -> RiskBand.AT_RISK
            percentage < watchCeiling -> RiskBand.WATCH
            else -> RiskBand.SAFE
        }
    }

    /**
     * Smallest number of consecutive future classes the student must attend for
     * attendance to reach [targetPercent].
     *
     *   (positive + n) / (total + n) >= target/100
     *   => n * (100 - target) >= target * total - 100 * positive
     *
     * Returns 0 when the target is already met, and null when it can never be
     * reached by attending (only possible at a 100% target with an existing absence).
     */
    fun classesNeededForTarget(positive: Int, total: Int, targetPercent: Int): Int? {
        if (total == 0) return null
        if (targetPercent <= 0) return 0
        val deficit = targetPercent.toLong() * total - 100L * positive
        if (deficit <= 0L) return 0
        if (targetPercent >= 100) return null // attending more never dilutes a past absence
        val denominator = (100 - targetPercent).toLong()
        return ceilDiv(deficit, denominator).toInt()
    }

    /**
     * Largest number of future classes that can be missed while attendance stays
     * at or above [targetPercent].
     *
     *   positive / (total + n) >= target/100
     *   => n <= (100 * positive - target * total) / target
     *
     * Returns null when the target is 0 (unbounded) and 0 when already below target.
     */
    fun classesMissableAtTarget(positive: Int, total: Int, targetPercent: Int): Int? {
        if (total == 0) return null
        if (targetPercent <= 0) return null // no target means no limit
        val headroom = 100L * positive - targetPercent.toLong() * total
        if (headroom < 0L) return 0
        return (headroom / targetPercent.toLong()).toInt()
    }

    /** Attendance if the next [n] classes are all attended / all missed. */
    fun project(positive: Int, total: Int, n: Int): Projection {
        val denominator = (total + n).coerceAtLeast(1)
        return Projection(
            n = n,
            ifAttendAllPercent = (positive + n).toDouble() * 100.0 / denominator.toDouble(),
            ifMissAllPercent = positive.toDouble() * 100.0 / denominator.toDouble(),
        )
    }

    private fun ceilDiv(numerator: Long, denominator: Long): Long =
        (numerator + denominator - 1) / denominator
}
