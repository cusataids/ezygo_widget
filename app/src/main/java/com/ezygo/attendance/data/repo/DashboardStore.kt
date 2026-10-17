package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.di.ApplicationScope
import com.ezygo.attendance.domain.AttendanceCalculator
import com.ezygo.attendance.domain.model.AttendanceDay
import com.ezygo.attendance.domain.model.Dashboard
import com.ezygo.attendance.domain.model.Period
import com.ezygo.attendance.domain.model.UpcomingTimetable
import com.ezygo.attendance.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place the whole app reads its attendance figures from.
 *
 * Every screen observes this, so Home, Timetable, Subject detail and Profile can
 * never show figures derived from different snapshots or different periods.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class DashboardStore @Inject constructor(
    private val preferences: PreferencesStore,
    private val attendanceRepository: AttendanceRepository,
    private val profileRepository: ProfileRepository,
    private val timetableRepository: TimetableRepository,
    private val courseCatalogRepository: CourseCatalogRepository,
    private val notificationsRepository: NotificationsRepository,
    private val calendarRepository: CalendarRepository,
    private val widgetUpdater: WidgetUpdater,
    @ApplicationScope private val scope: CoroutineScope,
) {

    private val refreshMutex = Mutex()

    /**
     * A refresh the user asked for, which is the only kind that should spin a
     * pull-to-refresh indicator.
     */
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Any load at all, background ones included. Screens use this to decide between
     * a skeleton and their cached content — never to show a spinner over content
     * that is already on screen.
     */
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Last refresh failure, shown inline without disturbing cached content. */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * When this process started, which is near enough to when the app was opened:
     * the store is a singleton built on the way to the first screen.
     */
    private val openedAtMs = System.currentTimeMillis()

    /** Holds a start-up failure back until the grace window is up. See [publishError]. */
    private var pendingError: Job? = null

    /**
     * Timetable failures are tracked separately so a schedule problem never hides
     * the attendance figures, which are the more important of the two.
     */
    private val _timetableError = MutableStateFlow<String?>(null)
    val timetableError: StateFlow<String?> = _timetableError.asStateFlow()

    /**
     * The day the upcoming view is anchored to. Re-read on every refresh so an app
     * left open overnight rolls onto the new day when the user pulls to refresh.
     */
    private val _today = MutableStateFlow(LocalDate.now())

    val activePeriod: StateFlow<Period?> =
        preferences.activePeriod.stateIn(scope, SharingStarted.Eagerly, null)

    val profile = profileRepository.profile.stateIn(scope, SharingStarted.Eagerly, null)

    /**
     * Recomputed from Room whenever the cache or the user's thresholds change, so a
     * refresh or a target change updates every screen with no explicit invalidation.
     */
    val dashboard: StateFlow<Dashboard?> = preferences.activePeriod
        .filterNotNull()
        .flatMapLatest { period ->
            combine(
                attendanceRepository.observe(period),
                preferences.settings,
                timetableRepository.observeAllSessionCounts(period),
            ) { snapshot, settings, sessionCounts ->
                val courses = AttendanceCalculator.courseStats(snapshot.records, settings)
                val courseIdsWithRecords = snapshot.records.mapTo(HashSet()) { it.courseId }

                val subjectPercentages = courses.mapNotNull { it.stats.percentage }
                val average = subjectPercentages.takeIf { it.isNotEmpty() }?.average()

                Dashboard(
                    period = snapshot.period,
                    settings = settings,
                    overall = AttendanceCalculator.statsFor(snapshot.records, settings),
                    averagePercent = average,
                    averageBand = AttendanceCalculator.bandFor(average, settings),
                    courses = courses,
                    sessions = snapshot.sessions,
                    sessionCounts = sessionCounts,
                    // Enrolled but never marked: surfaced honestly rather than as 0%.
                    coursesWithoutRecords = snapshot.enrolledCourses
                        .filterNot { it.courseId in courseIdsWithRecords },
                    recent = snapshot.records.take(RECENT_LIMIT),
                    days = snapshot.records
                        .groupBy { it.dateKey }
                        .entries
                        .sortedByDescending { it.key }
                        .map { (dateKey, dayRecords) ->
                            AttendanceDay(
                                dateKey = dateKey,
                                displayDate = dayRecords.first().displayDate,
                                dayName = dayRecords.first().dayName,
                                records = dayRecords,
                            )
                        },
                    lastUpdatedEpochMs = snapshot.lastUpdatedEpochMs,
                    hasSnapshot = snapshot.hasSnapshot,
                )
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** The next seven days of the cohort's schedule, rebuilt from the cache. */
    val timetable: StateFlow<UpcomingTimetable?> =
        combine(preferences.activePeriod.filterNotNull(), _today) { period, today -> period to today }
            .flatMapLatest { (period, today) -> timetableRepository.observeUpcoming(period, today) }
            .stateIn(scope, SharingStarted.Eagerly, null)

    /** Clears the banner and, with it, any failure still waiting out its grace window. */
    fun clearError() {
        pendingError?.cancel()
        pendingError = null
        _error.value = null
    }

    /**
     * Reloads everything the account has: profile, institution, the attendance
     * report, the enrolment list and the timetable. Every entry point — the home
     * pull, the timetable pull, the profile button, the widget — refreshes the
     * same whole, so no screen can be looking at older data than its neighbour.
     *
     * Failures surface as an inline message and leave the cached snapshot in place:
     * the screen never blanks because the network did.
     *
     * [userInitiated] drives the pull-to-refresh indicator only. A background load
     * updates the same data without putting a spinner over content the user is
     * already reading.
     */
    suspend fun refresh(userInitiated: Boolean = true) = withRefreshLock(userInitiated) {
        _today.value = LocalDate.now()

        val period = profileRepository.refresh()
        attendanceRepository.refresh(period)
        clearMemoryCaches()

        // Best effort: the attendance refresh above has already succeeded and must
        // not be reported as failed because the schedule could not load.
        refreshTimetableBestEffort(currentPeriodOrDefault(period))

        // A pull means "bring everything up to date", so it forces the feed; a
        // background load lets the cache-first interval decide, which is usually
        // "not yet". Either way a calendar problem is not an attendance problem.
        syncCalendarBestEffort(force = userInitiated)
    }

    /**
     * Everything, quietly, on first sign-in and session restore. Not user-initiated:
     * a restored session shows its cached dashboard immediately and fills in behind.
     */
    suspend fun initialLoad() = refresh(userInitiated = false)

    /**
     * Switches the account's period and reloads everything for it. See
     * [AttendanceRepository.switchPeriod] for why this also changes the setting in
     * the portal itself.
     *
     * A period change invalidates far more than the attendance report — the cohort,
     * the timetable and the course list all belong to the semester.
     */
    suspend fun switchPeriod(period: Period) = withRefreshLock(userInitiated = true) {
        _today.value = LocalDate.now()
        val confirmed = attendanceRepository.switchPeriod(period)
        profileRepository.refresh()
        clearMemoryCaches()
        refreshTimetableBestEffort(confirmed)
    }

    /**
     * The course catalogue and the notification list live in memory rather than
     * Room, so dropping them is how their screens pick up a change on next open.
     * Neither is re-fetched here: doing so would spend calls on screens that may
     * never be opened, and both fetch on open anyway.
     */
    private suspend fun clearMemoryCaches() {
        courseCatalogRepository.clear()
        notificationsRepository.clear()
    }

    suspend fun setTargetPercent(value: Int) = preferences.setTargetPercent(value)

    suspend fun setWatchBuffer(value: Int) = preferences.setWatchBuffer(value)

    /**
     * Shows a failure, but never in the first few seconds after the app is opened.
     *
     * The first load runs against whatever token was restored from disk. If that
     * token has expired the call comes back 401 almost immediately, long before the
     * silent re-authentication that answers it has had time to finish — so the very
     * first thing the user saw on opening the app was "Your session has expired",
     * with a Retry button, for a session that was in the middle of renewing itself
     * and would be valid a moment later.
     *
     * Holding the message back for [STARTUP_GRACE_MS] lets that round trip land. A
     * renewal that succeeds refreshes, which calls [clearError] and cancels the
     * pending message before it is ever shown; one that genuinely fails leaves the
     * message to appear at the end of the window.
     *
     * Only background loads are delayed. A failure the user asked for by pulling to
     * refresh is reported at once — they are watching for an answer, and silence is
     * a worse answer than an error.
     */
    private fun publishError(message: String, immediate: Boolean) {
        pendingError?.cancel()
        val waitMs = STARTUP_GRACE_MS - (System.currentTimeMillis() - openedAtMs)
        if (immediate || waitMs <= 0) {
            _error.value = message
            return
        }
        pendingError = scope.launch {
            delay(waitMs)
            _error.value = message
        }
    }

    /** Serialises refreshes so a pull-to-refresh cannot race a period switch. */
    private suspend inline fun withRefreshLock(
        userInitiated: Boolean,
        crossinline block: suspend () -> Unit,
    ) {
        refreshMutex.withLock {
            _isLoading.value = true
            if (userInitiated) _isRefreshing.value = true
            clearError()
            try {
                block()
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                publishError(ApiError.from(throwable).userMessage, immediate = userInitiated)
            } finally {
                _isRefreshing.value = false
                _isLoading.value = false
            }
        }
        // Outside the lock: the home-screen widgets read the same Room cache, so they
        // are redrawn once the write has landed. Push-on-refresh is what lets the
        // widget skip polling entirely.
        widgetUpdater.updateAll()
    }

    /**
     * The calendar lives on a different server entirely, so its failure says
     * nothing about the attendance data and must not surface as a refresh error.
     */
    private suspend fun syncCalendarBestEffort(force: Boolean) {
        try {
            calendarRepository.sync(force = force)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The screens show the last successful sync time, which is the honest
            // signal here: cached events stay exactly as they were.
        }
    }

    /**
     * Loads the schedule without letting its failure fail the whole refresh.
     * Cancellation still propagates — only real errors are captured.
     */
    private suspend fun refreshTimetableBestEffort(period: Period) {
        _timetableError.value = null
        try {
            timetableRepository.refresh(period)
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            _timetableError.value = ApiError.from(throwable).userMessage
        }
    }

    /**
     * The period actually in force after a refresh: the report may have declared a
     * different one than we asked for, and the timetable must follow the same one.
     */
    private suspend fun currentPeriodOrDefault(fallback: Period): Period =
        preferences.currentPeriod() ?: fallback

    private companion object {
        const val RECENT_LIMIT = 6

        /**
         * How long a failure on the first load is held back. Long enough to cover a
         * token renewal round trip on a slow connection, short enough that a genuine
         * outage is still reported while the user is looking at the screen.
         */
        const val STARTUP_GRACE_MS = 7_000L
    }
}
