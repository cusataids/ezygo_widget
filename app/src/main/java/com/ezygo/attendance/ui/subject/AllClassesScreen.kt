package com.ezygo.attendance.ui.subject

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.ClassCountsStatus
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.asPercentLabel1dp
import com.ezygo.attendance.ui.common.dateWithWeekday
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * Every class the cohort has a register for, newest first.
 *
 * The statistics page answers "how is the class doing"; this answers "what happened on
 * the 14th", which is a different question and a much longer answer. Separating them
 * keeps the summary a summary — a hundred rows under the trend line would bury it.
 *
 * The filter is by subject rather than by anything else because that is the cut a
 * student actually makes: a run of thin Thursdays matters when they are all the same
 * lecture and means nothing when they are six different ones.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllClassesScreen(
    onBack: () -> Unit,
    viewModel: ClassStatsViewModel = hiltViewModel(),
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()

    // Course ids rather than indices, so a refresh that reorders the subject list
    // leaves the selection pointing at the same subjects.
    var selected by rememberSaveable { mutableStateOf(emptySet<Int>()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All classes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selected.isNotEmpty()) {
                        TextButton(onClick = { selected = emptySet() }) { Text("Show all") }
                    }
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
        if (data == null || data.allMeetings.isEmpty()) {
            EmptyState(
                title = if (ui.loading) "Fetching class attendance" else "No classes recorded yet",
                body = "These rows come from the portal's register for each class. " +
                    "Pull down or tap refresh to fetch them.",
                modifier = Modifier.fillMaxSize(),
            )
            return@PullToRefreshBox
        }

        // An empty selection means everything, not nothing. Nobody opens a list of
        // classes to see none of them, and it saves a "select all" that every reader
        // would have to use before the screen said anything.
        val shown = remember(data, selected) {
            if (selected.isEmpty()) {
                data.allMeetings
            } else {
                data.allMeetings.filter { it.courseId in selected }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                ClassCountsStatus(
                    fetchedAtEpochMs = ui.fetchedAtEpochMs,
                    loading = ui.loading,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                )
            }
            item {
                SubjectFilter(
                    courses = data.courses,
                    selected = selected,
                    onToggle = { id ->
                        selected = if (id in selected) selected - id else selected + id
                    },
                )
            }
            item {
                Text(
                    countLabel(shown.size, data.allMeetings.size, selected.isNotEmpty()),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (shown.isEmpty()) {
                item {
                    EmptyState(
                        title = "Nothing in that filter",
                        body = "None of the chosen subjects have a register yet.",
                    )
                }
            }

            items(shown, key = { it.dateKey + "-" + it.courseId + "-" + it.sessionName }) {
                ClassRow(it)
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }
        }
        }
    }
}

private fun countLabel(shown: Int, total: Int, filtered: Boolean): String = when {
    !filtered && total == 1 -> "1 class"
    !filtered -> "$total classes"
    shown == 1 -> "1 of $total classes"
    else -> "$shown of $total classes"
}

/**
 * The subjects, as chips that can each be on or off.
 *
 * A horizontal strip rather than a dropdown: with a handful of subjects the whole
 * choice fits on screen, and a filter you can see the state of is one you trust.
 */
@Composable
private fun SubjectFilter(
    courses: List<CourseClassStats>,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "Filter by subject",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Gap(6)
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            courses.forEach { course ->
                val on = course.courseId in selected
                FilterChip(
                    selected = on,
                    onClick = { onToggle(course.courseId) },
                    label = {
                        Text(
                            course.courseName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}

/** One class: when it met, which subject it was, and how full the room was. */
@Composable
private fun ClassRow(meeting: MeetingStat) {
    val colors = LocalStatusColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                dateWithWeekday(meeting.dateKey, meeting.displayDate) +
                    " · " + meeting.sessionName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                meeting.courseName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ) {
            Column(
                Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    meeting.percent.asPercentLabel1dp(),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    // Banded against the same thresholds the rest of the app uses, so
                    // a thin class looks thin here too rather than only in the stats.
                    color = when {
                        meeting.percent >= SAFE_PERCENT -> colors.safe
                        meeting.percent >= WATCH_PERCENT -> colors.watch
                        else -> colors.risk
                    },
                )
                Text(
                    "${meeting.present} of ${meeting.marked}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Where a class stops being well attended.
 *
 * Deliberately not the student's own attendance target: that setting is about one
 * person's shortage, and reading a room against it would say a lecture two thirds
 * full had failed. These are the thresholds for a cohort.
 */
private const val SAFE_PERCENT = 75.0
private const val WATCH_PERCENT = 50.0
