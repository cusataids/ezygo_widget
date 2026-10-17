package com.ezygo.attendance.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.data.repo.HiddenItem
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.CalendarEvent
import com.ezygo.attendance.domain.model.PendingItem
import com.ezygo.attendance.domain.model.EventSource
import com.ezygo.attendance.domain.model.Dashboard
import com.ezygo.attendance.ui.common.AttendanceChip
import com.ezygo.attendance.ui.common.BandChip
import com.ezygo.attendance.ui.common.CourseAttendanceRow
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.HomeSkeleton
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.WaterBall
import com.ezygo.attendance.ui.common.currentLocale
import com.ezygo.attendance.ui.common.dueLabel
import com.ezygo.attendance.ui.miniapps.MiniApp
import com.ezygo.attendance.ui.miniapps.MiniAppsRow
import com.ezygo.attendance.ui.common.asPercentLabel1dp
import com.ezygo.attendance.ui.common.asPercentLabel2dp
import com.ezygo.attendance.ui.common.relativeTime
import com.ezygo.attendance.ui.common.rememberCountdown
import com.ezygo.attendance.ui.theme.LocalStatusColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCourseClick: (Int) -> Unit,
    /** Opens the calendar on a specific day, or on today when null. */
    onOpenCalendar: (LocalDate?) -> Unit,
    onOpenMiniApp: (MiniApp) -> Unit,
    /** Opens every subject as tabs, from the average the card is showing. */
    onOpenCourseTabs: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val upcomingEvents by viewModel.upcomingEvents.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val hidden by viewModel.hidden.collectAsStateWithLifecycle()

    // What the confirmation is about, if anything. Held here rather than in the row so
    // one dialog serves every row on the screen.
    var pendingHide by remember { mutableStateOf<HideRequest?>(null) }
    var showingHidden by remember { mutableStateOf(false) }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    PullToRefreshBox(
        // Only a pull spins the indicator. A background refresh leaves the cached
        // dashboard exactly as it is and swaps the numbers in when it lands.
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val data = dashboard
        when {
            // The skeleton is for having nothing to show. Once a snapshot exists it
            // stays on screen through every later refresh, however that refresh was
            // triggered — replacing real content with a skeleton would be a step
            // backwards for the reader.
            data == null || (!data.hasSnapshot && isLoading) ->
                HomeSkeleton(Modifier.fillMaxSize())

            else -> HomeContent(
                dashboard = data,
                upcomingEvents = upcomingEvents,
                pending = pending,
                hidden = hidden,
                error = error,
                onRetry = viewModel::refresh,
                onCourseClick = onCourseClick,
                onOpenCalendar = onOpenCalendar,
                onOpenMiniApp = onOpenMiniApp,
                onOpenCourseTabs = onOpenCourseTabs,
                onHideEvent = { id, title ->
                    pendingHide = HideRequest(id, title, isEvent = true)
                },
                onHidePending = { id, title ->
                    pendingHide = HideRequest(id, title, isEvent = false)
                },
                onOpenHidden = { showingHidden = true },
            )
        }
    }

    if (showingHidden && hidden.isNotEmpty()) {
        HiddenDialog(
            hidden = hidden,
            onRestore = viewModel::restore,
            onRestoreAll = {
                viewModel.restoreAll()
                showingHidden = false
            },
            onDismiss = { showingHidden = false },
        )
    }

    pendingHide?.let { request ->
        HideConfirmationDialog(
            request = request,
            onConfirm = {
                if (request.isEvent) {
                    viewModel.hideEvent(request.id, request.title)
                } else {
                    viewModel.hidePending(request.id, request.title)
                }
                pendingHide = null
            },
            onDismiss = { pendingHide = null },
        )
    }
}

/** What is about to be hidden, while the confirmation is on screen. */
private data class HideRequest(val id: String, val title: String, val isEvent: Boolean)

/**
 * Asks before hiding, and says where the thing goes.
 *
 * Hiding is reversible, but only if the reader knows that — without a word about it the
 * button reads as a delete. One line is enough to say it; the rest was reassurance
 * nobody asked for.
 */
@Composable
private fun HideConfirmationDialog(
    request: HideRequest,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hide \"" + request.title + "\"?") },
        text = {
            Text(
                "You can bring it back from Hidden tasks & events.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Hide") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * What has been hidden, behind a button.
 *
 * A dialog rather than a section: this is a place to go looking on the rare occasion
 * something is missing, and a permanent list of things the reader has already said they
 * do not want to see is the opposite of what they asked for.
 */
@Composable
private fun HiddenDialog(
    hidden: List<HiddenItem>,
    onRestore: (String) -> Unit,
    onRestoreAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Hidden tasks & events") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                hidden.forEach { entry ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                entry.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                if (entry.isEvent) "Event" else "Task",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onRestore(entry.key) }) { Text("Show") }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (hidden.size > 1) {
                TextButton(onClick = onRestoreAll) { Text("Show all") }
            }
        },
    )
}

@Composable
private fun HomeContent(
    dashboard: Dashboard,
    upcomingEvents: List<CalendarEvent>,
    pending: List<PendingItem>,
    hidden: List<HiddenItem>,
    error: String?,
    onRetry: () -> Unit,
    onCourseClick: (Int) -> Unit,
    onOpenCalendar: (LocalDate?) -> Unit,
    onOpenMiniApp: (MiniApp) -> Unit,
    onOpenCourseTabs: () -> Unit,
    onHideEvent: (String, String) -> Unit,
    onHidePending: (String, String) -> Unit,
    onOpenHidden: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (error != null) {
            item { InlineError(error, onRetry = onRetry) }
        }

        // Above the attendance itself, alongside the calendar and notifications
        // in the bar: these are the things the app can do rather than the thing it
        // is about, and they are what a reader comes looking for on purpose.
        item { SectionHeader("Mini apps") }
        item { MiniAppsRow(onOpen = onOpenMiniApp) }

        item { AverageCard(dashboard, onClick = onOpenCourseTabs) }
        item {
            // Under the card rather than over it. It is a footnote to the figure —
            // when the figure was last true — and a footnote set above its subject
            // reads as a heading for it. Still kept near the number rather than at
            // the foot of the page, where it was before and where nobody saw it.
            Text(
                relativeTime(dashboard.lastUpdatedEpochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // What is still owed, above the attendance detail: a deadline is the thing
        // most likely to need acting on today.
        if (pending.isNotEmpty()) {
            item { SectionHeader("Pending tasks", trailing = "${pending.size}") }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(vertical = 4.dp)) {
                        pending.forEach { item ->
                            // The whole row opens the tasks app rather than a small
                            // chevron doing it: everything here lives in that one
                            // place, so there is only one thing a tap can mean.
                            PendingRow(
                                item = item,
                                onClick = { onOpenMiniApp(MiniApp.TASKS) },
                                onHide = { onHidePending(item.id, item.title) },
                            )
                        }
                    }
                }
            }
        }

        if (upcomingEvents.isNotEmpty()) {
            item {
                SectionHeader("Coming up", trailing = "Calendar")
            }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        upcomingEvents.forEachIndexed { index, event ->
                            // Each row opens the calendar on its own day, rather
                            // than the card as a whole opening it on today: the
                            // day the event is on is what the reader just tapped.
                            UpcomingEventRow(
                                event = event,
                                onClick = { onOpenCalendar(event.startDate()) },
                                onHide = { onHideEvent(event.id, event.title) },
                            )
                            if (index != upcomingEvents.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }
            }
        }

        if (dashboard.courses.isNotEmpty()) {
            // Worst-first ordering already puts anything in shortage at the top, so a
            // separate "needs attention" block would just repeat these same rows.
            // The chevron on each row carries the "tappable" cue on its own.
            item { SectionHeader("Subjects", trailing = "${dashboard.courses.size}") }
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                ) {
                    Column {
                        dashboard.courses.forEachIndexed { index, course ->
                            CourseAttendanceRow(course, dashboard.settings.targetPercent) {
                                onCourseClick(course.courseId)
                            }
                            if (index != dashboard.courses.lastIndex) {
                                HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }
            }
        }

        if (dashboard.coursesWithoutRecords.isNotEmpty()) {
            item {
                // Enrolled but never marked. Stated explicitly so it is not mistaken
                // for a subject sitting at zero percent.
                Text(
                    "No classes recorded yet: " +
                        dashboard.coursesWithoutRecords.joinToString(", ") { it.name },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (dashboard.isEmpty) {
            item {
                EmptyState(
                    title = "No attendance data",
                    body = "Nothing has been recorded for " +
                        dashboard.period.displayLabel +
                        " yet. Pull down to refresh, or change the period in your profile.",
                )
            }
        }

        if (dashboard.recent.isNotEmpty()) {
            item { SectionHeader("Recent") }
            items(dashboard.recent, key = { it.dateKey + "-" + it.sessionId }) { RecentRow(it) }
        }

        // Hiding must never be a one-way door, but the way back is a button rather than
        // a standing list: a permanent block of things the reader has already said they
        // do not want to see is the opposite of hiding them.
        if (hidden.isNotEmpty()) {
            item {
                OutlinedButton(onClick = onOpenHidden, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text("  Hidden tasks & events (${hidden.size})")
                }
            }
        }

    }
}

/**
 * One line of the "Coming up" list.
 *
 * The source is stated on every row rather than only in the calendar screen: a
 * student glancing at Home must not mistake their own reminder for something the
 * institution published.
 */
@Composable
private fun UpcomingEventRow(event: CalendarEvent, onClick: () -> Unit, onHide: () -> Unit) {
    val locale = currentLocale()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(event.source.eventColor()),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                event.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            Gap(2)
            Text(
                listOfNotNull(
                    event.whenLabel(locale),
                    event.location,
                    if (event.source == EventSource.LOCAL) "On this device" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // How soon, next to what and when. Kept to one short phrase in the accent
        // colour: the date beside it already says the day, and this is only here to
        // answer "do I need to move". An all-day event has no hour to count down to,
        // so it gets none rather than a countdown to midnight.
        val countdown = if (event.isAllDay) {
            null
        } else {
            rememberCountdown(event.start.toEpochMilli())
        }
        countdown?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        HideButton(what = event.title, onHide = onHide)
    }
}

/**
 * Takes one row off the home screen.
 *
 * Hiding and nothing else: the event or task stays in its own app, keeps syncing, and
 * still raises its reminder. This screen is a summary, and what belongs in a summary is
 * the reader's to decide.
 */
@Composable
private fun HideButton(what: String, onHide: () -> Unit) {
    IconButton(onClick = onHide) {
        Icon(
            Icons.Default.VisibilityOff,
            contentDescription = "Hide " + what + " from the home screen",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun EventSource.eventColor(): Color = when (this) {
    EventSource.BATCH -> MaterialTheme.colorScheme.primary
    // Never reached from here — the home list excludes the academic feed — but the
    // colour matches the calendar screen so the two can never disagree.
    EventSource.ACADEMIC -> MaterialTheme.colorScheme.secondary
    EventSource.LOCAL -> MaterialTheme.colorScheme.tertiary
}

/** "Today", "Tomorrow", then a date — the way a person actually reads a deadline. */
private fun CalendarEvent.whenLabel(locale: Locale): String {
    val date = startDate()
    val today = LocalDate.now()
    val day = when (date) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
    }
    val time = startTime()?.format(
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale),
    )
    return if (time == null) day else "$day, $time"
}

/**
 * The semester average, and the way in to the subjects behind it.
 *
 * Tappable because the number is an average over every subject, and "which subjects?"
 * is the question it immediately raises. The whole card is the target rather than a
 * chevron in the corner: it is one idea, and a reader aiming at a number should not
 * have to find a separate control to act on it.
 */
@Composable
private fun AverageCard(dashboard: Dashboard, onClick: () -> Unit) {
    val stats = dashboard.overall
    // Headline is the average across subjects; the counts below are the aggregate
    // totals, which are still the honest answer to "how many classes have I had".
    val average = dashboard.averagePercent
    val target = dashboard.settings.targetPercent
    val statusColors = LocalStatusColors.current
    // Above the target is the one thing this number is read for, so it says so in
    // colour rather than leaving the band to imply it: the band is a three-way risk
    // scale, and "at or above target" is a yes-or-no question.
    val color = if (average != null && average >= target) {
        statusColors.safe
    } else {
        statusColors.forBand(dashboard.averageBand)
    }
    val belowTarget = dashboard.subjectsAtRisk.size

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The figure sits inside its own gauge instead of on top of a
                // full-width bar. The old card was four stacked bands - number, bar,
                // counts, sentence - all the same width and the same weight, which is
                // what made it read as a form rather than as a headline. A ring makes
                // the percentage one object instead of two, and frees the width beside
                // it for everything that qualifies the number.
                WaterBall(
                    percent = average,
                    color = color,
                    modifier = Modifier.size(width = BALL_WIDTH, height = BALL_HEIGHT),
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Average attendance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        dashboard.period.displayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(6)
                    Text(
                        average.asPercentLabel1dp(),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = color,
                        maxLines = 1,
                    )
                    Gap(4)
                    BandChip(dashboard.averageBand)
                    Gap(6)
                    Text(
                        // The other way of counting, kept deliberately quiet.
                        //
                        // The headline treats every subject equally; this pools every
                        // class together, so a subject with twice the classes pulls it
                        // twice as hard. They answer different questions and they
                        // disagree often, which is the whole reason to show both - but
                        // only one of them is the figure this card is about.
                        //
                        // Uncoloured on purpose: colour here means "against your
                        // target", and two coloured percentages would read as two
                        // verdicts rather than one verdict and one footnote.
                        "Overall : " + stats.percentage.asPercentLabel2dp(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "See every subject",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Gap(14)
            // The tally as framed figures rather than runs of bare text. Equal weights
            // keep them on one line at any width, and the frames are what stop the row
            // reading as a sentence that happens to contain numbers.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("Attended", stats.positive, Modifier.weight(1f))
                StatTile("Absent", stats.absent, Modifier.weight(1f))
                if (stats.dutyLeave > 0) {
                    StatTile("Duty leave", stats.dutyLeave, Modifier.weight(1f))
                }
                StatTile("Total", stats.total, Modifier.weight(1f))
            }

            if (dashboard.courses.isNotEmpty()) {
                Gap(12)
                Text(
                    // Per-subject recovery advice lives on the subject rows; here the
                    // useful summary is simply how many subjects are in trouble.
                    when (belowTarget) {
                        0 -> "Every subject is at or above $target%"
                        1 -> "1 subject is below $target%"
                        else -> "$belowTarget subjects are below $target%"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = if (belowTarget > 0) FontWeight.Medium else FontWeight.Normal,
                    color = if (belowTarget > 0) color else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One figure from the tally, framed so the row reads as a set rather than a sentence. */
@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * The ball's footprint on the card.
 *
 * Square for the ball itself, with a strip of height above it for the water to fall
 * through on the way in. Nothing is drawn in that strip once the pour is over.
 */
private val BALL_WIDTH = 84.dp
private val BALL_HEIGHT = 104.dp

@Composable
private fun RecentRow(record: AttendanceRecord) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                record.displayDate + " · " + record.sessionName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(record.courseName, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        AttendanceChip(record.attendanceCode, record.attendanceName, record.isPositive)
    }
}

/** One outstanding item: what it is, when it is due, and where it came from. */
@Composable
private fun PendingRow(item: PendingItem, onClick: () -> Unit, onHide: () -> Unit) {
    val now = System.currentTimeMillis()
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val overdue = item.isOverdue(now)
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                // Overdue is the whole row's condition, not a detail of its date line:
                // it is the thing the reader has to notice from across the screen.
                fontWeight = if (overdue) FontWeight.SemiBold else FontWeight.Normal,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                // Named, not only coloured: red alone asks the reader to know what red
                // means here, and a deadline gone by is worth one plain word.
                text = listOfNotNull(
                    "Overdue".takeIf { overdue },
                    item.dueEpochMs?.let { dueLabel(it, item.isAllDay) } ?: "No deadline",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = if (overdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Text(
            if (item.fromTasks) "Synced" else "Yours",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HideButton(what = item.title, onHide = onHide)
    }
}
