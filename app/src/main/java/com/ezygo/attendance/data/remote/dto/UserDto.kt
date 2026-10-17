package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Long? = null,
    val username: String? = null,
    val email: String? = null,
    val mobile: String? = null,
    val settings: UserSettingsDto? = null,
)

@Serializable
data class UserSettingsDto(
    @SerialName("default_academic_year") val defaultAcademicYear: String? = null,
    @SerialName("default_semester") val defaultSemester: String? = null,
    @SerialName("default_institutionUser") val defaultInstitutionUser: Long? = null,
    @SerialName("default_institute") val defaultInstitute: Long? = null,
)

@Serializable
data class InstitutionUserDto(
    val id: Long? = null,
    @SerialName("first_name") val firstName: String? = null,
    @SerialName("last_name") val lastName: String? = null,
    @SerialName("institution_id") val institutionId: Long? = null,
    @SerialName("institutionrole_id") val institutionRoleId: Long? = null,
    @SerialName("enroll_status") val enrollStatus: String? = null,
    val institution: InstitutionDto? = null,
    @SerialName("institution_role") val institutionRole: NamedDto? = null,
)

@Serializable
data class InstitutionDto(
    val id: Long? = null,
    val name: String? = null,
    val slug: String? = null,
    val type: String? = null,
)

@Serializable
data class NamedDto(
    val id: Long? = null,
    val name: String? = null,
)

@Serializable
data class AcademicYearSettingRequest(
    @SerialName("default_academic_year") val defaultAcademicYear: String,
)

@Serializable
data class SemesterSettingRequest(
    @SerialName("default_semester") val defaultSemester: String,
)

@Serializable
data class AcademicYearSettingResponse(
    @SerialName("default_academic_year") val defaultAcademicYear: String? = null,
)

@Serializable
data class SemesterSettingResponse(
    @SerialName("default_semester") val defaultSemester: String? = null,
)

/** Courses the student holds a role in. Not necessarily courses with attendance. */
@Serializable
data class MyRolesResponse(
    @SerialName("institutionRole") val institutionRole: NamedDto? = null,
    @SerialName("subgroupRoles") val subgroupRoles: List<NamedDto> = emptyList(),
    @SerialName("courseRoles") val courseRoles: List<NamedDto> = emptyList(),
)
