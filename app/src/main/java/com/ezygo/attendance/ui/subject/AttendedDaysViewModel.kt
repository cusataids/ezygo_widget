package com.ezygo.attendance.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.domain.AttendedDay
import com.ezygo.attendance.domain.AttendedDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Every day with something attended on it, best first. */
@HiltViewModel
class AttendedDaysViewModel @Inject constructor(
    store: DashboardStore,
) : ViewModel() {

    val days: StateFlow<List<AttendedDay>> = store.dashboard
        .map { AttendedDays.rank(it?.days.orEmpty()) }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
