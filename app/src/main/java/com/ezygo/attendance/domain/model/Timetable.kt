package com.ezygo.attendance.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** One period on one day. [courseId] is null when nothing is scheduled. */
data class ScheduleSlot(
    val sessionId: Int,
    val sessionName: String,
    val courseId: Int?,
    val courseName: String?,
    val courseCode: String?,
    /**
     * Every staff member scheduled for this period. The portal emits one schedule
     * row per instructor, so a period taken by two teachers arrives as two rows and
     * is merged back into one slot here.
     */
    val instructors: List<String>,
    val deliveryMethod: String?,
    val classType: String?,
    /**
     * Set when a suspension covers this period. The portal generates the schedule
     * from the weekly timetable and then overlays suspensions on top, so a period
     * can be both scheduled and cancelled — the cancellation is what counts.
     */
    val cancelledReason: String? = null,
    /**
     * The short code the user gave this course, when they gave it one.
     *
     * Resolved at read time from the course-code table rather than stored on the
     * schedule row: the schedule is refetched and replaced whole on every refresh,
     * and a code copied onto it would be thrown away with it.
     */
    val shortCode: String? = null,
    /** True when this period was placed by the user on top of the published one. */
    val isOverride: Boolean = false,
) {
    val isFree: Boolean get() = courseId == null && courseName == null
    val isCancelled: Boolean get() = cancelledReason != null

    /** What a cramped grid cell shows: the code if there is one, else the name. */
    val displayName: String? get() = shortCode ?: courseName
}

/** Why a day has no classes, when that is known from the timetable definition. */
enum class DayStatus { CLASSES, WEEKEND, HOLIDAY, OUT_OF_TERM, NO_CLASSES }

data class TimetableDay(
    val date: LocalDate,
    val weekdayName: String,
    val status: DayStatus,
    /** Reason text for a holiday or suspension, when the payload supplied one. */
    val note: String?,
    /** Every period for the day, in order, including free ones. */
    val slots: List<ScheduleSlot>,
) {
    /** Periods with a class on the plan, cancelled ones included. */
    val scheduled: List<ScheduleSlot> get() = slots.filterNot { it.isFree }

    /** Periods actually going ahead. */
    val live: List<ScheduleSlot> get() = scheduled.filterNot { it.isCancelled }
    val isToday: Boolean get() = date == LocalDate.now()
}

/**
 * The days a week grid shows, always Monday first.
 *
 * Saturday and Sunday are dropped rather than drawn as two empty columns of "Weekend":
 * the institution does not teach on them, and two dead rows out of seven is a third of
 * the grid saying nothing. Lives here rather than in either grid so the app and the
 * home-screen widget cannot disagree about what a week is.
 *
 * Sorted by weekday rather than by date, which is the whole point. The source is the
 * *next* seven days, so a grid opened on a Wednesday arrived as Wed, Thu, Fri, Mon,
 * Tue — a week that starts in the middle and wraps, which is not how anyone reads a
 * timetable. Ordering by [DayOfWeek] puts Monday first whatever day it is read on.
 *
 * [distinctBy] guards a case seven consecutive days cannot actually produce: it takes
 * eight to see the same weekday twice. It is here so that a longer or duplicated
 * window degrades to one row per weekday instead of drawing two Mondays.
 */
fun List<TimetableDay>.gridDays(): List<TimetableDay> =
    filterNot { it.date.dayOfWeek == DayOfWeek.SATURDAY || it.date.dayOfWeek == DayOfWeek.SUNDAY }
        .distinctBy { it.date.dayOfWeek }
        .sortedBy { it.date.dayOfWeek.value }

/** The next seven days of the cohort's timetable. */
data class UpcomingTimetable(
    val subgroupName: String?,
    val days: List<TimetableDay>,
    val lastUpdatedEpochMs: Long?,
) {
    val hasSnapshot: Boolean get() = lastUpdatedEpochMs != null
}

/**
 * How many of the cohort were marked present and absent in one period.
 *
 * Class-wide, not the signed-in student's own: this answers "was anyone else here?",
 * which is the question a student asks when their own mark looks wrong. The student's
 * own status for the same period lives on [com.ezygo.attendance.domain.model.AttendanceRecord].
 */
data class SessionCount(val present: Int, val absent: Int) {
    val marked: Int get() = present + absent

    /** Nothing was recorded for this period, as opposed to everyone being absent. */
    val isUnmarked: Boolean get() = marked == 0
}
