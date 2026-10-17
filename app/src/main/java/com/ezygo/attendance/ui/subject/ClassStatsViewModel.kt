package com.ezygo.attendance.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.repo.ClassCountsResult
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.TimetableRepository
import com.ezygo.attendance.domain.model.ClassSessionKey
import com.ezygo.attendance.domain.model.Dashboard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One subject, with the cohort's attendance in it beside the student's own. */
data class CourseClassStats(
    val courseId: Int,
    val courseName: String,
    val meetings: Int,
    val classPercent: Double,
    val yourPercent: Double?,
)

/** One class meeting, for the best-and-worst lists and the full list behind them. */
data class MeetingStat(
    val dateKey: String,
    val displayDate: String,
    val sessionName: String,
    /** Carried so the full list can be filtered by subject without a second lookup. */
    val courseId: Int,
    val courseName: String,
    val present: Int,
    val marked: Int,
) {
    val percent: Double get() = if (marked == 0) 0.0 else present * 100.0 / marked
}

/**
 * How the whole cohort has been doing, across every subject.
 *
 * Every figure here comes from data the app already holds: the monitoring report's
 * per-session present/absent counts, joined to the student's own records to know
 * which session belonged to which subject. Nothing is estimated, and a session the
 * portal never had a register for contributes to nothing.
 */
data class ClassStats(
    val classPercent: Double?,
    val totalPresent: Int,
    val totalAbsent: Int,
    val meetings: Int,
    val yourPercent: Double?,
    val courses: List<CourseClassStats>,
    /** Class attendance per day, oldest first, for the trend line. */
    val trend: List<Double>,
    val best: List<MeetingStat>,
    val worst: List<MeetingStat>,
    /**
     * Every counted meeting, newest first.
     *
     * The extremes above are a sample; this is the register itself, for the reader who
     * wants the day rather than the pattern. Newest first matches the history list on
     * the subject screens — the recent end is the one anybody is looking for.
     */
    val allMeetings: List<MeetingStat>,
) {
    val hasData: Boolean get() = meetings > 0

    val strongest: CourseClassStats? get() = courses.maxByOrNull { it.classPercent }
    val weakest: CourseClassStats? get() = courses.minByOrNull { it.classPercent }
}

/**
 * Everything the class screens need, including how it is going.
 *
 * The figures and their provenance travel together: a cohort percentage with no idea
 * when it was read is a number you cannot act on, and this screen is the only place
 * that report is ever fetched.
 */
data class ClassStatsUiState(
    val stats: ClassStats?,
    /** When the cohort report was last read, or null if it never has been. */
    val fetchedAtEpochMs: Long?,
    val loading: Boolean,
    val error: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ClassStatsViewModel @Inject constructor(
    store: DashboardStore,
    private val preferences: PreferencesStore,
    private val timetableRepository: TimetableRepository,
) : ViewModel() {

    private val _loading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)

    private val fetchedAt: Flow<Long?> = preferences.activePeriod
        .flatMapLatest { period ->
            if (period == null) flowOf(null)
            else timetableRepository.observeClassCountsFetchedAt(period)
        }

    val state: StateFlow<ClassStatsUiState> = combine(
        store.dashboard.map { it?.toClassStats() }.distinctUntilChanged(),
        fetchedAt,
        _loading,
        _error,
    ) { stats, at, loading, error ->
        ClassStatsUiState(stats = stats, fetchedAtEpochMs = at, loading = loading, error = error)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ClassStatsUiState(null, null, false, null),
    )

    init {
        // On open, and only on open. Returns without a request when the cache is
        // younger than the repository's age limit, which is the whole point of
        // moving this off the app-wide refresh.
        load(force = false)
    }

    /** The refresh control on the class screens. Always asks. */
    fun refresh() = load(force = true)

    private fun load(force: Boolean) = viewModelScope.launch {
        if (_loading.value) return@launch
        val period = preferences.currentPeriod() ?: return@launch
        _loading.value = true
        _error.value = null
        _error.value = when (val result = timetableRepository.loadClassCounts(period, force)) {
            is ClassCountsResult.Failed -> result.message
            ClassCountsResult.NoCohort -> NO_COHORT_MESSAGE
            else -> null
        }
        _loading.value = false
    }

    private companion object {
        const val NO_COHORT_MESSAGE =
            "Your class group has not loaded yet. Refresh the timetable, then try again."
    }
}

/** How many meetings each end-of-list shows. Enough to spot a pattern, not a table. */
private const val EXTREMES = 3

private fun Dashboard.toClassStats(): ClassStats {
    // One row per class meeting the student has a record for and the portal has a
    // register for. Both halves are required: the record names the subject, the
    // register says who turned up.
    data class Row(
        val dateKey: String,
        val displayDate: String,
        val sessionName: String,
        val courseId: Int,
        val courseName: String,
        val present: Int,
        val marked: Int,
    )

    val rows = days.flatMap { day ->
        day.records.mapNotNull { record ->
            val counts = sessionCounts[ClassSessionKey(record.dateKey, record.sessionId)]
                ?: return@mapNotNull null
            if (counts.marked == 0) return@mapNotNull null
            Row(
                dateKey = record.dateKey,
                displayDate = record.displayDate,
                sessionName = record.sessionName,
                courseId = record.courseId,
                courseName = record.courseName,
                present = counts.present,
                marked = counts.marked,
            )
        }
    }

    val present = rows.sumOf { it.present }
    val seats = rows.sumOf { it.marked }

    val perCourse = rows.groupBy { it.courseId }.map { (courseId, group) ->
        val coursePresent = group.sumOf { it.present }
        val courseSeats = group.sumOf { it.marked }
        CourseClassStats(
            courseId = courseId,
            courseName = group.first().courseName,
            meetings = group.size,
            classPercent = coursePresent * 100.0 / courseSeats,
            // The student's own figure for the same subject, so the two can be read
            // against each other rather than looked up on another screen.
            yourPercent = courses.firstOrNull { it.courseId == courseId }?.stats?.percentage,
        )
    }.sortedByDescending { it.classPercent }

    // Pooled per day, oldest first: a line of one point per class would be noise,
    // and the question the trend answers is whether the cohort is drifting.
    val trend = rows.groupBy { it.dateKey }
        .toSortedMap()
        .map { (_, group) ->
            group.sumOf { it.present } * 100.0 / group.sumOf { it.marked }
        }

    val meetings = rows.map {
        MeetingStat(
            dateKey = it.dateKey,
            displayDate = it.displayDate,
            sessionName = it.sessionName,
            courseId = it.courseId,
            courseName = it.courseName,
            present = it.present,
            marked = it.marked,
        )
    }

    return ClassStats(
        classPercent = if (seats == 0) null else present * 100.0 / seats,
        totalPresent = present,
        totalAbsent = seats - present,
        meetings = rows.size,
        yourPercent = overall.percentage,
        courses = perCourse,
        trend = trend,
        best = meetings.sortedByDescending { it.percent }.take(EXTREMES),
        worst = meetings.sortedBy { it.percent }.take(EXTREMES),
        // Sorted on the "YYYYMMDD" key, which sorts correctly as text. The session
        // name breaks ties within a day, and the portal's Roman numerals happen to
        // order correctly as text too: I, II, III, IV, V, VI, VII.
        allMeetings = meetings.sortedWith(
            compareByDescending<MeetingStat> { it.dateKey }.thenBy { it.sessionName },
        ),
    )
}
