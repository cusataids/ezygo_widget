package com.ezygo.attendance.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.AttendanceDay
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.DayStatus
import com.ezygo.attendance.domain.model.SavedCourse
import com.ezygo.attendance.domain.model.ScheduleSlot
import com.ezygo.attendance.domain.model.SessionCount
import com.ezygo.attendance.domain.model.TimetableOverride
import com.ezygo.attendance.domain.model.TimetableDay
import com.ezygo.attendance.ui.common.AttendanceChip
import com.ezygo.attendance.ui.common.ConfirmDeleteDialog
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.HomeSkeleton
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.ListScrollbar
import com.ezygo.attendance.ui.common.ScrollToTopButton
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.formatDayHeader
import com.ezygo.attendance.ui.theme.LocalStatusColors
import com.ezygo.attendance.ui.theme.StatusColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * Two halves: what is coming up over the next week, and what has actually been
 * recorded so far. Both are phone-shaped vertical lists rather than a wide
 * date x session grid, which no phone can show legibly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(viewModel: TimetableViewModel = hiltViewModel()) {
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val timetable by viewModel.timetable.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val timetableError by viewModel.timetableError.collectAsStateWithLifecycle()

    val gridView by viewModel.gridView.collectAsStateWithLifecycle()
    val courses by viewModel.courses.collectAsStateWithLifecycle()
    val codeErrors by viewModel.codeErrors.collectAsStateWithLifecycle()
    val courseChoices by viewModel.courseChoices.collectAsStateWithLifecycle()
    val periods by viewModel.periods.collectAsStateWithLifecycle()
    val overrides by viewModel.overrides.collectAsStateWithLifecycle()

    var mapping by remember { mutableStateOf(false) }
    var overriding by remember { mutableStateOf(false) }
    // A tapped grid cell: one holds what to show, the other what to fill in.
    var openSlot by remember { mutableStateOf<Pair<TimetableDay, ScheduleSlot>?>(null) }
    var fillSlot by remember { mutableStateOf<Pair<TimetableDay, ScheduleSlot>?>(null) }
    // From the list rather than the grid, so day and period are asked for. The value
    // is the class being corrected, or null for one being added.
    var editingOverride by remember { mutableStateOf<TimetableOverride?>(null) }
    var addingOverride by remember { mutableStateOf(false) }
    var confirmingRemoveSlot by remember { mutableStateOf<Pair<TimetableDay, ScheduleSlot>?>(null) }
    var confirmingRemoveOverride by remember { mutableStateOf<TimetableOverride?>(null) }
    var confirmingForget by remember { mutableStateOf<SavedCourse?>(null) }
    var selectedDayIndex by remember { mutableStateOf(0) }
    var selectedRecord by remember { mutableStateOf<AttendanceRecord?>(null) }
    var historyView by remember { mutableStateOf(HistoryView.LIST) }
    var historyFilter by remember { mutableStateOf(HistoryFilter.ALL) }
    // Hoisted out of the list so the scrollbar and the way back to the top can read
    // it. A term of history runs to a few hundred rows, and this is the only screen
    // in the app long enough to need either.
    val historyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val days = timetable?.days.orEmpty()
    // Keep the selection valid if the week rolls over while the screen is open.
    LaunchedEffect(days.size) {
        if (selectedDayIndex >= days.size) selectedDayIndex = 0
    }

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        val attendance = dashboard

        // Filtered here rather than inside the list so it is recomputed only when
        // something it depends on changes. The calendar is deliberately left whole:
        // its point is the shape of the month, which a grid missing half its days
        // would misrepresent.
        val historyDays = remember(attendance, historyView, historyFilter) {
            val all = attendance?.days.orEmpty()
            if (historyView == HistoryView.LIST) all.filteredBy(historyFilter) else all
        }

        if (attendance == null && timetable == null) {
            HomeSkeleton(Modifier.fillMaxSize())
            return@PullToRefreshBox
        }

        LazyColumn(
            state = historyListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            error?.let { message ->
                item { InlineError(message, onRetry = viewModel::refresh) }
            }

            // ---- upcoming week ------------------------------------------

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(
                        "Next 7 days",
                        trailing = timetable?.subgroupName,
                        modifier = Modifier.weight(1f),
                    )
                    TimetableMenu(
                        gridView = gridView,
                        onToggleView = { viewModel.setGridView(!gridView) },
                        onMapCourses = { mapping = true },
                        onOverride = { overriding = true },
                    )
                }
            }

            timetableError?.let { message ->
                item { InlineError(message, onRetry = viewModel::refresh) }
            }

            if (days.isEmpty() && timetableError == null) {
                item {
                    EmptyState(
                        title = "No schedule yet",
                        body = "Pull down to load your class timetable for the coming week.",
                    )
                }
            }

            if (days.isNotEmpty()) {
                if (gridView) {
                    // The whole week at once. A cell with a class opens a card with the
                    // code, the name and the teacher — the grid stays where it is,
                    // because being thrown into the day list to read one period is a
                    // worse answer than a card over the grid you were already reading.
                    item(key = "timetable-grid") {
                        TimetableGrid(
                            days = days,
                            onSlotClick = { day, slot -> openSlot = day to slot },
                            onEmptyClick = { day, slot -> fillSlot = day to slot },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                } else {
                    item {
                        DayStrip(
                            days = days,
                            selectedIndex = selectedDayIndex.coerceIn(0, days.lastIndex),
                            onSelect = { selectedDayIndex = it },
                        )
                    }
                    item {
                        DaySchedule(day = days[selectedDayIndex.coerceIn(0, days.lastIndex)])
                    }
                }
            }

            // ---- recorded attendance ------------------------------------

            if (attendance != null) {
                item { Gap(4) }
                item {
                    SectionHeader(
                        "Recorded attendance",
                        // Counts what is actually below it, so a filtered list does
                        // not sit under a heading claiming days it is not showing.
                        trailing = "${historyDays.size} days",
                    )
                }

                if (attendance.days.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Nothing recorded",
                            body = "No classes have been marked for " +
                                attendance.period.displayLabel + " yet.",
                        )
                    }
                } else {
                    item(key = "history-view-toggle") {
                        HistoryViewToggle(
                            selected = historyView,
                            onSelect = { historyView = it },
                        )
                    }

                    when (historyView) {
                        HistoryView.LIST -> {
                            item(key = "history-filter") {
                                HistoryFilterRow(
                                    selected = historyFilter,
                                    onSelect = { historyFilter = it },
                                )
                            }

                            if (historyDays.isEmpty()) {
                                item(key = "history-filter-empty") {
                                    EmptyState(
                                        title = "No matching classes",
                                        body = "Nothing in " +
                                            attendance.period.displayLabel +
                                            " is marked " +
                                            historyFilter.label().lowercase() + ".",
                                    )
                                }
                            } else {
                                for (day in historyDays) {
                                    item(key = "history-" + day.dateKey) {
                                        HistoryDayBlock(
                                            day = day,
                                            onRecordClick = { selectedRecord = it },
                                        )
                                    }
                                }
                            }
                        }

                        HistoryView.CALENDAR ->
                            item(key = "history-calendar") {
                                HistoryCalendar(
                                    days = attendance.days,
                                    onRecordClick = { selectedRecord = it },
                                )
                            }
                    }
                }
            }
        }

        ListScrollbar(historyListState)
        ScrollToTopButton(
            state = historyListState,
            onClick = { scope.launch { historyListState.animateScrollToItem(0) } },
            // The week ahead takes most of a screen on its own, so the first handful
            // of items are not yet "scrolled down" in any sense the reader would
            // recognise. Waiting until the recorded history is what is on screen.
            appearAfterItems = 10,
        )
    }

    selectedRecord?.let { record ->
        // Asked for as the dialog opens and dropped as it closes, so nothing is
        // observed for a session nobody is looking at.
        LaunchedEffect(record.dateKey, record.sessionId) {
            viewModel.loadSessionCount(record.dateKey, record.sessionId)
        }
        val sessionCount by viewModel.sessionCount.collectAsStateWithLifecycle()
        val countsFetchedAt by viewModel.classCountsFetchedAt.collectAsStateWithLifecycle()
        val countsLoading by viewModel.classCountsLoading.collectAsStateWithLifecycle()
        val countsMessage by viewModel.classCountsMessage.collectAsStateWithLifecycle()
        SessionDetailDialog(
            record = record,
            count = sessionCount,
            countsFetchedAt = countsFetchedAt,
            countsLoading = countsLoading,
            countsMessage = countsMessage,
            onFetchCounts = { viewModel.fetchClassCounts() },
            onDismiss = {
                selectedRecord = null
                viewModel.clearSessionCount()
            },
        )
    }

    if (mapping) {
        CourseMappingDialog(
            courses = courses,
            errors = codeErrors,
            onSet = viewModel::setShortCode,
            onDismiss = { mapping = false },
        )
    }

    if (overriding) {
        OverridesDialog(
            overrides = overrides,
            periods = periods,
            onAdd = {
                overriding = false
                addingOverride = true
            },
            onEdit = {
                overriding = false
                editingOverride = it
            },
            onRemove = { override -> confirmingRemoveOverride = override },
            onDismiss = { overriding = false },
        )
    }

    confirmingForget?.let { course ->
        ConfirmDeleteDialog(
            what = course.name,
            confirmLabel = "Forget",
            onConfirm = {
                viewModel.forgetCourse(course)
                confirmingForget = null
            },
            onDismiss = { confirmingForget = null },
        )
    }

    confirmingRemoveOverride?.let { override ->
        ConfirmDeleteDialog(
            what = override.courseName,
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.removeOverride(override.dayOfWeek, override.sessionId)
                confirmingRemoveOverride = null
            },
            onDismiss = { confirmingRemoveOverride = null },
        )
    }

    if (addingOverride || editingOverride != null) {
        val existing = editingOverride
        OverrideEditorDialog(
            existing = existing,
            fixedDay = null,
            fixedSession = null,
            fixedLabel = null,
            periods = periods,
            choices = courseChoices,
            onForgetCourse = { confirmingForget = it },
            onSave = {
                viewModel.saveOverride(it, replacing = existing)
                addingOverride = false
                editingOverride = null
            },
            onDismiss = {
                addingOverride = false
                editingOverride = null
            },
        )
    }

    openSlot?.let { (day, slot) ->
        SlotDetailDialog(
            day = day,
            slot = slot,
            onEdit = {
                // Editing a published period means writing an override over it, so the
                // editor opens filled with whatever that period currently says.
                fillSlot = day to slot
                openSlot = null
            },
            onRemoveOverride = if (slot.isOverride) {
                { confirmingRemoveSlot = day to slot }
            } else {
                null
            },
            onDismiss = { openSlot = null },
        )
    }

    confirmingRemoveSlot?.let { (day, slot) ->
        ConfirmDeleteDialog(
            what = slot.courseName ?: "this class",
            confirmLabel = "Remove",
            onConfirm = {
                viewModel.removeOverride(day.date.dayOfWeek, slot.sessionId)
                confirmingRemoveSlot = null
                openSlot = null
            },
            onDismiss = { confirmingRemoveSlot = null },
        )
    }

    fillSlot?.let { (day, slot) ->
        OverrideEditorDialog(
            // A free cell starts blank; a cell with a class starts as that class,
            // whether the portal published it or the student placed it.
            existing = slot.takeUnless { it.isFree }?.let {
                TimetableOverride(
                    dayOfWeek = day.date.dayOfWeek,
                    sessionId = it.sessionId,
                    courseId = it.courseId,
                    courseName = it.courseName.orEmpty(),
                    courseCode = it.courseCode,
                    shortCode = it.shortCode,
                    instructor = it.instructors.joinToString(", ").takeIf { name -> name.isNotBlank() },
                    deliveryMethod = it.deliveryMethod,
                    classType = it.classType,
                )
            },
            fixedDay = day.date.dayOfWeek,
            fixedSession = slot.sessionId,
            fixedLabel = day.weekdayName + " · " + slot.sessionName + ", every week",
            periods = periods,
            choices = courseChoices,
            onForgetCourse = { confirmingForget = it },
            onSave = {
                viewModel.saveOverride(it)
                fillSlot = null
            },
            onDismiss = { fillSlot = null },
        )
    }
}

/** The timetable's own overflow menu: how it is laid out, and what overrides it. */
@Composable
private fun TimetableMenu(
    gridView: Boolean,
    onToggleView: () -> Unit,
    onMapCourses: () -> Unit,
    onOverride: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = "Timetable options")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (gridView) "Day list view" else "Week grid view") },
                onClick = {
                    open = false
                    onToggleView()
                },
            )
            DropdownMenuItem(
                text = { Text("Course short codes") },
                onClick = {
                    open = false
                    onMapCourses()
                },
            )
            DropdownMenuItem(
                text = { Text("Your classes") },
                onClick = {
                    open = false
                    onOverride()
                },
            )
        }
    }
}

// ---- upcoming week -------------------------------------------------------

/** Horizontal day picker: seven taps, today first. */
@Composable
private fun DayStrip(days: List<TimetableDay>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(days.size, key = { days[it].date.toString() }) { index ->
            val day = days[index]
            val selected = index == selectedIndex
            val liveCount = day.live.size
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                modifier = Modifier
                    .width(62.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSelect(index) },
            ) {
                Column(
                    Modifier.padding(vertical = 9.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, localeForFormatting()),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Text(
                        "${day.date.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    Gap(2)
                    // Count rather than a bare dot, so the strip says how busy each
                    // day is without needing to open it.
                    Text(
                        if (liveCount > 0) "$liveCount" else "–",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

/**
 * The locale to format dates with, read so that composition follows a locale change.
 *
 * `Locale.getDefault()` is a process-wide global: reading it directly in a
 * composable means the month and weekday names keep the language the screen was
 * first drawn in.
 */
@Composable
private fun localeForFormatting(): Locale = LocalConfiguration.current.locales[0]

/** Every period of the selected day, free ones included. */
@Composable
private fun DaySchedule(day: TimetableDay) {
    val locale = localeForFormatting()
    val heading = buildString {
        append(day.date.dayOfMonth)
        append(' ')
        append(day.date.month.getDisplayName(TextStyle.FULL, locale))
        append(" · ")
        append(day.weekdayName)
        if (day.isToday) append(" · Today")
    }

    // A day can be both scheduled and called off, so the banner and the class list
    // are independent: the banner explains the day, the list still shows what was
    // planned, struck through.
    val banner = when (day.status) {
        DayStatus.HOLIDAY -> day.note ?: "Classes suspended"
        DayStatus.WEEKEND -> "Not a working day"
        DayStatus.OUT_OF_TERM -> "Outside the current term"
        DayStatus.NO_CLASSES -> "No classes scheduled"
        DayStatus.CLASSES -> null
    }
    val isHoliday = day.status == DayStatus.HOLIDAY

    Column {
        Text(
            heading,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Gap(6)

        if (banner != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isHoliday) {
                    LocalStatusColors.current.watch.copy(alpha = 0.12f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    banner,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isHoliday) {
                        LocalStatusColors.current.watch
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        if (day.scheduled.isNotEmpty()) {
            if (banner != null) Gap(8)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(vertical = 4.dp)) {
                    day.slots.forEachIndexed { index, slot ->
                        SlotRow(slot)
                        if (index != day.slots.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(horizontal = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotRow(slot: ScheduleSlot) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            slot.sessionName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        if (slot.isFree) {
            Text(
                "Free",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Row
        }

        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    if (slot.isCancelled) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                ),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                slot.courseName ?: "Class",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                // Struck through as well as greyed, so the cancellation does not
                // depend on colour alone.
                textDecoration = if (slot.isCancelled) TextDecoration.LineThrough else null,
                color = if (slot.isCancelled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            val subtitle = listOfNotNull(
                slot.cancelledReason?.let { "Cancelled · $it" },
                slot.courseCode,
                slot.instructors.takeIf { it.isNotEmpty() }?.joinToString(", "),
                slot.classType,
                slot.deliveryMethod,
            ).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---- recorded attendance -------------------------------------------------

/**
 * Duty leave, by the portal's own code.
 *
 * Its own state rather than a shade of present: the list colours it separately, and
 * the filter leaves it out of both named options for the same reason.
 */
private val AttendanceRecord.isDutyLeave: Boolean
    get() = attendanceCode.equals("DL", ignoreCase = true)

/** The two ways of reading the same recorded classes. */
private enum class HistoryView { LIST, CALENDAR }

private fun HistoryView.label(): String = when (this) {
    HistoryView.LIST -> "List"
    HistoryView.CALENDAR -> "Calendar"
}

/**
 * What the list shows. Offered only in list view, because the calendar is read as a
 * month at a glance and hiding days from it would make a good month out of a bad one.
 *
 * Duty leave is left out of both named filters on purpose. It counts as positive for
 * the percentage, but it is not a class the student attended and it is not one they
 * missed, and the row already draws it in its own colour — folding it into "Present"
 * here would contradict what the list itself says about it. It stays visible under All.
 */
private enum class HistoryFilter { ALL, PRESENT, ABSENT }

private fun HistoryFilter.label(): String = when (this) {
    HistoryFilter.ALL -> "All"
    HistoryFilter.PRESENT -> "Present"
    HistoryFilter.ABSENT -> "Absent"
}

private fun HistoryFilter.matches(record: AttendanceRecord): Boolean = when (this) {
    HistoryFilter.ALL -> true
    HistoryFilter.PRESENT -> record.isPositive && !record.isDutyLeave
    HistoryFilter.ABSENT -> !record.isPositive
}

/**
 * Drops the records that do not match, and then the days left with none.
 *
 * A day kept with an empty record list would render as a heading over an empty card,
 * which reads as "nothing happened" rather than "nothing matched".
 */
private fun List<AttendanceDay>.filteredBy(filter: HistoryFilter): List<AttendanceDay> {
    if (filter == HistoryFilter.ALL) return this
    return mapNotNull { day ->
        val kept = day.records.filter { filter.matches(it) }
        if (kept.isEmpty()) null else day.copy(records = kept)
    }
}

/**
 * Chips rather than a second segmented row: the row above chooses how to read the
 * records, this chooses which ones, and the two should not look like one control
 * with six options.
 */
@Composable
private fun HistoryFilterRow(selected: HistoryFilter, onSelect: (HistoryFilter) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        HistoryFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(filter) },
                label = { Text(filter.label()) },
                leadingIcon = if (!isSelected) {
                    null
                } else {
                    {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun HistoryViewToggle(selected: HistoryView, onSelect: (HistoryView) -> Unit) {
    val views = HistoryView.entries
    SingleChoiceSegmentedButtonRow {
        views.forEachIndexed { index, view ->
            SegmentedButton(
                selected = view == selected,
                onClick = { onSelect(view) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = views.size),
                label = { Text(view.label()) },
            )
        }
    }
}

/**
 * The same records as the list, laid out on a month grid.
 *
 * The list answers "what happened that day"; the calendar answers "how has the
 * month gone", which is the shape of the question when a student is deciding
 * whether they can afford to miss something.
 */
@Composable
private fun HistoryCalendar(days: List<AttendanceDay>, onRecordClick: (AttendanceRecord) -> Unit) {
    // A day whose key could not be parsed cannot be placed on a grid. The list view
    // still shows it, so nothing is lost by leaving it out here.
    val byDate = remember(days) {
        days.mapNotNull { day -> day.date?.let { it to day } }.toMap()
    }
    val months = remember(byDate) {
        byDate.keys.map { YearMonth.from(it) }.distinct().sorted()
    }

    if (months.isEmpty()) {
        EmptyState(
            title = "No dated records",
            body = "These records carry no usable dates, so they cannot be placed on a calendar.",
        )
        return
    }

    var month by remember(months) { mutableStateOf(months.last()) }
    var picked by remember(months) { mutableStateOf<LocalDate?>(null) }

    // The highlighted day always belongs to the month on screen: stepping to another
    // month falls back to its most recent recorded day, rather than leaving the panel
    // below describing a date that is no longer visible.
    val shown = picked?.takeIf { YearMonth.from(it) == month }
        ?: byDate.keys.filter { YearMonth.from(it) == month }.maxOrNull()

    // Stepping through the months that have records, rather than by calendar month,
    // so the arrows never land on an empty grid.
    val index = months.indexOf(month).coerceAtLeast(0)

    Column {
        MonthHeader(
            month = month,
            hasPrevious = index > 0,
            hasNext = index < months.lastIndex,
            onPrevious = { month = months[index - 1] },
            onNext = { month = months[index + 1] },
        )
        Gap(6)
        WeekdayHeader()
        Gap(4)
        MonthGrid(
            month = month,
            byDate = byDate,
            selected = shown,
            onSelect = { picked = it },
        )
        Gap(10)
        CalendarLegend()

        byDate[shown]?.let { day ->
            Gap(14)
            HistoryDayBlock(day = day, onRecordClick = onRecordClick)
        }
    }
}

@Composable
private fun MonthHeader(
    month: YearMonth,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
            )
        }
        Text(
            month.month.getDisplayName(TextStyle.FULL, localeForFormatting()) + " " + month.year,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val locale = localeForFormatting()
    Row(Modifier.fillMaxWidth()) {
        WEEK_ORDER.forEach { dayOfWeek ->
            Text(
                dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    byDate: Map<LocalDate, AttendanceDay>,
    selected: LocalDate?,
    onSelect: (LocalDate) -> Unit,
) {
    val lengthOfMonth = month.lengthOfMonth()
    // Blank cells before the 1st, so every column stays under its weekday.
    val leading = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val rows = (leading + lengthOfMonth + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(DAYS_IN_WEEK) { column ->
                    val dayOfMonth = row * DAYS_IN_WEEK + column - leading + 1
                    val date = if (dayOfMonth in 1..lengthOfMonth) month.atDay(dayOfMonth) else null
                    DayCell(
                        date = date,
                        day = date?.let(byDate::get),
                        isSelected = date != null && date == selected,
                        onClick = { date?.let(onSelect) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayCell(
    date: LocalDate?,
    day: AttendanceDay?,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val statusColors = LocalStatusColors.current
    val accent = day?.accentColor(statusColors)
    val background = when {
        accent != null -> accent.copy(alpha = 0.16f)
        date == null -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
    }

    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .clip(shape)
            .background(background)
            .then(
                if (isSelected) {
                    Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .then(if (date == null) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "${date.dayOfMonth}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (day != null) FontWeight.Bold else FontWeight.Normal,
                color = if (day != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            // The count is what makes a tinted square mean something: the colour says
            // how the day went, the number says how much of it was recorded.
            if (day != null) {
                Text(
                    "${day.records.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * One colour for a whole day, worst-first: a single absence colours the day, and
 * duty leave only shows when nothing was actually missed. Same priority the list
 * rows use, so the two views never disagree about how a day went.
 */
private fun AttendanceDay.accentColor(colors: StatusColors): Color = when {
    records.any { !it.isPositive } -> colors.risk
    records.any { it.attendanceCode.equals("DL", ignoreCase = true) } -> colors.watch
    else -> colors.safe
}

@Composable
private fun CalendarLegend() {
    val colors = LocalStatusColors.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        LegendDot(colors.safe, "All present")
        LegendDot(colors.watch, "Duty leave")
        LegendDot(colors.risk, "Absent")
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}

/** Monday-first, matching the way the portal names its working days. */
private val WEEK_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private const val DAYS_IN_WEEK = 7


@Composable
private fun HistoryDayBlock(day: AttendanceDay, onRecordClick: (AttendanceRecord) -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatDayHeader(day.dateKey, day.displayDate),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (day.dayName.isNotBlank()) {
                Text(
                    " · " + day.dayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Gap(6)
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(vertical = 4.dp)) {
                day.records.forEach { record ->
                    HistoryRow(record) { onRecordClick(record) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(record: AttendanceRecord, onClick: () -> Unit) {
    val colors = LocalStatusColors.current
    val accent = when {
        record.isDutyLeave -> colors.watch
        record.isPositive -> colors.safe
        else -> colors.risk
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            record.sessionName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp),
        )
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(accent),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(record.courseName, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            record.courseCode?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        AttendanceChip(record.attendanceCode, record.attendanceName, record.isPositive)
    }
}

@Composable
private fun SessionDetailDialog(
    record: AttendanceRecord,
    count: SessionCount?,
    /** Null when the cohort report has never been read on this device. */
    countsFetchedAt: Long?,
    countsLoading: Boolean,
    countsMessage: String?,
    onFetchCounts: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(record.courseName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("Date", record.displayDate + "  " + record.dayName)
                DetailLine("Session", record.sessionName)
                DetailLine("Status", record.attendanceName)
                // The class alongside the individual, which is the comparison a
                // student actually wants here: an absence on a day nobody was marked
                // present means something different from one where everyone else was.
                // Absent while the room is empty is the shape of a cancelled class;
                // absent while twenty others were there is the shape of a mistake.
                //
                // Omitted rather than shown as zero when it is not known: the counts
                // come from a separate report, and "0 of 0" is a claim about the class
                // rather than an admission that the figure has not arrived.
                count?.takeUnless { it.isUnmarked }?.let {
                    DetailLine("Class", "${it.present} present · ${it.absent} absent")
                }
                // Nothing stored and nothing ever asked for: offer the fetch rather
                // than making it. The report is a whole term of the cohort's register
                // and most readers opening a session do not want it, so it is a
                // button here and a silence otherwise. Once it has been read, a
                // session with no row genuinely had no register, and saying nothing
                // is the honest answer.
                if (count == null && countsFetchedAt == null) {
                    Gap(2)
                    FetchClassCountsRow(loading = countsLoading, onFetch = onFetchCounts)
                }
                countsMessage?.let {
                    Gap(2)
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                record.courseCode?.let { DetailLine("Code", it) }
                record.classType?.let { DetailLine("Class type", it) }
                record.deliveryMethods?.let { DetailLine("Delivery", it) }
                record.lessonPlans?.let { DetailLine("Lesson plan", it) }
            }
        },
    )
}

/** The offer, and the wait once it has been taken up. */
@Composable
private fun FetchClassCountsRow(loading: Boolean, onFetch: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Class",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        if (loading) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Gap(0)
            Text(
                "  Fetching…",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            TextButton(
                onClick = onFetch,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Text("Fetch class attendance", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
