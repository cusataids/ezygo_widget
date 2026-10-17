package com.ezygo.attendance.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.AbsenceDay
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.dateWithWeekday
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * Every day with an absence on it, worst first.
 *
 * A page of its own rather than ten rows on the attendance screen. Ten was an
 * arbitrary cut that answered "roughly how bad has it been"; the question a student
 * actually brings to this list is "which days exactly", and that one needs all of
 * them. The attendance screen keeps the headline and sends you here for the rest.
 *
 * Ranked by how much of the day was missed rather than by date, because a list of
 * absences in date order is just the history list again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbsenceDaysScreen(
    onBack: () -> Unit,
    viewModel: AbsenceDaysViewModel = hiltViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()
    val wholeDaysOnly by viewModel.wholeDaysOnly.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Days with the most absences") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // A chip rather than a button: it is a state that stays on until it
                // is turned off, and a chip is the control in this app that looks
                // like one.
                FilterChip(
                    selected = wholeDaysOnly,
                    onClick = viewModel::toggleWholeDaysOnly,
                    label = {
                        Text(
                            "Remove days with at least one present",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = if (wholeDaysOnly) {
                        {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        null
                    },
                )
            }

            if (days.isEmpty()) {
                item {
                    EmptyState(
                        title = if (wholeDaysOnly) {
                            "No day missed in full"
                        } else {
                            "No absences recorded"
                        },
                        body = if (wholeDaysOnly) {
                            "Every day with an absence on it also has a class you " +
                                "attended. Which is the good outcome."
                        } else {
                            "Nothing has been marked against you this period."
                        },
                    )
                }
            } else {
                itemsIndexed(days) { index, day ->
                    AbsenceDayCard(rank = index + 1, day = day)
                }
                // The line that used to close this list now introduces it, from the
                // card on the attendance screen. A joke at the bottom of a list of
                // forty days is a joke nobody reaches.
                item { Gap(2) }
            }
        }
    }
}

/** One ranked day. */
@Composable
private fun AbsenceDayCard(rank: Int, day: AbsenceDay) {
    val colors = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$rank",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    dateWithWeekday(day.dateKey, day.displayDate, day.dayName),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    // The denominator matters: four missed out of four is a day off,
                    // four out of seven is a late start.
                    if (day.wholeDay) "Missed the whole day" else "of ${day.recorded} classes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                if (day.absences == 1) "1 absence" else "${day.absences} absences",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                // A whole day missed is the one worth picking out of the column.
                color = if (day.wholeDay) colors.risk else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
