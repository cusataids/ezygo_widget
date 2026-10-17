package com.ezygo.attendance.ui.subject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.domain.AttendancePlan
import com.ezygo.attendance.domain.AttendancePlanner
import com.ezygo.attendance.domain.model.ClassSessionKey
import com.ezygo.attendance.domain.model.CourseStats
import com.ezygo.attendance.domain.model.Dashboard
import com.ezygo.attendance.domain.model.RiskBand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The figures at the top of the attendance area, and the subjects beneath them.
 *
 * The two attendance metrics are kept apart on purpose and are not two renderings of
 * one number: [averagePercent] gives every subject equal weight, [overallPercent]
 * pools every class. A subject with forty classes and one with eleven move the two by
 * very different amounts, which is the whole reason both are shown.
 */
data class AttendanceSummary(
    /** Equal weight per subject; subjects with no recorded classes are excluded. */
    val averagePercent: Double?,
    val averageBand: RiskBand,
    /** Pooled: attended / conducted across every recorded class. */
    val overallPercent: Double?,
    val attended: Int,
    val conducted: Int,
    /** How the cohort did, across the classes this student has a record for. */
    val classAveragePercent: Double?,
    /** Class meetings the portal has cohort counts for. */
    val classMeetings: Int,
    val courses: List<CourseStats>,
    val targetPercent: Int,
)

@HiltViewModel
class CourseTabsViewModel @Inject constructor(
    store: DashboardStore,
) : ViewModel() {

    val summary: StateFlow<AttendanceSummary?> = store.dashboard
        .map { it?.toSummary() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * The fewest classes that would bring the equal-weight average back to target.
     *
     * The user's own target is used rather than a fixed 75. The app treats the
     * threshold as theirs to set — the settings screen says so out loud — and quoting
     * 75 to someone who chose 80 would be this screen disagreeing with the rest.
     */
    val plan: StateFlow<AttendancePlan?> = store.dashboard
        .map { dashboard ->
            dashboard?.let { AttendancePlanner.plan(it.courses, it.settings.targetPercent) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/**
 * Both attendance figures and the cohort headline, from the one dashboard.
 *
 * Courses with no recorded classes drop out of every figure here without being
 * filtered for: they contribute no records to the pooled counts, and their percentage
 * is null so the equal-weight mean already skips them.
 */
private fun Dashboard.toSummary(): AttendanceSummary {
    // Only sessions this student has a record for, so the cohort headline describes
    // their own classes rather than every session the cohort was ever marked in.
    val mine = days.flatMap { day -> day.records }
        .mapNotNull { sessionCounts[ClassSessionKey(it.dateKey, it.sessionId)] }
        .filter { it.marked > 0 }
    val present = mine.sumOf { it.present }
    val seats = mine.sumOf { it.marked }

    return AttendanceSummary(
        averagePercent = averagePercent,
        averageBand = averageBand,
        overallPercent = overall.percentage,
        attended = overall.positive,
        conducted = overall.total,
        classAveragePercent = if (seats == 0) null else present * 100.0 / seats,
        classMeetings = mine.size,
        courses = courses,
        targetPercent = settings.targetPercent,
    )
}
