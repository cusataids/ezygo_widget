package com.ezygo.attendance.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.AuthRepository
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.SamarthRepository
import com.ezygo.attendance.domain.model.Period
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val store: DashboardStore,
    private val authRepository: AuthRepository,
    private val samarthRepository: SamarthRepository,
) : ViewModel() {

    /** Served from the Room cache; no request is made just because the tab opened. */
    val profile = store.profile
    val dashboard = store.dashboard
    val activePeriod = store.activePeriod
    val isRefreshing = store.isRefreshing
    val error = store.error

    /** Reloads everything: profile, attendance, enrolment, courses and timetable. */
    fun refresh() = viewModelScope.launch { store.refresh() }

    /**
     * Changing this also changes the default academic year and semester on the
     * user's EzyGo account, because the attendance report has no per-request period.
     */
    fun selectPeriod(period: Period) = viewModelScope.launch { store.switchPeriod(period) }

    /** Whether there is any Samarth data to offer to remove. */
    val hasSamarthData = samarthRepository.hasCredentials

    /**
     * Forgets the Samarth side without touching the EzyGo session: the sign-in, the
     * cached record and the downloaded grade cards.
     */
    fun removeSamarthData() = viewModelScope.launch { samarthRepository.clear() }

    fun signOut() = viewModelScope.launch { authRepository.signOut() }
}
