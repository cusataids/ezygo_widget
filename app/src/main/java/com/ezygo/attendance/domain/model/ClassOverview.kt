package com.ezygo.attendance.domain.model

/**
 * One period on one day, as the cohort counts are filed.
 *
 * The date is the report's "YYYYMMDD" key rather than the monitoring endpoint's ISO
 * form, because this is joined against attendance records and they are the larger
 * side of the join. The one conversion happens where the counts are read.
 */
data class ClassSessionKey(val dateKey: String, val sessionId: Int)

/**
 * One time the class actually met, and how the room looked.
 *
 * [attended] is the signed-in student's own mark; [present] and [absent] are everyone.
 * Carrying both is the whole point — "I was away" and "so were eleven others" are
 * different facts, and only together do they say whether a day went wrong for the
 * student or for the class.
 */
data class ClassMeeting(
    val dateKey: String,
    val displayDate: String,
    val dayName: String,
    val sessionName: String,
    val present: Int,
    val absent: Int,
    val attended: Boolean,
) {
    /** How many people were marked at all. Zero means the register was never taken. */
    val marked: Int get() = present + absent

    val percent: Double? get() = if (marked == 0) null else present * 100.0 / marked
}

/**
 * How a class has been doing, meeting by meeting.
 *
 * Built only from meetings the portal has counts for. A class the student attended but
 * whose register the staff never submitted contributes nothing here, and saying so is
 * better than implying nobody turned up.
 */
data class ClassOverview(val meetings: List<ClassMeeting>) {

    /** Meetings with a register behind them, which are the only ones worth counting. */
    val recorded: List<ClassMeeting> get() = meetings.filter { it.marked > 0 }

    val hasData: Boolean get() = recorded.isNotEmpty()

    val totalPresent: Int get() = recorded.sumOf { it.present }

    val totalAbsent: Int get() = recorded.sumOf { it.absent }

    /**
     * The class's attendance across every meeting, pooled.
     *
     * Pooled rather than the mean of the per-day percentages, because every meeting
     * has roughly the same number of people in it — so the two barely differ, and the
     * pooled form is the one that matches the present and absent totals beside it.
     */
    val averagePercent: Double?
        get() {
            val marked = totalPresent + totalAbsent
            return if (marked == 0) null else totalPresent * 100.0 / marked
        }

    /** Roughly how many people are on the register, for reading the counts against. */
    val classSize: Int?
        get() = recorded.map { it.marked }.takeIf { it.isNotEmpty() }?.max()

    /** The meeting the fewest people came to, which is usually the interesting one. */
    val quietest: ClassMeeting? get() = recorded.minByOrNull { it.percent ?: 100.0 }
}
