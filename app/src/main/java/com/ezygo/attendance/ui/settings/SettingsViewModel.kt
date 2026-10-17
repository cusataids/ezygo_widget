package com.ezygo.attendance.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.ThemeChoice
import com.ezygo.attendance.notifications.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The settings screen's state.
 *
 * Its own ViewModel rather than a share of the profile's: these settings are no longer
 * shown on the profile screen, and leaving them on that ViewModel would have kept the
 * profile paying for a dashboard subscription it no longer reads.
 *
 * The attendance thresholds go through [DashboardStore] rather than straight to
 * [PreferencesStore], because changing a target re-bands every subject and the store is
 * what the rest of the app observes for that.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val store: DashboardStore,
    private val preferences: PreferencesStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    /**
     * Read straight from preferences rather than from the dashboard, so the sliders
     * work before any attendance has loaded — on a cold start the dashboard is null
     * and both of them used to sit at their hardcoded defaults until it arrived.
     */
    val attendanceSettings: StateFlow<AttendanceSettings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttendanceSettings())

    val theme = preferences.theme
    val reminderSources = preferences.reminderSources
    val homeSources = preferences.homeSources

    fun setTarget(value: Int) = viewModelScope.launch { store.setTargetPercent(value) }

    fun setWatchBuffer(value: Int) = viewModelScope.launch { store.setWatchBuffer(value) }

    fun setTheme(choice: ThemeChoice) = viewModelScope.launch { preferences.setTheme(choice) }

    fun setHomeSource(key: String, enabled: Boolean) = viewModelScope.launch {
        preferences.setHomeSource(key, enabled)
    }

    /**
     * Turns one feed's reminders on or off, then reschedules.
     *
     * Immediately rather than at the next sync: a switch the user just moved should
     * take effect while they are still looking at it, and rescheduling is also what
     * cancels the reminders a switched-off feed had already queued.
     */
    fun setReminderSource(key: String, enabled: Boolean) = viewModelScope.launch {
        preferences.setReminderSource(key, enabled)
        reminderScheduler.rescheduleAll()
    }
}
