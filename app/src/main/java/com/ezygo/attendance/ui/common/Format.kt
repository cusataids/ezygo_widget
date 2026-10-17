package com.ezygo.attendance.ui.common

import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.AttendanceStats
import com.ezygo.attendance.domain.model.RiskBand
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Percentage rendered for display, or a dash when nothing has been recorded. */
fun AttendanceStats.percentLabel(): String =
    percentage?.let { "${it.roundToInt()}%" } ?: "--"

/**
 * A percentage to one decimal, or a dash when there is nothing to show.
 *
 * One decimal rather than whole points wherever a figure is read against the target:
 * rounding hides the difference between 74.6% and 75.4%, which is exactly the
 * difference the target is about. Lives here because three screens had grown their
 * own copy of the same format string, one of which also shadowed [percentLabel].
 */
fun Double?.asPercentLabel1dp(): String =
    this?.let { String.format(Locale.getDefault(), "%.1f%%", it) } ?: "--"

/**
 * A percentage to two decimals.
 *
 * Two rather than the one [asPercentLabel1dp] uses, because this is the pooled figure
 * over every class of a term: by the hundredth class a single attendance moves it by
 * about a hundredth of a point, and a figure that never visibly moves looks stuck.
 */
fun Double?.asPercentLabel2dp(): String =
    this?.let { String.format(Locale.getDefault(), "%.2f%%", it) } ?: "--"

fun RiskBand.label(): String = when (this) {
    RiskBand.SAFE -> "Safe"
    RiskBand.WATCH -> "Watch"
    RiskBand.AT_RISK -> "Shortage"
    RiskBand.NO_DATA -> "No data"
}

/**
 * The one-line answer the student actually needs: how far from the target, in
 * plain language. No formulas, no ratios.
 */
fun AttendanceStats.shortageMessage(targetPercent: Int): String = when {
    !hasData -> "No attendance data yet"
    band == RiskBand.AT_RISK -> when (val needed = classesNeededForTarget) {
        null -> "Cannot reach $targetPercent% this semester"
        0 -> "At $targetPercent%"
        1 -> "Attend 1 more class to reach $targetPercent%"
        else -> "Attend $needed more classes to reach $targetPercent%"
    }
    else -> when (val missable = classesMissableAtTarget) {
        null -> "Above $targetPercent%"
        0 -> "Cannot miss any class and stay above $targetPercent%"
        1 -> "Can miss 1 class and stay above $targetPercent%"
        else -> "Can miss $missable classes and stay above $targetPercent%"
    }
}

/** "Last updated 10 min ago" style relative time. */
fun relativeTime(epochMs: Long?, nowMs: Long = System.currentTimeMillis()): String =
    if (epochMs == null) "Never updated" else "Last updated ${timeAgo(epochMs, nowMs)}"

/** The age of a moment on its own: "just now", "10 min ago", "3 days ago". */
fun timeAgo(epochMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val deltaSeconds = ((nowMs - epochMs) / 1000).coerceAtLeast(0)
    return when {
        deltaSeconds < 60 -> "just now"
        deltaSeconds < 3600 -> "${deltaSeconds / 60} min ago"
        deltaSeconds < 86_400 -> {
            val hours = deltaSeconds / 3600
            "$hours ${if (hours == 1L) "hour" else "hours"} ago"
        }
        else -> {
            val days = deltaSeconds / 86_400
            "$days ${if (days == 1L) "day" else "days"} ago"
        }
    }
}

/**
 * When a moment happened, phrased for how far away it is: recent things read
 * better as an age, but "217 days ago" tells nobody anything, so past a week it
 * becomes a date.
 */
fun eventTime(epochMs: Long?, nowMs: Long = System.currentTimeMillis()): String? {
    if (epochMs == null) return null
    if (nowMs - epochMs < ABSOLUTE_DATE_THRESHOLD_MS) return timeAgo(epochMs, nowMs)
    val date = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).toLocalDate()
    val monthName = MONTHS.getOrNull(date.monthValue - 1) ?: return timeAgo(epochMs, nowMs)
    return "${date.dayOfMonth} $monthName ${date.year}"
}

private const val ABSOLUTE_DATE_THRESHOLD_MS = 7L * 24 * 60 * 60 * 1000

/**
 * The date of one attendance record, and the weekday that actually falls on it.
 *
 * Both read from [AttendanceRecord.dateKey], which the API documents as the source of
 * truth, rather than from the two display fields beside it. `displayDate` arrives as
 * "19.8.26", which is ambiguous about which half is the day, and `dayName` is optional
 * in the payload — it is `.orEmpty()` at the point it is parsed, so a record whose
 * feed omitted it carried a blank day. Deriving the weekday from the date it is shown
 * next to makes the pair consistent by construction.
 */
fun AttendanceRecord.recordDate(): String = prettyDate(dateKey, displayDate)

fun AttendanceRecord.recordWeekday(): String = weekdayName(dateKey, fallback = dayName)

/**
 * "19 Aug 2026" from a "YYYYMMDD" key, or [fallback] when the key is not one.
 *
 * The same derivation [recordDate] uses, exposed because the class lists show dates
 * that did not come from an [AttendanceRecord] and two spellings of the same day on
 * two screens is the kind of difference a reader notices and cannot explain.
 */
fun prettyDate(dateKey: String, fallback: String): String =
    parseDateKey(dateKey)?.let { "${it.dayOfMonth} ${MONTHS_SHORT[it.monthValue - 1]} ${it.year}" }
        ?: fallback

/**
 * The weekday that actually falls on a "YYYYMMDD" key.
 *
 * Derived rather than read from whatever day name the payload carried: the field is
 * optional upstream and arrives blank often enough that trusting it would leave the
 * occasional date with nothing beside it.
 *
 * [short] gives "Wed" rather than "Wednesday", which is what a date in a list wants —
 * the full name is for places where the day is the subject rather than a qualifier.
 */
fun weekdayName(dateKey: String, fallback: String = "", short: Boolean = false): String =
    parseDateKey(dateKey)?.dayOfWeek
        ?.getDisplayName(
            if (short) TextStyle.SHORT else TextStyle.FULL,
            Locale.getDefault(),
        )
        ?: fallback

/**
 * A date with its weekday in front: "Wed, 19 Aug 2026".
 *
 * Weekday first because that is the half a student scans for — "which Tuesday" is a
 * rarer question than "was that a Tuesday" — and because leading with it lines the
 * list up on the short word rather than on a day number that changes width.
 */
fun dateWithWeekday(dateKey: String, fallback: String, dayName: String = ""): String {
    val date = prettyDate(dateKey, fallback)
    val weekday = weekdayName(dateKey, fallback = dayName, short = true)
    return if (weekday.isBlank()) date else "$weekday, $date"
}

/** "YYYYMMDD" to a date, or null when the key is not that shape or not a real day. */
private fun parseDateKey(dateKey: String): LocalDate? {
    if (dateKey.length != 8) return null
    val year = dateKey.substring(0, 4).toIntOrNull() ?: return null
    val month = dateKey.substring(4, 6).toIntOrNull() ?: return null
    val day = dateKey.substring(6, 8).toIntOrNull() ?: return null
    // 31 February is a well-formed key and not a date; runCatching is what separates
    // the two without the caller having to know the length of every month.
    return runCatching { LocalDate.of(year, month, day) }.getOrNull()
}

private val MONTHS_SHORT = listOf(
    "Jan", "Feb", "Mar", "Apr", "May", "Jun",
    "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
)

/** "19 August" from a "YYYYMMDD" key, falling back to whatever the API gave us. */
fun formatDayHeader(dateKey: String, fallback: String): String {
    if (dateKey.length != 8 || dateKey.toIntOrNull() == null) return fallback
    val month = dateKey.substring(4, 6).toIntOrNull() ?: return fallback
    val day = dateKey.substring(6, 8).toIntOrNull() ?: return fallback
    val monthName = MONTHS.getOrNull(month - 1) ?: return fallback
    return "$day $monthName"
}

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

/**
 * When something is due, as a date and, if it has one, a clock time.
 *
 * Absolute rather than relative for the same reason as everywhere else a deadline is
 * shown: "in 3 hours" stops being true the moment it is drawn, and a deadline is read
 * to find out when, not how soon.
 */
fun dueLabel(epochMs: Long, isAllDay: Boolean, nowMs: Long = System.currentTimeMillis()): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMs).atZone(zone)
    val day = moment.toLocalDate()
    val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
    val date = when (day) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        today.minusDays(1) -> "Yesterday"
        else -> "${day.dayOfMonth} " +
            day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    }
    if (isAllDay) return date
    return date + " · " + "%02d:%02d".format(moment.hour, moment.minute)
}
