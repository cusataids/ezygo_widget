package com.ezygo.attendance.domain.model

import com.ezygo.attendance.data.repo.SessionInfo
import java.time.LocalDate

/**
 * Which part of a day a claim covers.
 *
 * [FORENOON] and [AFTERNOON] are not this app's invention: the portal marks every
 * session `forenoon` or `afternoon` in its own session list, and that mark is what
 * these resolve against. Nothing here guesses which period falls on which side of
 * lunch — the institution already said.
 */
enum class ClaimSessionType(val label: String) {
    FULL_DAY("Full day"),
    FORENOON("FN"),
    AFTERNOON("AN"),
    CUSTOM("Custom session"),
}

/** Portal values for [SessionInfo.type]. Compared lowercase; the feed is not consistent. */
internal const val SESSION_TYPE_FORENOON = "forenoon"
internal const val SESSION_TYPE_AFTERNOON = "afternoon"

/**
 * One day of a claim, and how much of it is being claimed.
 *
 * [customSessionIds] is read only when [type] is [ClaimSessionType.CUSTOM]; it is kept
 * across a change of type rather than cleared, so flipping to Full day to look at the
 * figure and back again does not lose the periods the user had already ticked.
 */
data class ClaimDay(
    val date: LocalDate,
    val type: ClaimSessionType = ClaimSessionType.FULL_DAY,
    val customSessionIds: Set<Int> = emptySet(),
) {
    /** "YYYYMMDD", the key attendance records are stored under. */
    val dateKey: String get() = "%04d%02d%02d".format(date.year, date.monthValue, date.dayOfMonth)
}

/**
 * One session the student says should not have counted against them.
 *
 * A (date, session) pair rather than a count, which is what makes claiming the same
 * period twice impossible: two entry routes reaching the same session produce the same
 * value, and the set absorbs the duplicate.
 */
data class ClaimedSession(val dateKey: String, val sessionId: Int, val courseId: Int)

/**
 * The sessions a set of claimed days actually covers.
 *
 * Resolved against recorded attendance rather than against the timetable, and only
 * records that currently count *against* the student are returned. Three consequences,
 * all deliberate:
 *
 *  - A claim on a day with no record changes nothing. There is no absence to overturn.
 *  - A claim covering a period already marked present changes nothing either, so
 *    claiming a whole day when only one period was missed cannot inflate anything.
 *  - Every claimed session names the course it belongs to, taken from the record, so a
 *    claim is attached to the course the portal says was scheduled — not to one the
 *    user picked and might have picked wrongly.
 */
fun resolveClaimedSessions(
    days: List<AttendanceDay>,
    sessions: List<SessionInfo>,
    claims: List<ClaimDay>,
): Set<ClaimedSession> {
    if (claims.isEmpty()) return emptySet()
    val sessionType = sessions.associate { it.id to it.type?.lowercase() }
    val byDateKey = days.associateBy { it.dateKey }

    return claims.flatMapTo(mutableSetOf()) { claim ->
        val day = byDateKey[claim.dateKey] ?: return@flatMapTo emptyList()
        day.records
            // Only what is currently held against the student. A claim overturns an
            // absence; it does not add attendance that was never scheduled.
            .filterNot { it.isPositive }
            .filter { record ->
                when (claim.type) {
                    ClaimSessionType.FULL_DAY -> true
                    ClaimSessionType.CUSTOM -> record.sessionId in claim.customSessionIds
                    ClaimSessionType.FORENOON ->
                        sessionType[record.sessionId] == SESSION_TYPE_FORENOON
                    ClaimSessionType.AFTERNOON ->
                        sessionType[record.sessionId] == SESSION_TYPE_AFTERNOON
                }
            }
            .map { ClaimedSession(claim.dateKey, it.sessionId, it.courseId) }
    }
}

/** How many claimed sessions fall to each course, for the projection to add back. */
fun Set<ClaimedSession>.countByCourse(): Map<Int, Int> =
    groupingBy { it.courseId }.eachCount()

/**
 * The sessions recorded on one date, for the custom picker and for saying what a
 * claim would actually cover. Absences first is not a filter — the whole day is
 * listed — but [AttendanceRecord.isPositive] lets the picker grey out what cannot
 * be claimed.
 */
fun sessionsOn(days: List<AttendanceDay>, dateKey: String): List<AttendanceRecord> =
    days.firstOrNull { it.dateKey == dateKey }?.records?.sortedBy { it.sessionId }.orEmpty()
