package com.ezygo.attendance.domain.model

/** Where a course sits relative to the user's configured target. */
enum class RiskBand { SAFE, WATCH, AT_RISK, NO_DATA }

/** User-tunable thresholds. Institution policy is never hard-coded as fact. */
data class AttendanceSettings(
    val targetPercent: Int = DEFAULT_TARGET,
    val watchBufferPoints: Int = DEFAULT_WATCH_BUFFER,
) {
    companion object {
        const val DEFAULT_TARGET = 75

        /**
         * No warning band by default: a subject is either at the target or it is not.
         * The slider on the profile screen still opens one for anyone who wants a
         * margin, and a value already chosen is untouched by this.
         */
        const val DEFAULT_WATCH_BUFFER = 0
    }
}

/**
 * Counts and derived figures over a set of records.
 *
 * [percentage] is null — not zero — when nothing has been recorded, so the UI can
 * say "No attendance data" instead of implying the student attended nothing.
 */
data class AttendanceStats(
    val total: Int,
    val positive: Int,
    val present: Int,
    val absent: Int,
    val dutyLeave: Int,
    /** Counts for any attendance code, including ones beyond P/AB/DL. */
    val byCode: Map<String, Int>,
    val percentage: Double?,
    val band: RiskBand,
    /** Consecutive classes to attend to reach the target; null when unreachable. */
    val classesNeededForTarget: Int?,
    /** Classes that can still be missed while staying at/above target; null when unbounded. */
    val classesMissableAtTarget: Int?,
) {
    val hasData: Boolean get() = total > 0

    companion object {
        val EMPTY = AttendanceStats(
            total = 0,
            positive = 0,
            present = 0,
            absent = 0,
            dutyLeave = 0,
            byCode = emptyMap(),
            percentage = null,
            band = RiskBand.NO_DATA,
            classesNeededForTarget = null,
            classesMissableAtTarget = null,
        )
    }
}

/** Per-course stats plus the course identity. */
data class CourseStats(
    val courseId: Int,
    val courseName: String,
    val courseCode: String?,
    val stats: AttendanceStats,
)

/**
 * Arithmetic projection — not a statistical forecast. Every figure here is a
 * direct consequence of the current counts.
 */
data class Projection(
    val n: Int,
    val ifAttendAllPercent: Double,
    val ifMissAllPercent: Double,
)
