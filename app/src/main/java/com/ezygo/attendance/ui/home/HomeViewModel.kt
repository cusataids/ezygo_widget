package com.ezygo.attendance.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.dao.HiddenKeys
import com.ezygo.attendance.data.repo.CalendarRepository
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.HiddenItem
import com.ezygo.attendance.data.repo.HomeVisibilityRepository
import com.ezygo.attendance.data.repo.TaskRepository
import com.ezygo.attendance.domain.model.CalendarEvent
import com.ezygo.attendance.domain.model.FeedSource
import com.ezygo.attendance.domain.model.PendingItem
import com.ezygo.attendance.domain.model.TaskSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val store: DashboardStore,
    private val visibility: HomeVisibilityRepository,
    preferences: PreferencesStore,
    calendarRepository: CalendarRepository,
    taskRepository: TaskRepository,
) : ViewModel() {

    val dashboard = store.dashboard
    val isRefreshing = store.isRefreshing
    val isLoading = store.isLoading
    val error = store.error

    /**
     * The next few academic dates, read straight from the cache. Home never
     * triggers a calendar fetch of its own — the shared refresh does that — so this
     * section costs nothing on a screen whose whole point is loading fast.
     *
     * More rows are read than are shown, because hiding is applied afterwards: asking
     * the database for exactly four and then hiding one would leave three, and the
     * next event in line would never take the empty place.
     */
    val upcomingEvents: StateFlow<List<CalendarEvent>> = preferences.homeSources
        .flatMapLatest { sources ->
            combine(
                calendarRepository.upcomingForHome(
                    limit = UPCOMING_EVENT_COUNT * OVERSCAN,
                    // Only the calendar half of the setting: the task keys in the same
                    // set belong to the pending list, not to this query.
                    sources = sources.filter { FeedSource.fromKey(it)?.isCalendar == true },
                ),
                visibility.hiddenIds,
            ) { events, hidden ->
                events.filterNot { HiddenKeys.event(it.id) in hidden }.take(UPCOMING_EVENT_COUNT)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * What is still owed, soonest first.
     *
     * Both kinds of task come from one table, so there is nothing to merge here any
     * more — only the source mark on the row distinguishes them, because for the
     * question "what is left" they are the same kind of thing.
     *
     * Only dated tasks reach here. Home shows what is coming due; something with no
     * deadline has nothing to be coming due about, and a handful of them would push
     * the real deadlines off a list that only holds five. They are all still in the
     * tasks app, under a group of their own.
     */
    val pending: StateFlow<List<PendingItem>> = combine(
        taskRepository.pending(PENDING_COUNT * OVERSCAN),
        visibility.hiddenIds,
        preferences.homeSources,
    ) { tasks, hidden, sources ->
        tasks
            .filter {
                val key = when (it.source) {
                    TaskSource.SYNCED -> FeedSource.TASKS_SYNCED.key
                    TaskSource.OWN -> FeedSource.TASKS_OWN.key
                }
                key in sources
            }
            .map {
                PendingItem(
                    id = HiddenKeys.task(it.id),
                    title = it.title,
                    dueEpochMs = it.dueEpochMs,
                    isAllDay = it.isAllDay,
                    fromTasks = it.source == TaskSource.SYNCED,
                )
            }
            .filterNot { it.id in hidden }
            .take(PENDING_COUNT)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Everything the home screen has been asked not to show, by name.
     *
     * Listed rather than counted, so hiding is never a one-way door and never an
     * all-or-nothing one: something taken off this screen is put back from the same
     * screen, one item at a time.
     */
    val hidden: StateFlow<List<HiddenItem>> = visibility.hidden
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun hideEvent(id: String, title: String) =
        viewModelScope.launch { visibility.hideEvent(id, title) }

    fun hidePending(id: String, title: String) =
        viewModelScope.launch { visibility.hidePending(id, title) }

    fun restore(key: String) = viewModelScope.launch { visibility.show(key) }

    fun restoreAll() = viewModelScope.launch { visibility.showAll() }

    /**
     * Pull-to-refresh: reloads everything the account has. Cached content stays on
     * screen if the network fails.
     */
    fun refresh() = viewModelScope.launch { store.refresh() }

    fun dismissError() = store.clearError()

    private companion object {
        /** Enough to be useful at a glance, few enough not to bury the attendance. */
        const val UPCOMING_EVENT_COUNT = 4

        /** Enough to see what is owed without turning home into a task list. */
        const val PENDING_COUNT = 5

        /** Headroom for hidden rows, so hiding one promotes the next rather than
         * leaving a gap. */
        const val OVERSCAN = 4
    }
}
