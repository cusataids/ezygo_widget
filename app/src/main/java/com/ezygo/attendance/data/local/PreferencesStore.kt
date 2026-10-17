package com.ezygo.attendance.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.FeedSource
import com.ezygo.attendance.domain.model.Period
import com.ezygo.attendance.domain.model.ThemeChoice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Non-sensitive local state: which period the app is currently showing and the
 * user's attendance thresholds. Thresholds are configurable because institution
 * policy is not something this app should assert as fact.
 */
class PreferencesStore(private val context: Context) {

    private val activePeriodKey = stringPreferencesKey("active_period")
    private val targetPercentKey = intPreferencesKey("target_percent")
    private val watchBufferKey = intPreferencesKey("watch_buffer_points")

    // Only the flag lives here. The credentials themselves are sealed in
    // CredentialStore; this is just whether the user opted in.
    private val rememberPasswordKey = booleanPreferencesKey("remember_password")
    private val reminderSourcesKey = stringSetPreferencesKey("reminder_sources")
    private val homeSourcesKey = stringSetPreferencesKey("home_sources")

    /** Which of the two timetable layouts the user last chose. */
    private val timetableGridKey = booleanPreferencesKey("timetable_grid_view")

    private val themeKey = stringPreferencesKey("theme_choice")

    val rememberPassword: Flow<Boolean> =
        context.preferencesFlow.map { prefs -> prefs[rememberPasswordKey] == true }

    val activePeriod: Flow<Period?> = context.preferencesFlow.map { prefs ->
        prefs[activePeriodKey]?.let(Period::parseKey)
    }

    val settings: Flow<AttendanceSettings> = context.preferencesFlow.map { prefs ->
        AttendanceSettings(
            targetPercent = prefs[targetPercentKey] ?: AttendanceSettings.DEFAULT_TARGET,
            watchBufferPoints = prefs[watchBufferKey] ?: AttendanceSettings.DEFAULT_WATCH_BUFFER,
        )
    }

    /**
     * True for the week grid, false for the day list.
     *
     * Remembered rather than reset per visit: a student who reads their timetable as a
     * grid reads it as a grid every time, and re-choosing on every open is a chore.
     */
    val timetableGridView: Flow<Boolean> =
        context.preferencesFlow.map { prefs -> prefs[timetableGridKey] == true }

    suspend fun setTimetableGridView(value: Boolean) {
        context.appDataStore.edit { it[timetableGridKey] = value }
    }

    /**
     * Light, dark, or whatever the phone says.
     *
     * Never null: an unset key means the user has not chosen, which resolves to the
     * app's own default rather than to the system's — see [ThemeChoice.DEFAULT].
     */
    val theme: Flow<ThemeChoice> = context.preferencesFlow.map { prefs ->
        ThemeChoice.fromKey(prefs[themeKey])
    }

    suspend fun setTheme(choice: ThemeChoice) {
        context.appDataStore.edit { it[themeKey] = choice.key }
    }

    suspend fun currentPeriod(): Period? = activePeriod.first()

    suspend fun currentSettings(): AttendanceSettings = settings.first()

    suspend fun setActivePeriod(period: Period) {
        context.appDataStore.edit { it[activePeriodKey] = period.key }
    }

    suspend fun currentRememberPassword(): Boolean = rememberPassword.first()

    /**
     * Which sources may raise a notification.
     *
     * Stored as the set of enabled source keys rather than a flag per feed, so adding a
     * source needs no new preference key and no migration. Absent means "not chosen
     * yet" — see [FeedSource.DEFAULT_NOTIFY].
     */
    val reminderSources: Flow<Set<String>> = context.preferencesFlow.map { prefs ->
        prefs[reminderSourcesKey] ?: FeedSource.DEFAULT_NOTIFY
    }

    /**
     * Which sources may appear on the home screen.
     *
     * Kept apart from [reminderSources] because they are different questions: a feed
     * worth glancing at is not necessarily one worth being interrupted by, and the
     * academic calendar is exactly that case.
     */
    val homeSources: Flow<Set<String>> = context.preferencesFlow.map { prefs ->
        prefs[homeSourcesKey] ?: FeedSource.DEFAULT_HOME
    }

    suspend fun currentReminderSources(): Set<String> = reminderSources.first()

    suspend fun setReminderSource(key: String, enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            val current = prefs[reminderSourcesKey] ?: FeedSource.DEFAULT_NOTIFY
            prefs[reminderSourcesKey] = if (enabled) current + key else current - key
        }
    }

    suspend fun setHomeSource(key: String, enabled: Boolean) {
        context.appDataStore.edit { prefs ->
            val current = prefs[homeSourcesKey] ?: FeedSource.DEFAULT_HOME
            prefs[homeSourcesKey] = if (enabled) current + key else current - key
        }
    }

    suspend fun setRememberPassword(value: Boolean) {
        context.appDataStore.edit { it[rememberPasswordKey] = value }
    }

    suspend fun setTargetPercent(value: Int) {
        context.appDataStore.edit { it[targetPercentKey] = value.coerceIn(1, 100) }
    }

    suspend fun setWatchBuffer(value: Int) {
        context.appDataStore.edit { it[watchBufferKey] = value.coerceIn(0, 50) }
    }
}
