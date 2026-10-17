package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Asks the portal how the whole cohort did, over a span of dates.
 *
 * The endpoint takes a list of subgroups, but the app only ever has one cohort for a
 * period, so the list always holds exactly one id.
 */
@Serializable
data class MonitoringRequest(
    val subgroups: List<Int>,
    @SerialName("start_date") val startDate: String,
    @SerialName("upto_date") val uptoDate: String,
)

/**
 * How many of the cohort were marked present and absent in one period.
 *
 * These are class-wide counts, not the signed-in student's own: "present 18, absent 5"
 * is eighteen of twenty-three people. `markedings` — the staff who marked it — is
 * deliberately not modelled. It names other people, and nothing in this app shows it.
 */
@Serializable
data class MonitoringSessionDto(
    val present: Int = 0,
    val absent: Int = 0,
)

/**
 * The monitoring report.
 *
 * Only [attendanceData] is read. The response also carries the schedules, the session
 * list, the subgroup record and the term's holidays and suspensions, but the app
 * already has all of those from the timetable endpoints — parsing them again here
 * would be a second source for data that already has one.
 *
 * The shape is date -> subgroup id -> session id -> counts, with every key a string
 * because they are JSON object keys. A date with nothing recorded simply has no entry,
 * and a session that was never marked comes back as zero and zero.
 */
@Serializable
data class MonitoringResponse(
    @SerialName("attendanceData")
    val attendanceData: Map<String, Map<String, Map<String, MonitoringSessionDto>>> = emptyMap(),
)
