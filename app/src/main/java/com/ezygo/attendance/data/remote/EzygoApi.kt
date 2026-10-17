package com.ezygo.attendance.data.remote

import com.ezygo.attendance.data.remote.dto.AcademicYearSettingRequest
import com.ezygo.attendance.data.remote.dto.AcademicYearSettingResponse
import com.ezygo.attendance.data.remote.dto.DetailedReportDto
import com.ezygo.attendance.data.remote.dto.InstitutionUserDto
import com.ezygo.attendance.data.remote.dto.LoginRequest
import com.ezygo.attendance.data.remote.dto.LoginResponse
import com.ezygo.attendance.data.remote.dto.LookupRequest
import com.ezygo.attendance.data.remote.dto.MonitoringRequest
import com.ezygo.attendance.data.remote.dto.MonitoringResponse
import com.ezygo.attendance.data.remote.dto.LookupResponse
import com.ezygo.attendance.data.remote.dto.MyRolesResponse
import com.ezygo.attendance.data.remote.dto.SemesterSettingRequest
import com.ezygo.attendance.data.remote.dto.SemesterSettingResponse
import com.ezygo.attendance.data.remote.dto.UserDto
import com.ezygo.attendance.data.remote.dto.ApiSessionDto
import com.ezygo.attendance.data.remote.dto.CourseDetailDto
import com.ezygo.attendance.data.remote.dto.CourseModuleDto
import com.ezygo.attendance.data.remote.dto.CourseOutcomeDto
import com.ezygo.attendance.data.remote.dto.CourseWithUsersDto
import com.ezygo.attendance.data.remote.dto.NamedRefDto
import com.ezygo.attendance.data.remote.dto.NotificationDto
import com.ezygo.attendance.data.remote.dto.SubgroupSchedulesDto
import com.ezygo.attendance.data.remote.dto.TimetableDefinitionDto
import com.ezygo.attendance.data.remote.dto.UserSubgroupDto
import com.ezygo.attendance.data.remote.dto.WorkdayDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The complete set of endpoints this app uses. Nothing beyond the documented
 * contract is called and no field outside it is relied upon.
 */
interface EzygoApi {

    @POST("login/lookup")
    suspend fun lookup(@Body body: LookupRequest): LookupResponse

    @POST("login")
    suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("user")
    suspend fun user(): UserDto

    @GET("institutionusers/myinstitutions")
    suspend fun myInstitutions(): List<InstitutionUserDto>

    @GET("institutionuser/myroles")
    suspend fun myRoles(): MyRolesResponse

    @POST("user/setting/default_academic_year")
    suspend fun setAcademicYear(@Body body: AcademicYearSettingRequest): AcademicYearSettingResponse

    @POST("user/setting/default_semester")
    suspend fun setSemester(@Body body: SemesterSettingRequest): SemesterSettingResponse

    /**
     * Takes an empty body on purpose: the period comes from the user's server-side
     * default settings, so those must be correct before this is called.
     */
    @POST("attendancereports/student/detailed")
    suspend fun detailedReport(@Body body: Map<String, String> = emptyMap()): DetailedReportDto

    // ---- notifications ----------------------------------------------------

    /** Every message the portal has raised for this user, newest first. */
    @GET("user/notifications")
    suspend fun notifications(): List<NotificationDto>

    // ---- course catalogue -------------------------------------------------

    /** The student's courses, each with the people enrolled in it. */
    @GET("institutionuser/courses/withusers")
    suspend fun coursesWithUsers(): List<CourseWithUsersDto>

    /** Course record plus its syllabus property block. */
    @GET("courses/{courseId}")
    suspend fun courseDetail(
        @Path("courseId") courseId: Int,
        @Query("course_property") courseProperty: Int = 1,
    ): CourseDetailDto

    // Note the singular "course" segment here; the detail endpoint above uses the
    // plural "courses". That asymmetry is the portal's, not a typo.
    @GET("course/{courseId}/coursemodules")
    suspend fun courseModules(@Path("courseId") courseId: Int): List<CourseModuleDto>

    @GET("course/{courseId}/courseoutcomes")
    suspend fun courseOutcomes(@Path("courseId") courseId: Int): List<CourseOutcomeDto>

    // ---- timetable --------------------------------------------------------

    /** The institution's periods (I, II, III, ...). */
    @GET("sessions")
    suspend fun sessions(): List<ApiSessionDto>

    /** The institution's working weekdays. */
    @GET("workdays")
    suspend fun workdays(): List<WorkdayDto>

    /** The cohorts the student belongs to; supplies the usersubgroup id. */
    @GET("institutionuser/usersubgroups")
    suspend fun userSubgroups(): List<UserSubgroupDto>

    @GET("usersubgrouptimetables")
    suspend fun subgroupTimetables(
        @Query("usersubgroup") usersubgroupId: Int,
    ): List<TimetableDefinitionDto>

    /** Dated, per-session schedule for the cohort, plus holidays and suspensions. */
    @GET("usersubgroupschedules")
    suspend fun subgroupSchedules(
        @Query("usersubgroup") usersubgroupId: Int,
    ): SubgroupSchedulesDto

    /**
     * How the whole cohort did, per date and per period, over a span of dates.
     *
     * One request covers a range, so the app asks for the whole term in one call
     * rather than a call per day. See MonitoringResponse for why only a fraction of
     * the body is parsed.
     */
    @POST("attendancereports/usersubgroups/monitoring")
    suspend fun subgroupMonitoring(@Body body: MonitoringRequest): MonitoringResponse

    @GET("coursedeliverymethods")
    suspend fun deliveryMethods(): List<NamedRefDto>

    @GET("classtypes")
    suspend fun classTypes(): List<NamedRefDto>

    companion object {
        const val HOST = "production.api.ezygo.app"
        const val BASE_URL = "https://$HOST/api/v1/Xcr45_salt/"
    }
}
