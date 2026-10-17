package com.ezygo.attendance.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.ClassMeeting
import com.ezygo.attendance.domain.model.ClassOverview
import com.ezygo.attendance.ui.common.AttendanceChip
import com.ezygo.attendance.ui.common.AttendanceMixChart
import com.ezygo.attendance.ui.common.AttendanceTrendChart
import com.ezygo.attendance.ui.common.ClassCountsStatus
import com.ezygo.attendance.ui.common.CourseSummaryCard
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.WideDialogProperties
import com.ezygo.attendance.ui.common.dateWithWeekday
import com.ezygo.attendance.ui.common.recordDate
import com.ezygo.attendance.ui.common.recordWeekday
import com.ezygo.attendance.ui.common.wideDialogModifier
import com.ezygo.attendance.ui.theme.LocalStatusColors
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDetailScreen(
    onBack: () -> Unit,
    onOpenCourseInfo: (Int) -> Unit,
    viewModel: SubjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val classCounts by viewModel.classCounts.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state?.course?.courseName ?: "Subject", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val data = state
        if (data == null) {
            EmptyState(
                title = "Subject unavailable",
                body = "This subject is not part of the currently selected period.",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
            return@Scaffold
        }

        SubjectDetailContent(
            data = data,
            classCounts = classCounts,
            onOpenClassOverview = viewModel::openClassOverview,
            onRefreshClassOverview = viewModel::refreshClassOverview,
            onOpenCourseInfo = onOpenCourseInfo,
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/**
 * One subject's attendance, in full.
 *
 * Split out from [SubjectDetailScreen] so the course-tabs view can show exactly this
 * and not a second rendering of it. The screen supplies the top bar and the "subject
 * unavailable" case; everything a reader actually looks at is here, so the two routes
 * into a subject cannot drift apart.
 */
@Composable
internal fun SubjectDetailContent(
    data: SubjectDetailUiState,
    classCounts: ClassCountsUi,
    onOpenClassOverview: () -> Unit,
    onRefreshClassOverview: () -> Unit,
    onOpenCourseInfo: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val stats = data.course.stats
    val color = LocalStatusColors.current.forBand(stats.band)

    // Held here rather than by either screen, so the single-subject view and the
    // course-tabs pager both get the dialog from the one place the button lives.
    var showingClass by rememberSaveable(data.course.courseId) { mutableStateOf(false) }

    // Opening the dialog is what asks for the cohort report — nothing else in the
    // app does any more. The repository decides whether that means a request or the
    // cache; from here it is simply "the reader wants this now".
    LaunchedEffect(showingClass) {
        if (showingClass) onOpenClassOverview()
    }

    if (showingClass) {
        ClassOverviewDialog(
            courseName = data.course.courseName,
            overview = data.classOverview,
            counts = classCounts,
            onRefresh = onRefreshClassOverview,
            onDismiss = { showingClass = false },
        )
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            // The same card the attendance list shows, so the way in looks like
            // where it leads. The name is left off here: the top app bar has it.
            CourseSummaryCard(course = data.course, targetPercent = data.settings.targetPercent)
        }

        item {
            CourseDetailsRow(onClick = { onOpenCourseInfo(data.course.courseId) })
        }

        // Directly under the student's own figures, because it answers the question
        // those figures raise: 68% is a different thing when the class average is 70
        // than when it is 95.
        //
        // Shown whether or not the figures are in yet. It used to appear only once
        // they were, which was fine while every refresh fetched them; now that the
        // report is only read when this row is tapped, hiding it until the data
        // arrives would mean it never arrives.
        item {
            ClassOverviewRow(
                hasData = data.classOverview.hasData,
                onClick = { showingClass = true },
            )
        }

        if (stats.hasData) {
            item { SectionHeader("Attendance mix") }
            item { AttendanceMixChart(stats.present, stats.dutyLeave, stats.absent) }

            if (data.trend.size >= 3) {
                item { SectionHeader("Trend") }
                item { AttendanceTrendChart(actual = data.trend, lineColor = color) }
            }
        }

        item { SectionHeader("History", trailing = "${data.history.size}") }
        if (data.history.isEmpty()) {
            item {
                EmptyState(
                    title = "No attendance data",
                    body = "No classes have been recorded for this subject yet.",
                )
            }
        }
        items(data.history, key = { it.dateKey + "-" + it.sessionId }) { HistoryRow(it) }
    }
}

/**
 * The way through to everything about this course that is not attendance: syllabus,
 * modules, outcomes and the people in it. Reached from here as well as from the
 * courses list, because this screen is where a student is already thinking about
 * the subject.
 */
@Composable
private fun CourseDetailsRow(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Course details",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Gap(2)
                Text(
                    "Syllabus, modules and classmates",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun HistoryRow(record: AttendanceRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(96.dp)) {
            Text(
                record.recordDate(),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                // Derived from the same date printed above it, so the two can never
                // disagree. The feed's own day name is a fallback only: it is optional
                // in the payload and arrived empty often enough that this row used to
                // show no day at all.
                record.recordWeekday(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            record.sessionName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        Box(Modifier.weight(1f))
        AttendanceChip(record.attendanceCode, record.attendanceName, record.isPositive)
    }
}

/**
 * The way in to how the rest of the class has been doing.
 *
 * Phrased as the question it answers rather than as the data it holds. "Class
 * attendance report" describes a table; "How is the class doing?" is what the student
 * is actually wondering when their own figure looks worse than they expected.
 */
@Composable
private fun ClassOverviewRow(hasData: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "How is the class doing?",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (hasData) {
                        "Who turned up, day by day, and how that compares with you"
                    } else {
                        // Says what tapping will do, rather than pretending to a
                        // figure it does not have.
                        "Tap to fetch the class register for this course"
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

/**
 * The cohort's attendance for one course: the totals, then every meeting.
 *
 * The summary leads because it is the answer; the day list is the working behind it,
 * and is there for the one day the reader wants to check rather than to be read
 * through. Newest first, matching the history list on the screen underneath.
 */
@Composable
private fun ClassOverviewDialog(
    courseName: String,
    overview: ClassOverview,
    counts: ClassCountsUi,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalStatusColors.current
    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("How is the class doing?", maxLines = 2, modifier = Modifier.weight(1f))
                // The dialog's own refresh. There is no pull gesture inside a
                // dialog, and this is the only screen whose data does not arrive by
                // itself, so the way to ask for it has to be on it.
                IconButton(onClick = onRefresh, enabled = !counts.loading) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh class attendance",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    courseName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(4)
                ClassCountsStatus(
                    fetchedAtEpochMs = counts.fetchedAtEpochMs,
                    loading = counts.loading,
                )
                counts.error?.let {
                    Gap(6)
                    InlineError(it, onRetry = onRefresh)
                }
                Gap(10)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                overview.averagePercent?.let {
                                    String.format(Locale.getDefault(), "%.0f%%", it)
                                } ?: "--",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "  class attendance",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                        Gap(8)
                        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            Count("Classes", overview.recorded.size)
                            Count("Present", overview.totalPresent)
                            Count("Absent", overview.totalAbsent)
                            overview.classSize?.let { Count("On roll", it) }
                        }
                    }
                }

                overview.quietest?.let { quiet ->
                    Gap(8)
                    Text(
                        // One meeting is worth naming: the emptiest. It is where a
                        // cancelled class or a clashing event usually shows up.
                        "Thinnest day: " +
                            dateWithWeekday(quiet.dateKey, quiet.displayDate, quiet.dayName) +
                            ", session ${quiet.sessionName} — " +
                            "${quiet.present} of ${quiet.marked}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Gap(14)
                Text(
                    "Every class",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Gap(4)
                overview.meetings.forEach { meeting ->
                    ClassMeetingRow(meeting, colors.safe, colors.risk)
                }
            }
        },
    )
}

/** A figure with its name under it, as the class overview's four totals are shown. */
@Composable
private fun Count(label: String, value: Int) {
    Column {
        Text(
            "$value",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ClassMeetingRow(
    meeting: ClassMeeting,
    attendedColor: Color,
    missedColor: Color,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                // Weekday included: a class list is read to find a particular day,
                // and "the Tuesday I missed" is how that day is remembered.
                dateWithWeekday(meeting.dateKey, meeting.displayDate, meeting.dayName) +
                    " · " + meeting.sessionName,
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                // The student's own mark beside the room's, because the comparison is
                // the reason to open this at all.
                if (meeting.attended) "You were present" else "You were absent",
                style = MaterialTheme.typography.labelSmall,
                color = if (meeting.attended) attendedColor else missedColor,
            )
        }
        Text(
            if (meeting.marked == 0) {
                // Not "0 present": nobody was marked, which is a missing register
                // rather than an empty room.
                "Not marked"
            } else {
                "${meeting.present} / ${meeting.marked}"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (meeting.marked == 0) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}
