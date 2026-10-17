package com.ezygo.attendance.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.dao.courseShortCodeKey
import com.ezygo.attendance.data.local.dao.savedCourseKey
import com.ezygo.attendance.data.repo.ClassCountsResult
import com.ezygo.attendance.data.repo.CourseShortCodeRepository
import com.ezygo.attendance.data.repo.SavedCourseRepository
import com.ezygo.attendance.data.repo.ShortCodeResult
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.TimetableRepository
import com.ezygo.attendance.domain.model.Period
import com.ezygo.attendance.domain.model.SavedCourse
import com.ezygo.attendance.domain.model.SessionCount
import com.ezygo.attendance.domain.model.TimetableOverride
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

/** One course the mapping screen and the override dialog can offer. */
data class MappableCourse(
    val courseKey: String,
    val courseId: Int?,
    val courseCode: String?,
    val name: String,
    val shortCode: String?,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimetableViewModel @Inject constructor(
    private val store: DashboardStore,
    private val preferences: PreferencesStore,
    private val timetableRepository: TimetableRepository,
    private val shortCodes: CourseShortCodeRepository,
    private val savedCourses: SavedCourseRepository,
) : ViewModel() {

    val dashboard = store.dashboard
    val timetable = store.timetable
    val isRefreshing = store.isRefreshing
    val error = store.error
    val timetableError = store.timetableError

    val gridView: StateFlow<Boolean> = preferences.timetableGridView
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * How the cohort did in the session whose details are open, or null while that is
     * not known yet.
     *
     * One holder rather than a flow per session: only one dialog is open at a time, so
     * tracking more than the open one would keep collectors alive for dialogs that
     * have been closed.
     */
    private val _sessionCount = MutableStateFlow<SessionCount?>(null)
    val sessionCount: StateFlow<SessionCount?> = _sessionCount.asStateFlow()

    private var sessionCountJob: Job? = null

    /**
     * Whether the cohort report has ever been read for the active period.
     *
     * Null means never. The session dialog uses it to tell two different silences
     * apart: a register the portal has no row for, and a report nobody has asked for
     * yet. Only the second is worth offering a button about.
     */
    val classCountsFetchedAt: StateFlow<Long?> = preferences.activePeriod
        .flatMapLatest { period ->
            if (period == null) flowOf(null) else repositoryCountsFetchedAt(period)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun repositoryCountsFetchedAt(period: Period) =
        timetableRepository.observeClassCountsFetchedAt(period)

    private val _classCountsLoading = MutableStateFlow(false)
    val classCountsLoading: StateFlow<Boolean> = _classCountsLoading.asStateFlow()

    private val _classCountsMessage = MutableStateFlow<String?>(null)
    val classCountsMessage: StateFlow<String?> = _classCountsMessage.asStateFlow()

    /**
     * Observes the cohort's present/absent counts for one recorded session.
     *
     * Reads only. The cohort report is no longer fetched as a side effect of opening
     * anything — it is a whole term of data for one line in a dialog, and the dialog
     * can say so and offer the fetch instead. [fetchClassCounts] is that offer.
     */
    fun loadSessionCount(dateKey: String, sessionId: Int) {
        sessionCountJob?.cancel()
        _sessionCount.value = null
        val date = parseReportDateKey(dateKey) ?: return
        sessionCountJob = viewModelScope.launch {
            val period = preferences.currentPeriod() ?: return@launch
            timetableRepository.observeSessionCounts(period, date).collect { bySession ->
                _sessionCount.value = bySession[sessionId]
            }
        }
    }

    /**
     * Reads the cohort report on request, from the session dialog.
     *
     * Forced, because the only reason this is on screen is that the reader asked for
     * it: having tapped a button labelled "Fetch", being told the cache is eleven
     * hours old and therefore fine would be no answer at all.
     */
    fun fetchClassCounts() = viewModelScope.launch {
        if (_classCountsLoading.value) return@launch
        val period = preferences.currentPeriod() ?: return@launch
        _classCountsLoading.value = true
        _classCountsMessage.value = null
        _classCountsMessage.value = when (val result =
            timetableRepository.loadClassCounts(period, force = true)) {
            is ClassCountsResult.Failed -> result.message
            ClassCountsResult.NoCohort -> NO_COHORT_MESSAGE
            else -> null
        }
        _classCountsLoading.value = false
    }

    /** Stops observing when the dialog closes. */
    fun clearSessionCount() {
        sessionCountJob?.cancel()
        sessionCountJob = null
        _sessionCount.value = null
        _classCountsMessage.value = null
    }

    private companion object {
        const val NO_COHORT_MESSAGE =
            "Your class group has not loaded yet. Pull down to refresh the timetable first."
    }

    /** The report's "YYYYMMDD" key as a date; null when it is not that shape. */
    private fun parseReportDateKey(dateKey: String): LocalDate? {
        if (dateKey.length != 8) return null
        return runCatching {
            LocalDate.of(
                dateKey.substring(0, 4).toInt(),
                dateKey.substring(4, 6).toInt(),
                dateKey.substring(6, 8).toInt(),
            )
        }.getOrNull()
    }

    /**
     * Every course this semester, with whatever short code it already carries.
     *
     * Drawn from the dashboard rather than from the timetable, so a course the student
     * is enrolled in can be given a code before its first class is ever scheduled.
     */
    val courses: StateFlow<List<MappableCourse>> = combine(
        store.dashboard,
        shortCodes.codes,
    ) { dashboard, codes ->
        val enrolled = dashboard?.courses.orEmpty().map {
            Triple(it.courseId, it.courseName, it.courseCode)
        } + dashboard?.coursesWithoutRecords.orEmpty().map {
            // Enrolment carries no course code, so these are filed by id. The
            // timetable looks a slot up by both keys for exactly this reason.
            Triple(it.courseId, it.name, null)
        }
        enrolled
            .distinctBy { it.first }
            .mapNotNull { (id, name, code) ->
                val key = courseShortCodeKey(code, id) ?: return@mapNotNull null
                MappableCourse(
                    courseKey = key,
                    courseId = id,
                    courseCode = code,
                    name = name,
                    shortCode = codes[key]?.shortCode,
                )
            }
            .sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Why a short code was refused, by course key.
     *
     * Kept per course rather than as one message, because two rows can be wrong at
     * once and a single error line would have to pick one of them to be about.
     */
    private val _codeErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val codeErrors: StateFlow<Map<String, String>> = _codeErrors.asStateFlow()

    /**
     * Every course a placed class can be, in one list.
     *
     * This semester's courses and everything the user has described before, merged and
     * deduplicated: the portal's courses are what they will usually want, and their own
     * inventions have to keep being offered or they are invented again every week.
     */
    val courseChoices: StateFlow<List<SavedCourse>> = combine(
        courses,
        savedCourses.courses,
    ) { semester, saved ->
        val fromSemester = semester.map {
            SavedCourse(
                key = savedCourseKey(it.courseCode, it.name),
                name = it.name,
                code = it.courseCode,
                shortCode = it.shortCode,
                teacher = null,
            )
        }
        // The stored one wins on a clash: it carries a teacher, and the semester's copy
        // never does — which also means a course of the user's own keeps its cross.
        (saved + fromSemester).distinctBy { it.key }.sortedBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** The institution's teaching periods, which an override has to choose one of. */
    val periods: StateFlow<List<Pair<Int, String>>> = preferences.activePeriod
        .flatMapLatest { period ->
            if (period == null) flowOf(emptyList()) else timetableRepository.periods(period)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val overrides: StateFlow<List<TimetableOverride>> = preferences.activePeriod
        .flatMapLatest { period ->
            if (period == null) flowOf(emptyList()) else timetableRepository.overrides(period)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Removes a course the user described. The portal's own are not offered for it. */
    fun forgetCourse(course: SavedCourse) = viewModelScope.launch {
        if (course.stored) savedCourses.forget(course.key)
    }

    fun setGridView(value: Boolean) = viewModelScope.launch {
        preferences.setTimetableGridView(value)
    }

    fun setShortCode(course: MappableCourse, code: String) = viewModelScope.launch {
        val result = shortCodes.set(
            courseCode = course.courseCode,
            courseId = course.courseId,
            courseName = course.name,
            shortCode = code,
        )
        _codeErrors.update { errors ->
            when (result) {
                is ShortCodeResult.Taken ->
                    errors + (course.courseKey to "Already used by " + result.courseName)
                // Anything that got through clears whatever the last attempt said, so a
                // corrected code stops being marked wrong the moment it is corrected.
                else -> errors - course.courseKey
            }
        }
    }

    /**
     * Saves a class the user placed, on one period of one weekday, every week.
     *
     * [replacing] is where it used to be. An override is keyed by its day and period,
     * so moving one to a different slot is a delete and an insert rather than an
     * update — without the delete the old cell would keep its class as well.
     */
    fun saveOverride(
        override: TimetableOverride,
        replacing: TimetableOverride? = null,
    ) = viewModelScope.launch {
        withPeriod { period ->
            if (replacing != null &&
                (replacing.dayOfWeek != override.dayOfWeek ||
                    replacing.sessionId != override.sessionId)
            ) {
                timetableRepository.removeOverride(
                    period,
                    replacing.dayOfWeek,
                    replacing.sessionId,
                )
            }
            timetableRepository.setOverride(period, override)
        }
        // Described once, offered from then on — including a custom course that the
        // portal has never heard of.
        savedCourses.remember(
            name = override.courseName,
            code = override.courseCode,
            shortCode = override.shortCode,
            teacher = override.instructor,
        )
    }

    fun removeOverride(dayOfWeek: DayOfWeek, sessionId: Int) = viewModelScope.launch {
        withPeriod { timetableRepository.removeOverride(it, dayOfWeek, sessionId) }
    }

    /** Reloads everything, schedule and attendance alike. */
    fun refresh() = viewModelScope.launch { store.refresh() }

    private suspend fun withPeriod(block: suspend (Period) -> Unit) {
        preferences.activePeriod.first()?.let { block(it) }
    }
}
