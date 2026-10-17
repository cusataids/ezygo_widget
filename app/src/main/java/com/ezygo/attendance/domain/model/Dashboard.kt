package com.ezygo.attendance.domain.model

import com.ezygo.attendance.data.repo.EnrolledCourse
import com.ezygo.attendance.data.repo.SessionInfo
import java.time.LocalDate

/** A day's worth of recorded classes, for the timetable timeline. */
data class AttendanceDay(
    val dateKey: String,
    val displayDate: String,
    val dayName: String,
    val records: List<AttendanceRecord>,
) {
    /**
     * The day itself, parsed from the report's "YYYYMMDD" key. Null when the key is
     * not in that shape, which is why the calendar view treats it as optional rather
     * than assuming every record can be placed on a grid.
     */
    val date: LocalDate?
        get() {
            if (dateKey.length != 8) return null
            return runCatching {
                LocalDate.of(
                    dateKey.substring(0, 4).toInt(),
                    dateKey.substring(4, 6).toInt(),
                    dateKey.substring(6, 8).toInt(),
                )
            }.getOrNull()
        }
}

/**
 * Everything the screens render, derived once from the cached snapshot so Home,
 * Timetable and Subject can never disagree with each other.
 */
data class Dashboard(
    val period: Period,
    val settings: AttendanceSettings,
    val overall: AttendanceStats,
    /**
     * Unweighted mean of the individual subject percentages, and its risk band.
     *
     * This is deliberately not [overall]'s percentage: that pools every class
     * together, so a subject with many classes dominates it. The average treats each
     * subject equally, which is what a student comparing subjects expects to see.
     * Subjects with no recorded classes are excluded rather than counted as zero.
     */
    val averagePercent: Double?,
    val averageBand: RiskBand,
    val courses: List<CourseStats>,
    /**
     * The institution's periods, carrying the portal's forenoon/afternoon mark.
     * Needed wherever a date has to be turned into the sessions that fall on it.
     */
    val sessions: List<SessionInfo>,
    /**
     * How many of the cohort were present and absent in each period, from the
     * monitoring report. Empty until that report has been fetched for this period.
     */
    val sessionCounts: Map<ClassSessionKey, SessionCount>,
    /** Enrolled courses that have no recorded classes at all. */
    val coursesWithoutRecords: List<EnrolledCourse>,
    val recent: List<AttendanceRecord>,
    val days: List<AttendanceDay>,
    val lastUpdatedEpochMs: Long?,
    /** False until a refresh has ever succeeded for this period. */
    val hasSnapshot: Boolean,
) {
    val subjectsAtRisk: List<CourseStats>
        get() = courses.filter { it.stats.band == RiskBand.AT_RISK }

    val isEmpty: Boolean get() = overall.total == 0
}
