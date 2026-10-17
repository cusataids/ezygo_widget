package com.ezygo.attendance.domain.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Where an event came from; no two are ever presented as the same thing. */
enum class EventSource {
    /** The batch's own calendar feed. Replaced on every sync. */
    BATCH,

    /**
     * The institution's academic calendar feed. Deliberately kept off the home
     * screen: it is reference material rather than the handful of dates a student
     * needs at a glance.
     */
    ACADEMIC,

    /** Added by the user on this device. Exists nowhere else, and is never synced. */
    LOCAL,
}

/** One academic or personal event, ready to render. */
data class CalendarEvent(
    val id: String,
    val source: EventSource,
    val title: String,
    val description: String?,
    val location: String?,
    val start: Instant,
    val end: Instant?,
    val isAllDay: Boolean,
) {
    fun startDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
        start.atZone(zone).toLocalDate()

    /** Null for an all-day event, which has no meaningful clock time. */
    fun startTime(zone: ZoneId = ZoneId.systemDefault()): LocalTime? =
        if (isAllDay) null else start.atZone(zone).toLocalTime()

    /**
     * The last day the event covers.
     *
     * An all-day DTEND is exclusive per RFC 5545 — a one-day event on the 18th ends
     * on the 19th — so the final day is a step back from it. Without this a
     * single-day exam would light up two squares on the calendar.
     */
    fun endDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val endInstant = end ?: return startDate(zone)
        val date = endInstant.atZone(zone).toLocalDate()
        val exclusive = isAllDay && endInstant.atZone(zone).toLocalTime() == LocalTime.MIDNIGHT
        val last = if (exclusive) date.minusDays(1) else date
        return maxOf(last, startDate(zone))
    }
}
