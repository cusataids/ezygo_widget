package com.ezygo.attendance.ui.miniapps.whatif

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.ezygo.attendance.domain.model.AttendanceRecord
import com.ezygo.attendance.domain.model.ClaimDay
import com.ezygo.attendance.domain.model.ClaimSessionType
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.VerticalScrollbar
import com.ezygo.attendance.ui.common.WideDialogProperties
import com.ezygo.attendance.ui.common.currentLocale
import com.ezygo.attendance.ui.common.recordDate
import com.ezygo.attendance.ui.common.wideDialogModifier
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

/** Which way in to adding a claim the user picked. */
enum class ClaimEntry { BY_DATE, BY_SUBJECT }

/**
 * The two ways to say which classes a claim covers.
 *
 * Both routes end at the same place — a set of recorded sessions — so they are offered
 * as two doors to one room rather than two features. Which door suits depends on how
 * the student remembers it: "I was away on the 10th and 11th" is a date, "I keep
 * missing Physics" is a subject.
 */
@Composable
fun ClaimEntryDialog(onPick: (ClaimEntry) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDismiss,
        title = { Text("Add claim") },
        text = {
            Column {
                Text(
                    "Claimed sessions count as attended in this projection only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(12)
                EntryRow(
                    title = "By date",
                    subtitle = "Select the days you have a claim for",
                    onClick = { onPick(ClaimEntry.BY_DATE) },
                )
                Gap(8)
                EntryRow(
                    title = "By subject",
                    subtitle = "Pick a subject and tick the absences to claim",
                    onClick = { onPick(ClaimEntry.BY_SUBJECT) },
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EntryRow(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
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
 * Dates on a calendar, then how much of each day is being claimed.
 *
 * Days with no recorded absence are shown but not selectable. A claim on such a day
 * would resolve to nothing, and letting it be added would leave the user staring at a
 * claim that changes no figure with no explanation of why.
 *
 * Everything is written straight to the view model as it is chosen, which is what
 * keeps the selection intact if the dialog is dismissed and reopened mid-claim.
 */
@Composable
fun ClaimCalendarDialog(
    claims: List<ClaimDay>,
    absenceDates: Set<LocalDate>,
    sessionsFor: (LocalDate) -> List<AttendanceRecord>,
    coveredFor: (ClaimDay) -> Int,
    onAddDate: (LocalDate) -> Unit,
    onRemoveDate: (LocalDate) -> Unit,
    onSetType: (LocalDate, ClaimSessionType) -> Unit,
    onToggleSession: (LocalDate, Int) -> Unit,
    onDone: () -> Unit,
) {
    val months = remember(absenceDates) {
        absenceDates.map { YearMonth.from(it) }.distinct().sorted()
    }
    var month by remember(months) {
        mutableStateOf(months.lastOrNull() ?: YearMonth.from(LocalDate.now()))
    }
    val index = months.indexOf(month)

    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDone,
        title = { Text("Claim by date") },
        text = {
            // The grid alone fills the dialog, so everything a selected date brings
            // with it — the session type chips, the custom-period ticks, the count of
            // what it actually covers — starts below the fold. Without a hint the
            // dialog looks like a calendar that does nothing when you tap it.
            val scroll = rememberScrollState()
            Box {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(scroll)
                        // Room at the edge for the bar, so it never sits on the text.
                        .padding(end = 10.dp),
                ) {
                    if (absenceDates.isEmpty()) {
                        Text(
                            "There are no recorded absences to claim.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        return@Column
                    }

                    MonthStepper(
                        month = month,
                        hasPrevious = index > 0,
                        hasNext = index in 0 until months.lastIndex,
                        onPrevious = { months.getOrNull(index - 1)?.let { month = it } },
                        onNext = { months.getOrNull(index + 1)?.let { month = it } },
                    )
                    WeekdayStrip()
                    Gap(4)
                    ClaimMonthGrid(
                        month = month,
                        selectable = absenceDates,
                        selected = claims.map { it.date }.toSet(),
                        onToggle = { date ->
                            if (claims.any { it.date == date }) onRemoveDate(date) else onAddDate(date)
                        },
                    )

                    Gap(14)
                    Text(
                        if (claims.isEmpty()) "No dates selected" else "Selected dates",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    claims.forEach { claim ->
                        Gap(8)
                        ClaimDayRow(
                            claim = claim,
                            sessions = sessionsFor(claim.date),
                            covered = coveredFor(claim),
                            onRemove = { onRemoveDate(claim.date) },
                            onSetType = { onSetType(claim.date, it) },
                            onToggleSession = { onToggleSession(claim.date, it) },
                        )
                    }
                }
                VerticalScrollbar(scroll)
            }
        },
        confirmButton = { TextButton(onClick = onDone) { Text("Done") } },
    )
}

/**
 * One selected date: what is being claimed on it, and what that actually covers.
 *
 * The covered count is the validation. A whole day with nothing absent on it, or a
 * custom selection with no period ticked, says so in place rather than being silently
 * worth nothing.
 */
@Composable
private fun ClaimDayRow(
    claim: ClaimDay,
    sessions: List<AttendanceRecord>,
    covered: Int,
    onRemove: () -> Unit,
    onSetType: (ClaimSessionType) -> Unit,
    onToggleSession: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        claim.date.dayOfMonth.toString() + " " +
                            claim.date.month.getDisplayName(TextStyle.SHORT, currentLocale()) +
                            " " + claim.date.year,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        when (covered) {
                            0 -> "Covers nothing — no absence here"
                            1 -> "Covers 1 absent session"
                            else -> "Covers $covered absent sessions"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (covered == 0) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove this date",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ClaimSessionType.entries.forEach { type ->
                    FilterChip(
                        selected = claim.type == type,
                        onClick = { onSetType(type) },
                        label = { Text(type.label, style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }

            if (claim.type == ClaimSessionType.CUSTOM) {
                Gap(6)
                if (sessions.isEmpty()) {
                    Text(
                        "Nothing was recorded on this day.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                sessions.forEach { record ->
                    SessionTickRow(
                        record = record,
                        checked = record.sessionId in claim.customSessionIds,
                        onToggle = { onToggleSession(record.sessionId) },
                    )
                }
            }
        }
    }
}

/**
 * One period of a day, tickable only when it is an absence.
 *
 * A period already marked present is listed rather than hidden, so the day reads as
 * the day it was — but it cannot be ticked, because claiming it would change nothing
 * and implying otherwise would be a lie.
 */
@Composable
private fun SessionTickRow(record: AttendanceRecord, checked: Boolean, onToggle: () -> Unit) {
    val claimable = !record.isPositive
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (claimable) Modifier.clickable(onClick = onToggle) else Modifier)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked && claimable, onCheckedChange = { onToggle() }, enabled = claimable)
        Column(Modifier.weight(1f)) {
            Text(
                record.sessionName + " · " + record.courseName,
                style = MaterialTheme.typography.labelMedium,
                color = if (claimable) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (!claimable) {
                Text(
                    record.attendanceName + " — nothing to claim",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The subject-wise route: one course's absences, ticked off.
 *
 * Reaches the same claim list the calendar builds, so an absence claimed here and a
 * day claimed there cannot both count the session.
 */
@Composable
fun ClaimSubjectDialog(
    courseName: String,
    absences: List<AttendanceRecord>,
    alreadyClaimed: Set<Pair<String, Int>>,
    onConfirm: (List<AttendanceRecord>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember(absences) { mutableStateOf(emptySet<Pair<String, Int>>()) }

    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDismiss,
        title = { Text(courseName, maxLines = 2) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (absences.isEmpty()) {
                    Text(
                        "No absences recorded for this subject.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                Text(
                    "Tick the absences to claim.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(6)
                absences.forEach { record ->
                    val id = record.dateKey to record.sessionId
                    val claimed = id in alreadyClaimed
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (claimed) Modifier else Modifier.clickable {
                                picked = if (id in picked) picked - id else picked + id
                            })
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            // An absence already in the claim shows as ticked and
                            // locked, rather than offering to add it a second time.
                            checked = claimed || id in picked,
                            onCheckedChange = {
                                picked = if (id in picked) picked - id else picked + id
                            },
                            enabled = !claimed,
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                record.recordDate() + " · " + record.sessionName,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                if (claimed) "Already claimed" else record.attendanceName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(absences.filter { (it.dateKey to it.sessionId) in picked }) },
                enabled = picked.isNotEmpty(),
            ) {
                Text(if (picked.isEmpty()) "Claim" else "Claim ${picked.size}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** One course offered for a subject-wise claim, with how much there is to claim. */
data class ClaimCourse(val courseId: Int, val name: String, val absences: Int)

/**
 * Which subject's absences to claim.
 *
 * Deliberately not the "Add a subject" picker this screen already has. That one
 * offers every course plus a hand-entered option, and neither fits here: a course
 * with full attendance has nothing to claim, and a subject typed in by hand has no
 * records to claim at all.
 */
@Composable
fun ClaimCoursePickerDialog(
    courses: List<ClaimCourse>,
    onPick: (ClaimCourse) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDismiss,
        title = { Text("Claim by subject") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (courses.isEmpty()) {
                    Text(
                        "No subject has a recorded absence to claim.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                courses.forEach { course ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(course) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(course.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (course.absences == 1) {
                                    "1 absence"
                                } else {
                                    "${course.absences} absences"
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
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- calendar ------------------------------------------------------------

@Composable
private fun MonthStepper(
    month: YearMonth,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious, enabled = hasPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.month.getDisplayName(TextStyle.FULL, currentLocale()) + " " + month.year,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, enabled = hasNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayStrip() {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth()) {
        CLAIM_WEEK_ORDER.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, locale),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ClaimMonthGrid(
    month: YearMonth,
    selectable: Set<LocalDate>,
    selected: Set<LocalDate>,
    onToggle: (LocalDate) -> Unit,
) {
    val length = month.lengthOfMonth()
    val leading = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val rows = (leading + length + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(CLAIM_DAYS_IN_WEEK) { column ->
                    val dayOfMonth = row * CLAIM_DAYS_IN_WEEK + column - leading + 1
                    val date = if (dayOfMonth in 1..length) month.atDay(dayOfMonth) else null
                    ClaimDayCell(
                        date = date,
                        claimable = date != null && date in selectable,
                        isSelected = date != null && date in selected,
                        onClick = { date?.let(onToggle) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ClaimDayCell(
    date: LocalDate?,
    claimable: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    val background = when {
        isSelected -> MaterialTheme.colorScheme.primary
        claimable -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        date == null -> Color.Transparent
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
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
            // Only days with something to claim respond. A day with full attendance
            // is drawn plainly and does nothing when tapped.
            .then(if (claimable) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (date == null) return@Box
        Text(
            "${date.dayOfMonth}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (claimable) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onPrimary
                claimable -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private val CLAIM_WEEK_ORDER = listOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY,
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY,
)

private const val CLAIM_DAYS_IN_WEEK = 7

// ---- the claim list on the What if screen --------------------------------

/**
 * One claimed day, as it reads on the main screen.
 *
 * The covered count is the point of the row. "10 Mar, Full day" says what was asked
 * for; "covers 2 absent sessions" says what it is actually worth, and those two differ
 * whenever part of the day was attended.
 */
@Composable
fun ClaimSummaryRow(
    claim: ClaimDay,
    covered: Int,
    onRemove: () -> Unit,
    onDetails: () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    claim.date.dayOfMonth.toString() + " " +
                        claim.date.month.getDisplayName(TextStyle.SHORT, currentLocale()) +
                        " · " + claim.type.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    when (covered) {
                        0 -> "Covers nothing — no absence here"
                        1 -> "Counts 1 absent session as attended"
                        else -> "Counts $covered absent sessions as attended"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (covered == 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            // Two actions, kept apart: a look and a delete sitting shoulder to
            // shoulder is how a tap meant for one lands on the other. Each gets its
            // own touch target, and only the destructive one carries the X.
            IconButton(onClick = onDetails) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = "Which sessions this claim covers",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove this claim",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun AddClaimButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ClaimActionButton(
        label = "Add claim",
        color = MaterialTheme.colorScheme.primary,
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * Drops every claim at once, without touching the subjects beside them.
 *
 * Reset already empties the whole screen, so this exists for the narrower thing a
 * student actually wants more often: the subject list is still the one they want to
 * reason about, and only the days they were testing need to go. Shown only when
 * there is something to clear, because a button that does nothing is worse than no
 * button at all.
 *
 * No confirmation, matching Reset in the app bar. Guarding one destructive control
 * and not the other would make the pair read as different kinds of action when they
 * differ only in reach, and a claim is a few taps to put back.
 */
@Composable
fun ClearClaimsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    ClaimActionButton(
        label = "Clear Claims",
        color = MaterialTheme.colorScheme.error,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun ClaimActionButton(
    label: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
            )
        }
    }
}


/**
 * Exactly which absences a claim turns into attendance, for one claimed day.
 *
 * Read-only, and resolved through the same function the projection uses — so this
 * list is not a description of what the claim probably does, it is the set the
 * average was computed from. Closing it changes nothing, and nothing here writes to
 * an attendance record.
 */
@Composable
fun ClaimDetailsDialog(
    claim: ClaimDay,
    sessions: List<AttendanceRecord>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDismiss,
        title = { Text("Claimed sessions") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(
                    claim.date.dayOfMonth.toString() + " " +
                        claim.date.month.getDisplayName(TextStyle.FULL, currentLocale()) +
                        " " + claim.date.year + " · " + claim.type.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Gap(8)
                if (sessions.isEmpty()) {
                    // The one line that is content rather than commentary: without it
                    // an empty claim opens an empty dialog.
                    Text(
                        "No recorded absence on this day.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    return@Column
                }
                Gap(4)
                sessions.forEach { record ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                "Session " + record.sessionName + " · " + record.courseName,
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                record.attendanceName + " → counted as present",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Why a subject nobody added is in the list. */
@Composable
fun ClaimedSubjectDialog(onDismiss: () -> Unit) {
    AlertDialog(
        modifier = wideDialogModifier(),
        properties = WideDialogProperties,
        onDismissRequest = onDismiss,
        title = { Text("Added by a claim") },
        text = {
            Text(
                "This subject is listed because one of your claims covers an absence " +
                    "in it. Its projected attendance counts those sessions as attended. " +
                    "Removing the claim removes the subject again.",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
