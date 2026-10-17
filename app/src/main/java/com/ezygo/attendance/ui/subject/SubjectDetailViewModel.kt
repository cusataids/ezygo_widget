package com.ezygo.attendance.ui.subject

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.repo.ClassCountsResult
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.TimetableRepository
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.AttendanceSettings
import com.ezygo.attendance.domain.model.ClassMeeting
import com.ezygo.attendance.domain.model.ClassOverview
import com.ezygo.attendance.domain.model.ClassSessionKey
import com.ezygo.attendance.domain.model.CourseStats
import com.ezygo.attendance.domain.model.Dashboard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubjectDetailUiState(
    val course: CourseStats,
    val settings: AttendanceSettings,
    val history: List<AttendanceRecord>,
    /** Running attendance percentage, oldest recorded class first. */
    val trend: List<Double>,
    /** How the rest of the class has been doing, meeting by meeting. */
    val classOverview: ClassOverview,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SubjectDetailViewModel @Inject constructor(
    private val store: DashboardStore,
    private val preferences: PreferencesStore,
    private val timetableRepository: TimetableRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val courseId: Int = savedStateHandle.get<Int>(ARG_COURSE_ID) ?: -1

    private val _classCountsLoading = MutableStateFlow(false)
    private val _classCountsError = MutableStateFlow<String?>(null)

    /**
     * The cohort report's state, for the "How is the class doing?" dialog.
     *
     * Nothing is fetched until that dialog is opened — see [openClassOverview]. The
     * subject page itself is about the student's own record and does not need it.
     */
    val classCounts: StateFlow<ClassCountsUi> = combine(
        preferences.activePeriod.flatMapLatest { period ->
            if (period == null) flowOf(null)
            else timetableRepository.observeClassCountsFetchedAt(period)
        },
        _classCountsLoading,
        _classCountsError,
    ) { at, loading, error -> ClassCountsUi(at, loading, error) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ClassCountsUi())

    /** Called as the dialog opens. Honours the cache; see the repository's age limit. */
    fun openClassOverview() = load(force = false)

    /** The dialog's refresh control. Always asks. */
    fun refreshClassOverview() = load(force = true)

    private fun load(force: Boolean) = viewModelScope.launch {
        if (_classCountsLoading.value) return@launch
        val period = preferences.currentPeriod() ?: return@launch
        _classCountsLoading.value = true
        _classCountsError.value = null
        _classCountsError.value =
            when (val result = timetableRepository.loadClassCounts(period, force)) {
                is ClassCountsResult.Failed -> result.message
                ClassCountsResult.NoCohort -> NO_COHORT_MESSAGE
                else -> null
            }
        _classCountsLoading.value = false
    }

    /**
     * The subject, its history and its trend.
     *
     * No "what if" here any more: the question is asked of every subject at once in
     * the What if mini app, and answering it a second time for one subject meant two
     * implementations of the same arithmetic that could disagree.
     */
    val state: StateFlow<SubjectDetailUiState?> =
        store.dashboard
            .map { dashboard -> dashboard?.subjectDetail(courseId) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        const val ARG_COURSE_ID = "courseId"

        private const val NO_COHORT_MESSAGE =
            "Your class group has not loaded yet. Refresh the timetable, then try again."
    }
}

/** When the cohort report was last read, and whether it is being read now. */
data class ClassCountsUi(
    val fetchedAtEpochMs: Long? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * One course's detail, derived from the dashboard.
 *
 * A free function rather than a method so the single-subject screen and the
 * course-tabs screen derive it the same way. Two copies of this would be two chances
 * for the same subject to read differently depending on how it was opened.
 *
 * Null when the course is not in the current period, which is the "subject
 * unavailable" case rather than an error.
 */
internal fun Dashboard.subjectDetail(courseId: Int): SubjectDetailUiState? {
    val course = courses.firstOrNull { it.courseId == courseId } ?: return null
    val history = days.flatMap { day -> day.records.filter { it.courseId == courseId } }
    return SubjectDetailUiState(
        course = course,
        settings = settings,
        history = history,
        trend = runningPercentages(history.asReversed()),
        classOverview = classOverview(history),
    )
}

/**
 * The cohort's side of this course's history.
 *
 * Built by joining the student's own records to the monitoring counts on (date,
 * session), which is sound because a period on a date belongs to one course — the
 * record names the course, and the count describes the same slot.
 *
 * Oldest last, matching the history list above it, so the two read the same way
 * round. Meetings the portal has no count for are kept rather than dropped: the class
 * did meet, and [ClassMeeting.marked] being zero is how the screen says the register
 * was never submitted.
 */
private fun Dashboard.classOverview(history: List<AttendanceRecord>): ClassOverview =
    ClassOverview(
        meetings = history.map { record ->
            val counts = sessionCounts[ClassSessionKey(record.dateKey, record.sessionId)]
            ClassMeeting(
                dateKey = record.dateKey,
                displayDate = record.displayDate,
                dayName = record.dayName,
                sessionName = record.sessionName,
                present = counts?.present ?: 0,
                absent = counts?.absent ?: 0,
                attended = record.isPositive,
            )
        },
    )

/**
 * Attendance percentage after each class in turn. Shows whether the student is
 * climbing out of a shortage or sliding into one.
 */
private fun runningPercentages(chronological: List<AttendanceRecord>): List<Double> {
    var positive = 0
    return chronological.mapIndexed { index, record ->
        if (record.isPositive) positive++
        positive * 100.0 / (index + 1)
    }
}
