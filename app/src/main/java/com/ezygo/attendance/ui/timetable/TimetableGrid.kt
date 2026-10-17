package com.ezygo.attendance.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.domain.model.DayStatus
import com.ezygo.attendance.domain.model.ScheduleSlot
import com.ezygo.attendance.domain.model.TimetableDay
import com.ezygo.attendance.domain.model.gridDays

/**
 * The week as a grid: weekdays down the left, periods across the top.
 *
 * A cell holds a course's short code where the user has given it one, and its full
 * name where they have not — which is the whole reason short codes exist, since five
 * columns of "Fundamentals of Data Science" is not a timetable anyone can read.
 *
 * Scrolls sideways rather than shrinking the columns: six periods at a legible width
 * is wider than a phone, and a grid squeezed until it fits is a grid nobody can use.
 */
@Composable
fun TimetableGrid(
    days: List<TimetableDay>,
    /** A period with a class on it: opens what the grid has no room to say. */
    onSlotClick: (TimetableDay, ScheduleSlot) -> Unit,
    /** A free period: the offer to put something there. */
    onEmptyClick: (TimetableDay, ScheduleSlot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val week = days.gridDays()
    // Every day carries the same period list, so the first day with any is the header.
    val periods = week.firstOrNull { it.slots.isNotEmpty() }?.slots.orEmpty()
    if (periods.isEmpty()) return

    Column(modifier.horizontalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderCell("", width = DAY_COLUMN)
            periods.forEach { period ->
                HeaderCell(period.sessionName, width = CELL_WIDTH)
            }
        }
        week.forEach { day ->
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                DayCell(day)
                day.slots.forEach { slot ->
                    SlotCell(
                        slot = slot,
                        onClick = {
                            if (slot.isFree) onEmptyClick(day, slot) else onSlotClick(day, slot)
                        },
                    )
                }
                // A day the timetable does not cover at all still gets its row, so the
                // week reads as a week rather than as a list of the days with classes.
                if (day.slots.isEmpty()) {
                    Box(
                        Modifier
                            .width(CELL_WIDTH * periods.size)
                            .height(CELL_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            day.emptyLabel(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Box(
        Modifier.width(width).padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DayCell(day: TimetableDay) {
    Column(
        Modifier
            .width(DAY_COLUMN)
            .height(CELL_HEIGHT)
            .padding(horizontal = 2.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            day.weekdayName.take(3),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
            color = if (day.isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
        Text(
            "${day.date.dayOfMonth}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SlotCell(slot: ScheduleSlot, onClick: () -> Unit) {
    val cancelled = slot.isCancelled
    val background = when {
        slot.isFree -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        cancelled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
        slot.isOverride -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val foreground = when {
        slot.isFree -> MaterialTheme.colorScheme.onSurfaceVariant
        cancelled -> MaterialTheme.colorScheme.onErrorContainer
        slot.isOverride -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = Modifier.width(CELL_WIDTH).height(CELL_HEIGHT).padding(2.dp),
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(background)
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            val name = slot.displayName
            if (name == null) {
                // An empty period is an invitation, not a gap. The plus is what makes
                // the cell look like something that can be filled in.
                Icon(
                    Icons.Default.Add,
                    contentDescription = "Add a class to " + slot.sessionName,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                Text(
                    name,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = foreground,
                    // A cancelled class is struck through, whoever put it there: the
                    // override is a change to the timetable, not an exemption from it.
                    textDecoration = if (cancelled) TextDecoration.LineThrough else null,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Why this day has no periods at all, in as few words as a cell can carry. */
private fun TimetableDay.emptyLabel(): String = when (status) {
    DayStatus.WEEKEND -> "Weekend"
    DayStatus.HOLIDAY -> note ?: "Holiday"
    DayStatus.OUT_OF_TERM -> "Out of term"
    else -> "No classes"
}

private val DAY_COLUMN = 44.dp
private val CELL_WIDTH = 74.dp
private val CELL_HEIGHT = 58.dp
