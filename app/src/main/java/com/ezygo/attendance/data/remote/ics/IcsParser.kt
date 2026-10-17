package com.ezygo.attendance.data.remote.ics

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One VEVENT, reduced to the fields this app shows. */
data class IcsEvent(
    val uid: String,
    val title: String,
    val description: String?,
    val location: String?,
    val startEpochMs: Long,
    val endEpochMs: Long?,
    val isAllDay: Boolean,
)

/**
 * A deliberately small iCalendar reader: enough of RFC 5545 to read a published
 * Google Calendar feed, and no more.
 *
 * What it handles: line unfolding, property parameters, escaped text, and the
 * three DTSTART forms Google emits (floating local, UTC, and VALUE=DATE).
 *
 * What it does not: **RRULE is ignored**, so a recurring event appears once, on
 * its first occurrence. Academic feeds are dominated by one-off exams and
 * deadlines, so this is a real but narrow limitation — expanding recurrence rules
 * is a much larger piece of work than the rest of this parser put together.
 * VTIMEZONE blocks are skipped in favour of the platform's own tz database.
 */
object IcsParser {

    fun parse(ics: String, zone: ZoneId = ZoneId.systemDefault()): List<IcsEvent> {
        val events = mutableListOf<IcsEvent>()
        var current: MutableMap<String, Line>? = null

        for (line in unfold(ics)) {
            val parsed = parseLine(line) ?: continue
            when {
                parsed.name == "BEGIN" && parsed.value == "VEVENT" -> current = mutableMapOf()
                parsed.name == "END" && parsed.value == "VEVENT" -> {
                    current?.toEvent(zone)?.let(events::add)
                    current = null
                }
                // Keep the first occurrence of a property; a malformed feed that
                // repeats one should not have the later copy silently win.
                current != null -> current.putIfAbsent(parsed.name, parsed)
            }
        }
        return events.sortedBy { it.startEpochMs }
    }

    // ---- structure -------------------------------------------------------

    private data class Line(
        val name: String,
        val params: Map<String, String>,
        val value: String,
    )

    /**
     * Content lines, with RFC 5545 folding undone.
     *
     * A line beginning with a space or tab is a continuation of the previous one;
     * the leading whitespace is part of the fold, not of the value.
     */
    private fun unfold(ics: String): List<String> {
        val out = mutableListOf<StringBuilder>()
        for (raw in ics.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if (raw.isEmpty()) continue
            val isContinuation = raw[0] == ' ' || raw[0] == '\t'
            if (isContinuation && out.isNotEmpty()) {
                out.last().append(raw.substring(1))
            } else {
                out.add(StringBuilder(raw))
            }
        }
        return out.map { it.toString() }
    }

    /**
     * Splits `NAME;PARAM=value:content` at the first colon that is not inside a
     * quoted parameter value — a TZID may legitimately contain one.
     */
    private fun parseLine(line: String): Line? {
        var quoted = false
        var colon = -1
        var index = 0
        while (index < line.length) {
            val character = line[index]
            if (character == '"') {
                quoted = !quoted
            } else if (character == ':' && !quoted) {
                colon = index
                break
            }
            index++
        }
        if (colon <= 0) return null

        val head = line.substring(0, colon)
        val value = line.substring(colon + 1)
        val parts = splitParams(head)
        val name = parts.first().uppercase()

        val params = parts.drop(1).mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) return@mapNotNull null
            val key = part.substring(0, equals).uppercase()
            val raw = part.substring(equals + 1).trim('"')
            key to raw
        }.toMap()

        return Line(name, params, value)
    }

    /** Parameter separator is `;`, but not one inside a quoted value. */
    private fun splitParams(head: String): List<String> {
        val parts = mutableListOf<String>()
        val builder = StringBuilder()
        var quoted = false
        for (character in head) {
            when {
                character == '"' -> {
                    quoted = !quoted
                    builder.append(character)
                }
                character == ';' && !quoted -> {
                    parts.add(builder.toString())
                    builder.clear()
                }
                else -> builder.append(character)
            }
        }
        parts.add(builder.toString())
        return parts
    }

    // ---- events ----------------------------------------------------------

    private fun Map<String, Line>.toEvent(zone: ZoneId): IcsEvent? {
        // A cancelled event is still in the feed; showing it would tell a student to
        // turn up to something that is not happening.
        if (this["STATUS"]?.value.equals("CANCELLED", ignoreCase = true)) return null

        val start = this["DTSTART"] ?: return null
        val title = this["SUMMARY"]?.value?.let(::unescape)?.takeIf { it.isNotBlank() }
            ?: return null
        val startMoment = start.toEpochMs(zone) ?: return null

        return IcsEvent(
            // UID is required by the spec, but a feed missing it should still show:
            // the start and title identify the event well enough to cache it.
            uid = this["UID"]?.value?.takeIf { it.isNotBlank() } ?: "$startMoment-$title",
            title = title,
            description = this["DESCRIPTION"]?.value?.let(::unescape)?.takeIf { it.isNotBlank() },
            location = this["LOCATION"]?.value?.let(::unescape)?.takeIf { it.isNotBlank() },
            startEpochMs = startMoment,
            endEpochMs = this["DTEND"]?.toEpochMs(zone),
            isAllDay = start.isDateOnly(),
        )
    }

    private fun Line.isDateOnly(): Boolean =
        params["VALUE"].equals("DATE", ignoreCase = true) || value.length == DATE_LENGTH

    /**
     * The three shapes a date-time arrives in: a bare date, a UTC instant ending in
     * Z, and a local time that is either floating or carries a TZID.
     */
    private fun Line.toEpochMs(zone: ZoneId): Long? = runCatching {
        when {
            isDateOnly() ->
                LocalDate.parse(value.take(DATE_LENGTH), DATE_FORMAT)
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

            value.endsWith("Z") ->
                LocalDateTime.parse(value.dropLast(1), DATE_TIME_FORMAT)
                    .atZone(ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli()

            else -> {
                val declared = params["TZID"]?.let { tzid ->
                    runCatching { ZoneId.of(tzid) }.getOrNull()
                }
                LocalDateTime.parse(value, DATE_TIME_FORMAT)
                    .atZone(declared ?: zone)
                    .toInstant()
                    .toEpochMilli()
            }
        }
    }.getOrNull()

    /**
     * Undoes RFC 5545 text escaping in one pass.
     *
     * Done as a scan rather than chained replaces: replacing the backslash escape
     * separately would corrupt a literal backslash followed by an `n`.
     */
    private fun unescape(raw: String): String {
        if (!raw.contains(ESCAPE)) return raw
        val out = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val character = raw[index]
            if (character != ESCAPE || index == raw.lastIndex) {
                out.append(character)
                index++
                continue
            }
            when (val next = raw[index + 1]) {
                'n', 'N' -> out.append('\n')
                ESCAPE, ',', ';', ':' -> out.append(next)
                else -> {
                    out.append(character)
                    out.append(next)
                }
            }
            index += 2
        }
        return out.toString()
    }

    private const val ESCAPE = '\\'
    private const val DATE_LENGTH = 8
    private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    private val DATE_TIME_FORMAT: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
}
