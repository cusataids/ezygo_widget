package com.ezygo.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Timetable cache. Like the attendance tables, everything is scoped by periodKey
 * so a semester switch can never blend two cohorts' schedules.
 */

@Entity(tableName = "timetable_meta")
data class TimetableMetaEntity(
    @PrimaryKey val periodKey: String,
    val subgroupId: Int?,
    val subgroupName: String?,
    /** Comma-separated weekday names the timetable excludes, e.g. "Saturday,Sunday". */
    val excludedWeekdays: String,
    /** Comma-separated working weekday names from the institution's workdays list. */
    val workdays: String,
    /** ISO dates bounding the term, when the timetable definition supplied them. */
    val startDate: String?,
    val uptoDate: String?,
    /**
     * The cohort's own first day, which is not always the timetable definition's.
     *
     * Kept because the monitoring report is asked for from day one, and day one is a
     * property of the subgroup rather than of whichever timetable was in force.
     */
    val subgroupStartDate: String?,
    /** Comma-separated "yyyy-MM-dd|reason" pairs for holidays and suspensions. */
    val nonWorkingDates: String,
    val lastUpdatedEpochMs: Long,
)

/** The institution's periods, from the sessions endpoint. */
@Entity(tableName = "timetable_sessions", primaryKeys = ["periodKey", "sessionId"])
data class TimetableSessionEntity(
    val periodKey: String,
    val sessionId: Int,
    val name: String,
    val viewOrder: Int,
)

@Entity(
    tableName = "schedules",
    primaryKeys = ["periodKey", "scheduleId"],
    indices = [Index("periodKey", "date")],
)
data class ScheduleEntity(
    val periodKey: String,
    val scheduleId: Long,
    /** ISO "yyyy-MM-dd"; sorts and range-compares correctly as text. */
    val date: String,
    val sessionId: Int,
    val courseId: Int?,
    val courseName: String?,
    val courseCode: String?,
    val instructor: String?,
    val deliveryMethod: String?,
    val classType: String?,
)

/**
 * How many of the cohort were present and absent in one period of one day.
 *
 * Cached beside attendance and refreshed with it: the figures only change when the
 * staff re-mark a register, and a widget or a dialog should never wait on a network
 * call to show a number the app already fetched.
 *
 * Keyed by ISO date rather than by the report's "YYYYMMDD" key, because this comes
 * from a different endpoint that speaks ISO and the two are converted at the one
 * place they meet.
 */
@Entity(tableName = "session_attendance_counts", primaryKeys = ["periodKey", "date", "sessionId"])
data class SessionCountEntity(
    val periodKey: String,
    /** ISO "yyyy-MM-dd". */
    val date: String,
    val sessionId: Int,
    val present: Int,
    val absent: Int,
)

/**
 * When the cohort report was last read, per period.
 *
 * A row of its own rather than a column on the counts, because the honest answer to
 * "when did we last ask" has to survive the report coming back empty — and a column
 * on rows that do not exist cannot. Without it a cohort with nothing recorded yet
 * would be re-fetched on every single open.
 *
 * Not on `timetable_meta` either: that row is rewritten wholesale by each timetable
 * refresh, which would quietly erase a timestamp that has nothing to do with it.
 */
@Entity(tableName = "class_counts_meta")
data class ClassCountsMetaEntity(
    @PrimaryKey val periodKey: String,
    val fetchedEpochMs: Long,
)
