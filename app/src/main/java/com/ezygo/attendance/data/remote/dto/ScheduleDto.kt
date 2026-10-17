package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Timetable payloads.
 *
 * The upcoming schedule is assembled from several endpoints:
 *
 *   usersubgroups -> usersubgrouptimetables -> usersubgroupschedules
 *                                                 |- session_id  -> sessions
 *                                                 |- course_id   -> course (embedded)
 *                                                 |- delivery_method_id -> coursedeliverymethods
 *                                                 |- class_type_id      -> classtypes
 *
 * with workdays giving the institution's working weekdays.
 */

@Serializable
data class ApiSessionDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("time_from") val timeFrom: String? = null,
    @SerialName("time_to") val timeTo: String? = null,
    @SerialName("view_order") val viewOrder: JsonElement? = null,
    val type: String? = null,
)

@Serializable
data class WorkdayDto(
    val id: Int? = null,
    val name: String? = null,
    @SerialName("view_order") val viewOrder: JsonElement? = null,
)

@Serializable
data class TimetableDefinitionDto(
    val id: Int? = null,
    val name: String? = null,
    val type: String? = null,
    /**
     * Observed empty in the captured payload, so the element shape is unknown.
     * Read defensively rather than assumed to be a list of date strings.
     */
    @SerialName("excluded_dates") val excludedDates: List<JsonElement> = emptyList(),
    @SerialName("excluded_weeks") val excludedWeeks: List<String> = emptyList(),
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("upto_date") val uptoDate: String? = null,
    @SerialName("default_timetable") val defaultTimetable: JsonElement? = null,
    @SerialName("usersubgroup_id") val usersubgroupId: Int? = null,
)

@Serializable
data class ScheduleCourseDto(
    val id: Int? = null,
    val name: String? = null,
    val code: String? = null,
)

@Serializable
data class ScheduleInstitutionUserDto(
    val id: Long? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
)

@Serializable
data class ScheduleCourseUserDto(
    val id: Long? = null,
    @SerialName("course_id") val courseId: Int? = null,
    @SerialName("courserole_id") val courseRoleId: Int? = null,
    val course: ScheduleCourseDto? = null,
    @SerialName("institution_user") val institutionUser: ScheduleInstitutionUserDto? = null,
)

@Serializable
data class ScheduleSessionDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
data class NamedRefDto(
    val id: Int? = null,
    val name: String? = null,
    val code: String? = null,
)

@Serializable
data class ScheduleDto(
    val id: Long? = null,
    /** ISO date, "YYYY-MM-DD". */
    val date: String? = null,
    @SerialName("session_id") val sessionId: Int? = null,
    @SerialName("usersubgroup_id") val usersubgroupId: Int? = null,
    @SerialName("delivery_method_id") val deliveryMethodId: Int? = null,
    @SerialName("class_type_id") val classTypeId: Int? = null,
    @SerialName("course_user") val courseUser: ScheduleCourseUserDto? = null,
    @SerialName("delivery_method") val deliveryMethod: NamedRefDto? = null,
    @SerialName("class_type") val classType: NamedRefDto? = null,
    val session: ScheduleSessionDto? = null,
)

/**
 * Holidays, suspensions, swaps and handovers were all empty in the captured
 * response, so their element shape is unverified. They stay as raw elements and
 * are mined for a date field defensively — never assumed.
 */
@Serializable
data class SubgroupSchedulesDto(
    val schedules: List<ScheduleDto> = emptyList(),
    val holidays: List<JsonElement> = emptyList(),
    val suspends: List<JsonElement> = emptyList(),
    val attendenceswaps: List<JsonElement> = emptyList(),
    val attendencesessionhandovers: List<JsonElement> = emptyList(),
)
