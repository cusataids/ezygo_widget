package com.ezygo.attendance.domain

import com.ezygo.attendance.domain.model.AttendanceDay

/**
 * One day, and how much of it was attended.
 *
 * The mirror of [AbsenceDay]. Kept as its own type rather than one type with a flag,
 * because the two are ranked by different things: absences by how many, attendance by
 * what fraction. A shared type would have to carry both orderings and explain which
 * one it was using.
 */
data class AttendedDay(
    val dateKey: String,
    val displayDate: String,
    val dayName: String,
    val attended: Int,
    val recorded: Int,
) {
    /** Nothing on this day was missed. */
    val wholeDay: Boolean get() = recorded > 0 && attended == recorded

    val percent: Double get() = if (recorded == 0) 0.0 else attended * 100.0 / recorded
}

/**
 * The days with the best attendance on them.
 *
 * Attendance is `isPositive`, the same test used everywhere else, so duty leave counts
 * as attended — the portal does not hold it against anyone and neither does this.
 */
object AttendedDays {

    /**
     * Ranked best first: by the fraction of the day attended, then by how long the
     * day was, then by date.
     *
     * The fraction leads because a day is good or bad in proportion to itself. The
     * class count breaks ties because six out of six is a better day than two out of
     * two — both are perfect, but one of them was a day's work. Date settles the rest,
     * most recent first, so an unbroken run reads newest to oldest.
     */
    fun rank(
        days: List<AttendanceDay>,
        limit: Int = Int.MAX_VALUE,
    ): List<AttendedDay> = days.asSequence()
        .map { day ->
            AttendedDay(
                dateKey = day.dateKey,
                displayDate = day.displayDate,
                dayName = day.dayName,
                attended = day.records.count { it.isPositive },
                recorded = day.records.size,
            )
        }
        // A day with nothing attended belongs on the other list, not at the bottom
        // of this one.
        .filter { it.attended > 0 }
        .sortedWith(
            compareByDescending<AttendedDay> { it.percent }
                .thenByDescending { it.recorded }
                .thenByDescending { it.dateKey },
        )
        .take(limit)
        .toList()
}
