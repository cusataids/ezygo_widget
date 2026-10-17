package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.entity.ProfileEntity
import com.ezygo.attendance.data.remote.EzygoApi
import com.ezygo.attendance.data.remote.dto.UserSettingsDto
import com.ezygo.attendance.domain.model.Period
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProfileRepository @Inject constructor(
    private val api: EzygoApi,
    private val database: AppDatabase,
    private val preferences: PreferencesStore,
) {

    /** Profile is served from cache; the network is only touched on explicit refresh. */
    val profile: Flow<ProfileEntity?> = database.profileDao().profile()

    /**
     * Pulls user + institution details and returns the period the *server* currently
     * considers default. That value drives the attendance report, so it is the one
     * the app must follow rather than any local guess.
     */
    suspend fun refresh(): Period {
        val user = apiCall { api.user() }
        val institutions = apiCall { api.myInstitutions() }
        // A student can in principle be enrolled at several institutions; the one
        // matching the user's default institute is the relevant one.
        val institutionUser = institutions.firstOrNull {
            it.institutionId != null && it.institutionId == user.settings?.defaultInstitute
        } ?: institutions.firstOrNull()

        val academicYear = user.settings?.defaultAcademicYear
        val semester = user.settings?.defaultSemester

        database.profileDao().upsert(
            ProfileEntity(
                username = user.username,
                email = user.email,
                firstName = institutionUser?.firstName,
                lastName = institutionUser?.lastName,
                institutionName = institutionUser?.institution?.name,
                institutionRole = institutionUser?.institutionRole?.name,
                enrollStatus = institutionUser?.enrollStatus,
                defaultAcademicYear = academicYear,
                defaultSemester = semester,
                lastUpdatedEpochMs = System.currentTimeMillis(),
            ),
        )

        // Falls back to whatever the app was last showing rather than inventing a
        // period the server may not agree with.
        val period = user.settings?.toPeriod()
            ?: preferences.currentPeriod()
            ?: Period(Period.ACADEMIC_YEARS.last(), Period.SEMESTER_ODD)
        preferences.setActivePeriod(period)
        return period
    }

    /** The period the account is set to, or null when the server left it blank. */
    private fun UserSettingsDto.toPeriod(): Period? {
        val year = defaultAcademicYear?.takeIf { it.isNotBlank() } ?: return null
        val semester = defaultSemester?.takeIf { it.isNotBlank() } ?: return null
        return Period(year, semester)
    }
}
