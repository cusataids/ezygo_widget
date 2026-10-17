package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Course catalogue payloads: the student's courses with their cohort, and the
 * per-course syllabus material.
 */

@Serializable
data class UserGroupDto(
    val id: Long? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("affiliated_university") val affiliatedUniversity: String? = null,
)

@Serializable
data class UserSubgroupDto(
    val id: Int? = null,
    @SerialName("si_no") val serialNo: JsonElement? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("class_id") val classId: String? = null,
    val scheme: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("academic_year") val academicYear: String? = null,
    @SerialName("academic_semester") val academicSemester: String? = null,
    val usergroup: UserGroupDto? = null,
)

/** A person enrolled in a course. `courserole_id` 1 is staff, 2 is a student. */
@Serializable
data class CourseMemberDto(
    val id: Long? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("enroll_status") val enrollStatus: String? = null,
    val pivot: CourseMemberPivotDto? = null,
)

@Serializable
data class CourseMemberPivotDto(
    @SerialName("course_id") val courseId: Int? = null,
    @SerialName("institution_user_id") val institutionUserId: Long? = null,
    @SerialName("courserole_id") val courseRoleId: Int? = null,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class CourseWithUsersDto(
    val id: Int? = null,
    @SerialName("si_no") val serialNo: JsonElement? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("academic_year") val academicYear: String? = null,
    @SerialName("academic_semester") val academicSemester: String? = null,
    @SerialName("usersubgroup_id") val usersubgroupId: Int? = null,
    val roleBy: String? = null,
    val roles: List<String> = emptyList(),
    val usersubgroup: UserSubgroupDto? = null,
    @SerialName("institution_users_includes_soft_deleted")
    val members: List<CourseMemberDto> = emptyList(),
)

@Serializable
data class CoursePropertyDto(
    @SerialName("text_books") val textBooks: String? = null,
    @SerialName("reference_docs") val referenceDocs: String? = null,
    @SerialName("pre_requisites") val preRequisites: String? = null,
    @SerialName("industrial_relevence") val industrialRelevance: String? = null,
    @SerialName("content_beyond_syllabus") val contentBeyondSyllabus: String? = null,
    @SerialName("details_of_lerning_matials") val learningMaterials: String? = null,
    @SerialName("ltp_credits") val ltpCredits: String? = null,
    @SerialName("planned_houres") val plannedHours: JsonElement? = null,
    val preamble: String? = null,
    val aims: String? = null,
    val objectives: String? = null,
)

@Serializable
data class CourseTypeDto(
    val id: Int? = null,
    val name: String? = null,
    val slug: String? = null,
)

@Serializable
data class CourseDetailDto(
    val id: Int? = null,
    val name: String? = null,
    val code: String? = null,
    @SerialName("academic_year") val academicYear: String? = null,
    @SerialName("academic_semester") val academicSemester: String? = null,
    val property: CoursePropertyDto? = null,
    val usersubgroup: UserSubgroupDto? = null,
    val type: CourseTypeDto? = null,
)

@Serializable
data class CourseModuleDto(
    val id: Int? = null,
    @SerialName("si_no") val serialNo: JsonElement? = null,
    val module: String? = null,
    val lessons: String? = null,
    @SerialName("contact_hours") val contactHours: JsonElement? = null,
    @SerialName("course_id") val courseId: Int? = null,
)

@Serializable
data class CourseOutcomeDto(
    val id: Int? = null,
    @SerialName("si_no") val serialNo: JsonElement? = null,
    val name: String? = null,
    val code: String? = null,
    val outcome: String? = null,
    @SerialName("course_id") val courseId: Int? = null,
)
