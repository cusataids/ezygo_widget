package com.ezygo.attendance.domain

import com.ezygo.attendance.domain.model.AttendanceDay

/**
 * One day, and how much of it was missed.
 *
 * [recorded] is every session the portal has a mark for on that day, so [absences]
 * can be read against it — "4 absences" means something different on a day with four
 * classes than on a day with seven.
 */
data class AbsenceDay(
    val dateKey: String,
    val displayDate: String,
    val dayName: String,
    val absences: Int,
    val recorded: Int,
) {
    /** Nothing on this day was attended. The days a certificate would have to cover. */
    val wholeDay: Boolean get() = recorded > 0 && absences == recorded
}

/**
 * The days with the most absences on them.
 *
 * Absence is `!isPositive`, the same test the rest of the app uses, which means duty
 * leave counts as attended here. That is the portal's own view — duty leave is not a
 * mark against anyone — and a day spent representing the college should not be at the
 * top of a list of days you went missing.
 */
object AbsenceDays {

    /** How many rows the section shows. */
    const val TOP_N = 10

    /**
     * Ranked worst first, with the most recent day winning ties.
     *
     * Days with nothing missed are dropped rather than ranked last: a list of the
     * worst days padded out with days that were fine says nothing, and a student with
     * three absences all term should see three rows, not ten.
     *
     * [wholeDaysOnly] is the filter. It keeps only days with no attended session at
     * all — which is a stronger condition than "lots of absences", and why it can
     * reorder as well as shorten the list.
     */
    fun rank(
        days: List<AttendanceDay>,
        wholeDaysOnly: Boolean = false,
        limit: Int = TOP_N,
    ): List<AbsenceDay> = days.asSequence()
        .map { day ->
            AbsenceDay(
                dateKey = day.dateKey,
                displayDate = day.displayDate,
                dayName = day.dayName,
                absences = day.records.count { !it.isPositive },
                recorded = day.records.size,
            )
        }
        .filter { it.absences > 0 }
        .filter { !wholeDaysOnly || it.wholeDay }
        // Date descending as the tie-break: among equally bad days the recent one is
        // the one still worth doing something about.
        .sortedWith(compareByDescending<AbsenceDay> { it.absences }.thenByDescending { it.dateKey })
        .take(limit)
        .toList()
}
