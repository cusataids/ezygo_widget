package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.entity.CourseEntity
import com.ezygo.attendance.data.local.entity.EnrolledCourseEntity
import com.ezygo.attendance.data.local.entity.PeriodEntity
import com.ezygo.attendance.data.local.entity.SessionEntity
import com.ezygo.attendance.data.remote.EzygoApi
import com.ezygo.attendance.data.remote.dto.AcademicYearSettingRequest
import com.ezygo.attendance.data.remote.dto.CourseDto
import com.ezygo.attendance.data.remote.dto.ReportParser
import com.ezygo.attendance.data.remote.dto.SemesterSettingRequest
import com.ezygo.attendance.domain.Normalizer
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.Period
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One period's cached attendance, exactly as the UI consumes it. */
data class AttendanceSnapshot(
    val period: Period,
    val records: List<AttendanceRecord>,
    /** Courses the student is enrolled in, including any with no recorded classes. */
    val enrolledCourses: List<EnrolledCourse>,
    val sessions: List<SessionInfo>,
    val lastUpdatedEpochMs: Long?,
) {
    /** True once a refresh has ever succeeded for this period. */
    val hasSnapshot: Boolean get() = lastUpdatedEpochMs != null
}

data class EnrolledCourse(val courseId: Int, val name: String)

@Singleton
class AttendanceRepository @Inject constructor(
    private val api: EzygoApi,
    private val database: AppDatabase,
    private val preferences: PreferencesStore,
) {

    /**
     * Room is the single source of truth. The UI observes this and a refresh simply
     * writes new rows, so cached data renders instantly and a failed refresh leaves
     * the previous snapshot untouched on screen.
     */
    fun observe(period: Period): Flow<AttendanceSnapshot> {
        val dao = database.attendanceDao()
        return combine(
            dao.recordsFor(period.key),
            dao.enrolledCoursesFor(period.key),
            dao.sessionsFor(period.key),
            dao.periodFor(period.key),
        ) { records, enrolled, sessions, periodRow ->
            AttendanceSnapshot(
                period = period,
                records = records.map { it.toDomain() },
                enrolledCourses = enrolled.map { EnrolledCourse(it.courseId, it.name) },
                sessions = sessions.map { it.toInfo() },
                lastUpdatedEpochMs = periodRow?.lastUpdatedEpochMs,
            )
        }
    }

    /**
     * The same snapshot as [observe], read once and returned.
     *
     * For a caller that renders a single frame and then goes away — the home-screen
     * widget — this avoids combining four observed Flows and waiting for each to
     * emit, which is both slower and one more thing that can fail to complete.
     */
    suspend fun snapshotOnce(period: Period): AttendanceSnapshot {
        val dao = database.attendanceDao()
        return AttendanceSnapshot(
            period = period,
            records = dao.recordsOnce(period.key).map { it.toDomain() },
            enrolledCourses = dao.enrolledCoursesOnce(period.key)
                .map { EnrolledCourse(it.courseId, it.name) },
            sessions = dao.sessionsOnce(period.key).map { it.toInfo() },
            lastUpdatedEpochMs = dao.periodOnce(period.key)?.lastUpdatedEpochMs,
        )
    }

    suspend fun hasSnapshot(period: Period): Boolean =
        database.attendanceDao().hasSnapshot(period.key) > 0

    /**
     * Points the account at [requested] and loads that period.
     *
     * Note this writes the user's *portal-wide* default academic year and semester —
     * the attendance endpoint has no per-request period parameter, so there is no way
     * to read another period without changing the account setting.
     *
     * The period the app switches to is the one the server echoes back, not the one
     * requested, so the cache is never labelled with a period the server disagreed with.
     * The active period is committed before the report is fetched, so a failure here
     * leaves the app showing the new period's own (possibly empty) cache rather than
     * the previous semester's numbers under a new label.
     */
    suspend fun switchPeriod(requested: Period): Period {
        val confirmedYear = apiCall {
            api.setAcademicYear(AcademicYearSettingRequest(requested.academicYear))
        }.defaultAcademicYear ?: requested.academicYear

        val confirmedSemester = apiCall {
            api.setSemester(SemesterSettingRequest(requested.semester))
        }.defaultSemester ?: requested.semester

        val confirmed = Period(confirmedYear, confirmedSemester)
        preferences.setActivePeriod(confirmed)
        refresh(confirmed)
        return confirmed
    }

    /**
     * Fetches the report for the period the server is currently set to and replaces
     * that period's cached snapshot atomically.
     *
     * The caller is responsible for having the server settings correct — see
     * [switchPeriod]. Passing a [period] that does not match the account's current
     * setting would cache the wrong semester's data under it.
     */
    suspend fun refresh(period: Period) {
        val reportDto = apiCall { api.detailedReport() }
        val parsed = ReportParser.parse(reportDto)
        val records = Normalizer.normalize(parsed)

        // The report itself declares which period it belongs to, via the academic
        // year and semester on each course. Trust that over the period we asked for:
        // the account default can be changed elsewhere (the web portal, another
        // device), and caching a semester's data under the wrong key is exactly the
        // semester-mixing this app must never do.
        val effective = declaredPeriod(parsed.courses.values) ?: period
        if (effective != period) preferences.setActivePeriod(effective)

        // Course roles are a separate source of truth: being enrolled does not imply
        // any class was recorded, and the two lists genuinely differ in live data.
        val enrolled = apiCall { api.myRoles() }.courseRoles.mapNotNull { role ->
            val id = role.id?.toInt() ?: return@mapNotNull null
            EnrolledCourseEntity(effective.key, id, role.name ?: "Course #$id")
        }.distinctBy { it.courseId }

        database.attendanceDao().replaceSnapshot(
            period = PeriodEntity(
                periodKey = effective.key,
                academicYear = effective.academicYear,
                semester = effective.semester,
                lastUpdatedEpochMs = System.currentTimeMillis(),
            ),
            courses = parsed.courses.values.map {
                CourseEntity(effective.key, it.id, it.name, it.code, it.serialNo)
            },
            enrolled = enrolled,
            sessions = parsed.sessions.map {
                SessionEntity(effective.key, it.id, it.name, it.timeFrom, it.timeTo, it.viewOrder, it.type)
            },
            records = records.map { it.toEntity(effective) },
        )
    }

    companion object {
        /**
         * The period the report says it covers, from the course metadata.
         *
         * Returns null when the courses carry no period (or disagree with each
         * other), in which case the caller keeps the period it asked for rather
         * than guessing.
         */
        internal fun declaredPeriod(courses: Collection<CourseDto>): Period? {
            val declared = courses.mapNotNull { course ->
                val year = course.academicYear
                val semester = course.academicSemester
                if (!year.isNullOrBlank() && !semester.isNullOrBlank()) {
                    Period(year, semester)
                } else {
                    null
                }
            }.distinct()
            return declared.singleOrNull()
        }
    }
}
