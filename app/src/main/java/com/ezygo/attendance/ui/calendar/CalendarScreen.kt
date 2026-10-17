package com.ezygo.attendance.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.CalendarEvent
import com.ezygo.attendance.domain.model.EventSource
import com.ezygo.attendance.ui.common.CannotDeleteDialog
import com.ezygo.attendance.ui.common.ConfirmDeleteDialog
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.currentLocale
import com.ezygo.attendance.ui.common.timeAgo
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

/**
 * The academic calendar: the institution's published feed and the student's own
 * notes, on one grid.
 *
 * Feed events and local events are never drawn the same way. A student needs to
 * know at a glance which dates came from the institution and which they wrote
 * down themselves, because only one of those is authoritative.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBack: () -> Unit,
    /** Day to open on, when the screen was reached by tapping a specific event. */
    initialDate: LocalDate? = null,
    /** Event to open outright, when a widget row named one rather than a day. */
    initialEventId: String? = null,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val events by viewModel.events.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val lastSync by viewModel.lastSyncEpochMs.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    // Arriving from a tapped event opens on that event's day rather than on today.
    val landingDate = initialDate ?: today
    var month by remember(landingDate) { mutableStateOf(YearMonth.from(landingDate)) }
    var selected by remember(landingDate) { mutableStateOf(landingDate) }
    var detail by remember { mutableStateOf<CalendarEvent?>(null) }
    var editing by remember { mutableStateOf<EventDraft?>(null) }

    // A widget row names the event, not just its day. Events arrive from Room a
    // moment after the screen does, so this waits for the one it was sent to find
    // rather than giving up on the first empty list, and records that it opened it
    // so dismissing the dialog does not immediately reopen it.
    var openedEventId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(initialEventId, events) {
        val wanted = initialEventId ?: return@LaunchedEffect
        if (openedEventId == wanted) return@LaunchedEffect
        val event = events.firstOrNull { it.id == wanted } ?: return@LaunchedEffect
        openedEventId = wanted
        month = YearMonth.from(event.startDate())
        selected = event.startDate()
        detail = event
    }

    // One entry per day an event covers, so a multi-day exam period lights up every
    // square it spans rather than only the first.
    val byDate = remember(events) { events.groupByDay() }
    val selectedEvents = byDate[selected].orEmpty()
    // Started and not yet finished. Read once per composition rather than ticked:
    // the boundary only matters to the minute for an event ending while the screen
    // is open, and reopening the calendar is what a student does anyway.
    val ongoing = remember(events, today) {
        val now = Instant.now()
        events.filter { it.isOngoing(now, today) }
    }

    // Bounded by a horizon rather than a count: the question this list answers is
    // "what is still to come this semester", and a fixed number of rows answers it
    // only until the academic feed puts five dates in the same fortnight. Anything
    // already running is listed above instead, so nothing appears in both.
    val upcoming = remember(events, ongoing, today) {
        val horizon = today.plusMonths(UPCOMING_MONTHS)
        val running = ongoing.mapTo(mutableSetOf()) { it.id }
        events.filter { event ->
            val start = event.startDate()
            event.id !in running && !start.isBefore(today) && !start.isAfter(horizon)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editing = EventDraft(existing = null, date = selected) }) {
                        Icon(Icons.Default.Add, contentDescription = "Add event")
                    }
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isSyncing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { message ->
                    item { InlineError(message, onRetry = viewModel::refresh) }
                }

                item {
                    MonthHeader(
                        month = month,
                        onPrevious = { month = month.minusMonths(1) },
                        onNext = { month = month.plusMonths(1) },
                    )
                }
                item { WeekdayHeader() }
                item {
                    MonthGrid(
                        month = month,
                        today = today,
                        byDate = byDate,
                        selected = selected,
                        onSelect = { selected = it },
                    )
                }
                item { CalendarLegend() }

                item {
                    Column {
                        Gap(2)
                        Text(
                            selected.format(dayHeadingFormat(currentLocale())),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                if (selectedEvents.isEmpty()) {
                    item {
                        EmptyState(
                            title = "Nothing on this day",
                            body = "Use the + button to note something of your own.",
                        )
                    }
                }

                items(selectedEvents.size) { index ->
                    val event = selectedEvents[index]
                    EventRow(event = event, onClick = { detail = event })
                }

                // Happening right now, and therefore the first thing worth
                // knowing. A multi-day exam period reads as already over once it is
                // filed under "upcoming" and the date on it has passed.
                if (ongoing.isNotEmpty()) {
                    item {
                        Column {
                            Gap(8)
                            Text(
                                "Ongoing",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(ongoing.size) { index ->
                        val event = ongoing[index]
                        UpcomingRow(event = event, onClick = { detail = event })
                    }
                }

                // What is coming, regardless of which day happens to be selected —
                // otherwise the only way to find the next exam is to page through
                // the months looking for it.
                if (upcoming.isNotEmpty()) {
                    item {
                        Column {
                            Gap(8)
                            Text(
                                "Upcoming",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    items(upcoming.size) { index ->
                        val event = upcoming[index]
                        UpcomingRow(
                            event = event,
                            // Opens the event itself. The row already states its
                            // date, so what the reader does not have yet is the
                            // detail behind it.
                            onClick = { detail = event },
                        )
                    }
                }

                item {
                    // The honest freshness signal: cached events are always on
                    // screen, so what matters is when they last came from the feed.
                    val syncedAt = lastSync
                    Column {
                        Gap(4)
                        Text(
                            if (syncedAt == null) {
                                "Not synced yet"
                            } else {
                                "Last synced " + timeAgo(syncedAt)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }

    detail?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { detail = null },
            onEdit = {
                editing = EventDraft(existing = event, date = event.startDate())
                detail = null
            },
            onDelete = {
                viewModel.deleteEvent(event.id)
                detail = null
            },
        )
    }

    deleteError?.let { message ->
        CannotDeleteDialog(message, onDismiss = viewModel::dismissDeleteError)
    }

    editing?.let { draft ->
        EventEditorDialog(
            draft = draft,
            onDismiss = { editing = null },
            onConfirm = { title, date, time, note ->
                val existing = draft.existing
                if (existing == null) {
                    viewModel.addEvent(title = title, date = date, time = time, note = note)
                } else {
                    viewModel.updateEvent(
                        id = existing.id,
                        title = title,
                        date = date,
                        time = time,
                        note = note,
                    )
                }
                // Follow the event to wherever it was filed, so the result of the
                // edit is on screen instead of somewhere the user has to go find.
                selected = date
                month = YearMonth.from(date)
                editing = null
            },
        )
    }
}

/**
 * An event being written: [existing] null for a new one, otherwise the local event
 * being edited. [date] is the day the editor opens on.
 */
private data class EventDraft(val existing: CalendarEvent?, val date: LocalDate)

// ---- grid ----------------------------------------------------------------

@Composable
private fun MonthHeader(month: YearMonth, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Previous month",
            )
        }
        Text(
            month.month.getDisplayName(TextStyle.FULL, currentLocale()) + " " + month.year,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Next month",
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    val locale = currentLocale()
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
    today: LocalDate,
    byDate: Map<LocalDate, List<CalendarEvent>>,
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    val lengthOfMonth = month.lengthOfMonth()
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
                        events = date?.let { byDate[it] }.orEmpty(),
                        isSelected = date == selected,
                        isToday = date == today,
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
    events: List<CalendarEvent>,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        isToday -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f)
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
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            )
            // A dot per source rather than per event: the point is "is there
            // something here, and whose is it", not an exact count in a 40dp square.
            // Driven off the enum so the order is stable and a new feed needs no
            // change here.
            val present = EventSource.entries.filter { source ->
                events.any { it.source == source }
            }
            if (present.isNotEmpty()) {
                Gap(2)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    present.forEach { SourceDot(it) }
                }
            }
        }
    }
}

@Composable
private fun SourceDot(source: EventSource) {
    Box(
        Modifier
            .size(5.dp)
            .clip(CircleShape)
            .background(source.color()),
    )
}

/**
 * Names the dot colours. Wraps rather than sitting on one line: three labels do
 * not fit across a phone, and a legend that runs off the edge explains nothing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarLegend() {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        EventSource.entries.forEach { source ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceDot(source)
                Text(
                    source.label(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 5.dp),
                )
            }
        }
    }
}

// ---- events --------------------------------------------------------------

@Composable
private fun EventRow(event: CalendarEvent, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(event.source.color()),
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
                        event.timeLabel(currentLocale()),
                        event.location,
                        event.source.label(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * An upcoming event, shown with its date because it is not necessarily on the day
 * currently selected - which is the whole point of the section.
 */
@Composable
private fun UpcomingRow(event: CalendarEvent, onClick: () -> Unit) {
    val locale = currentLocale()
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(event.source.color()),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    event.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                )
                Gap(2)
                Text(
                    event.startDate().format(dayHeadingFormat(locale)) +
                        " · " + event.timeLabel(locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EventDetailDialog(
    event: CalendarEvent,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val locale = currentLocale()
    var confirmingDelete by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            what = """ + event.title + """,
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(event.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    event.startDate().format(dayHeadingFormat(locale)) +
                        " · " + event.timeLabel(locale),
                    style = MaterialTheme.typography.bodyMedium,
                )
                event.location?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                event.description?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Text(
                    when (event.source) {
                        EventSource.BATCH ->
                            "From the IMSc 24-29 Batch Calendar."
                        EventSource.ACADEMIC ->
                            "From the institution's academic calendar."
                        EventSource.LOCAL -> "Saved on this device."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            // A feed event can be neither edited nor deleted: the next sync would
            // overwrite the change, so offering either would be a lie.
            if (event.source == EventSource.LOCAL) {
                Row {
                    TextButton(onClick = { confirmingDelete = true }) { Text("Delete") }
                    TextButton(onClick = onEdit) { Text("Edit") }
                }
            } else {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            if (event.source == EventSource.LOCAL) {
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}

/**
 * Writes a local event: new when [EventDraft.existing] is null, otherwise an edit
 * of that event in place.
 *
 * One dialog for both, because the fields are identical and a separate edit screen
 * would be the same form with a different title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventEditorDialog(
    draft: EventDraft,
    onDismiss: () -> Unit,
    onConfirm: (title: String, date: LocalDate, time: LocalTime?, note: String?) -> Unit,
) {
    val existing = draft.existing
    var title by remember(draft) { mutableStateOf(existing?.title.orEmpty()) }
    var note by remember(draft) { mutableStateOf(existing?.description.orEmpty()) }
    var date by remember(draft) { mutableStateOf(draft.date) }
    var allDay by remember(draft) { mutableStateOf(existing?.isAllDay ?: true) }
    var time by remember(draft) { mutableStateOf(existing?.startTime() ?: LocalTime.of(9, 0)) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    val locale = currentLocale()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New event" else "Edit event") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                FieldRow(label = "Date", onClick = { pickingDate = true }) {
                    FieldValue(date.format(dayHeadingFormat(locale)))
                }
                FieldRow(label = "All day") {
                    Switch(checked = allDay, onCheckedChange = { allDay = it })
                }
                if (!allDay) {
                    FieldRow(label = "Starts", onClick = { pickingTime = true }) {
                        FieldValue(time.format(timeFormat(locale)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title,
                        date,
                        if (allDay) null else time,
                        note.takeIf { it.isNotBlank() },
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (pickingDate) {
        // The picker works in UTC millis by contract, so the conversion both ways is
        // anchored to UTC. Using the local zone here would shift the chosen date by
        // a day for anyone east or west of Greenwich.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        // DatePickerDialog rather than an AlertDialog holding a DatePicker: the
        // picker needs its own width (this dialog opts out of the platform default)
        // and carries no extra content padding, where AlertDialog's inset squeezed
        // the seven-column grid out of alignment.
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    pickingDate = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state)
        }
    }

    if (pickingTime) {
        val state = rememberTimePickerState(
            initialHour = time.hour,
            initialMinute = time.minute,
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Start time") },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute)
                    pickingTime = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * Label on the left, value on the right, on one shared trailing edge.
 *
 * The label takes the slack rather than a spacer between the two, so every row's
 * control ends flush with the text fields above it. A `TextButton` cannot be used
 * for the value: its built-in content padding insets the text from its own edge,
 * which left the date sitting a few dp short of the switch below it. Making the
 * whole row clickable instead lines the values up and gives a bigger touch target
 * than the button had.
 */
/** Coloured rather than styled as a button, so the row still reads as tappable. */
@Composable
private fun FieldValue(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
    )
}

@Composable
private fun FieldRow(
    label: String,
    onClick: (() -> Unit)? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

// ---- helpers -------------------------------------------------------------

/**
 * Every day each event covers, so multi-day entries appear on all of their dates.
 *
 * The span is capped: a malformed feed with a runaway DTEND would otherwise fill
 * the map with years of entries for a single event.
 */
private fun List<CalendarEvent>.groupByDay(): Map<LocalDate, List<CalendarEvent>> {
    val byDate = mutableMapOf<LocalDate, MutableList<CalendarEvent>>()
    for (event in this) {
        var date = event.startDate()
        val last = minOf(event.endDate(), date.plusDays(MAX_EVENT_SPAN_DAYS))
        while (date <= last) {
            byDate.getOrPut(date) { mutableListOf() }.add(event)
            date = date.plusDays(1)
        }
    }
    return byDate
}

@Composable
private fun EventSource.color(): Color = when (this) {
    EventSource.BATCH -> MaterialTheme.colorScheme.primary
    EventSource.ACADEMIC -> MaterialTheme.colorScheme.secondary
    EventSource.LOCAL -> MaterialTheme.colorScheme.tertiary
}

private fun EventSource.label(): String = when (this) {
    EventSource.BATCH -> "IMSc 24-29 Batch Calendar"
    EventSource.ACADEMIC -> "Academic calendar"
    EventSource.LOCAL -> "On this device"
}

private fun CalendarEvent.timeLabel(locale: Locale): String =
    startTime()?.format(timeFormat(locale)) ?: "All day"

private fun dayHeadingFormat(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale)

private fun timeFormat(locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)

/** Monday-first, matching the timetable. */
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
private const val MAX_EVENT_SPAN_DAYS = 60L

/**
 * Whether the event has started and has not finished.
 *
 * All-day events are judged by date, because that is the only resolution they carry.
 * A timed event is judged against the clock, and one with no end is a moment rather
 * than a span: it is never "in progress", it has either happened or it has not.
 */
private fun CalendarEvent.isOngoing(now: Instant, today: LocalDate): Boolean = if (isAllDay) {
    !startDate().isAfter(today) && !endDate().isBefore(today)
} else {
    val finish = end ?: return false
    !start.isAfter(now) && finish.isAfter(now)
}

/**
 * How far ahead the upcoming list looks.
 *
 * Long enough to cover a semester, which is the span a student plans over, and short
 * enough that next year's academic calendar does not arrive as a wall of dates.
 */
private const val UPCOMING_MONTHS = 6L
