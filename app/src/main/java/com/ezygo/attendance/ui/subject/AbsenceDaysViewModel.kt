package com.ezygo.attendance.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.domain.AbsenceDay
import com.ezygo.attendance.domain.AbsenceDays
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * The full ranked list of days with absences, and the one filter over it.
 *
 * Its own view model rather than a corner of [CourseTabsViewModel]: the attendance
 * screen needs a count and a worst case to put on a card, and this screen needs every
 * row and a filter. Sharing one would mean the attendance screen ranking a term of
 * days it never shows.
 */
@HiltViewModel
class AbsenceDaysViewModel @Inject constructor(
    store: DashboardStore,
) : ViewModel() {

    private val _wholeDaysOnly = MutableStateFlow(false)
    val wholeDaysOnly: StateFlow<Boolean> = _wholeDaysOnly.asStateFlow()

    fun toggleWholeDaysOnly() = _wholeDaysOnly.update { !it }

    /**
     * Every day with an absence, not merely the worst ten.
     *
     * [Int.MAX_VALUE] rather than a larger constant: there is no sensible number of
     * days to stop at, and a term cannot produce enough rows for the cap to matter.
     */
    val days: StateFlow<List<AbsenceDay>> =
        combine(store.dashboard, _wholeDaysOnly) { dashboard, wholeOnly ->
            AbsenceDays.rank(
                days = dashboard?.days.orEmpty(),
                wholeDaysOnly = wholeOnly,
                limit = Int.MAX_VALUE,
            )
        }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
