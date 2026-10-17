package com.ezygo.attendance.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.ui.common.AttendanceBar
import com.ezygo.attendance.ui.common.AttendanceMixChart
import com.ezygo.attendance.ui.common.AttendanceTrendChart
import com.ezygo.attendance.ui.common.ClassCountsStatus
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.asPercentLabel1dp
import com.ezygo.attendance.ui.common.dateWithWeekday
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * How the whole cohort has been doing, in one page.
 *
 * Reached from the "How is the class doing?" card, which carries only the headline:
 * per-subject figures, a trend and the extremes need room to be read, and a card that
 * tried to hold them would be a wall of small numbers.
 *
 * Every figure here is the cohort's, except where a row says "you" — the comparison
 * is the point of the page, and the two are never mixed into one number.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassStatsScreen(
    onBack: () -> Unit,
    onOpenAllClasses: () -> Unit,
    viewModel: ClassStatsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How is the class doing?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // A button as well as the pull gesture: this is the one screen in
                    // the app whose data does not arrive on its own, so the way to
                    // ask for it should be visible rather than discovered.
                    IconButton(onClick = viewModel::refresh, enabled = !ui.loading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh class attendance")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = ui.loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            val data = ui.stats
            if (data == null || !data.hasData) {
                ClassCountsEmpty(ui)
                return@PullToRefreshBox
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ClassCountsStatus(
                        fetchedAtEpochMs = ui.fetchedAtEpochMs,
                        loading = ui.loading,
                    )
                }
                ui.error?.let { message ->
                    item { InlineError(message, onRetry = viewModel::refresh) }
                }
                item { HeadlineCard(data) }

                if (data.trend.size >= 3) {
                    item { SectionHeader("Class attendance over time") }
                    item {
                        // One point per day rather than per class: the question is whether
                        // the cohort is drifting, and a point per period is noise.
                        AttendanceTrendChart(
                            actual = data.trend,
                            lineColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                item { SectionHeader("By subject", trailing = "${data.courses.size}") }
                item { StrongestWeakest(data) }
                items(data.courses, key = { it.courseId }) { CourseStatRow(it) }

                if (data.worst.isNotEmpty()) {
                    item { SectionHeader("Least attended classes") }
                    item { MeetingList(data.worst) }
                }
                if (data.best.isNotEmpty()) {
                    item { SectionHeader("Best attended classes") }
                    item { MeetingList(data.best) }
                }

                // Last, because it is the way out of the summary rather than part of it:
                // everything above is the shape of the term, and this is the register the
                // shape was read from.
                item { AllClassesRow(count = data.allMeetings.size, onClick = onOpenAllClasses) }
            }
        }
    }
}

/**
 * Nothing to show, and why.
 *
 * Scrollable so the pull gesture still works on it: an empty state you cannot pull on
 * is a dead end, and pulling is one of the two ways to fill it.
 */
@Composable
private fun ClassCountsEmpty(ui: ClassStatsUiState) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ui.error?.let { Gap(8); InlineError(it, onRetry = null) }
        EmptyState(
            title = if (ui.loading) "Fetching class attendance" else "No class attendance yet",
            body = "These figures come from the portal's register for each class. " +
                "Pull down or tap refresh to fetch them.",
        )
    }
}

/** The way through to every class, with the size of the list stated up front. */
@Composable
private fun AllClassesRow(count: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "List all classes",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (count == 1) {
                        "The one class on record"
                    } else {
                        "All $count classes by date, filtered by subject if you like"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeadlineCard(data: ClassStats) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    data.classPercent.asPercentLabel1dp(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Column(Modifier.padding(start = 12.dp, bottom = 6.dp)) {
                    Text("Class attendance", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "${data.meetings} classes counted",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Gap(12)
            // Present and absent as one proportional bar, the same component the
            // subject screen uses for a student's own mix.
            AttendanceMixChart(
                present = data.totalPresent,
                dutyLeave = 0,
                absent = data.totalAbsent,
            )
            Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Figure("Present", data.totalPresent.toString())
                Figure("Absent", data.totalAbsent.toString())
                Figure("You", data.yourPercent.asPercentLabel1dp())
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The two ends of the subject list, named rather than left to be spotted. */
@Composable
private fun StrongestWeakest(data: ClassStats) {
    val strongest = data.strongest
    val weakest = data.weakest
    if (strongest == null || weakest == null || strongest.courseId == weakest.courseId) return
    val colors = LocalStatusColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        ExtremeCard("Best attended", strongest, colors.safe, Modifier.weight(1f))
        ExtremeCard("Least attended", weakest, colors.risk, Modifier.weight(1f))
    }
}

@Composable
private fun ExtremeCard(
    label: String,
    course: CourseClassStats,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(2)
            Text(
                course.classPercent.asPercentLabel1dp(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accent,
            )
            Text(
                course.courseName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CourseStatRow(course: CourseClassStats) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    course.courseName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${course.meetings} classes · you ${course.yourPercent.asPercentLabel1dp()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(6)
                AttendanceBar(
                    fraction = (course.classPercent / 100.0).toFloat(),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }
            Text(
                course.classPercent.asPercentLabel1dp(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MeetingList(meetings: List<MeetingStat>) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            meetings.forEach { meeting ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            // Weekday included, as in every other class list: these
                            // rows are read to recognise a day, not to look one up.
                            dateWithWeekday(meeting.dateKey, meeting.displayDate) +
                                " · " + meeting.sessionName,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Text(
                            meeting.courseName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${meeting.present} / ${meeting.marked}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
