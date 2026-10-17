package com.ezygo.attendance.ui.subject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.ezygo.attendance.domain.AttendedDay
import com.ezygo.attendance.ui.common.AttendanceBar
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.asPercentLabel1dp
import com.ezygo.attendance.ui.common.dateWithWeekday
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * Every day with something attended on it, best first.
 *
 * The counterpart to [AbsenceDaysScreen], and deliberately the same page in reverse:
 * same ranks, same rows, same shape. A student who has read one will not have to work
 * out how to read the other.
 *
 * Ranked by the fraction of the day attended rather than the count, because a count
 * would simply list the longest days — six out of nine is not a better day than four
 * out of four, and a ranking that said so would be measuring the timetable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendedDaysScreen(
    onBack: () -> Unit,
    viewModel: AttendedDaysViewModel = hiltViewModel(),
) {
    val days by viewModel.days.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Days with the most present") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (days.isEmpty()) {
            EmptyState(
                title = "Nothing attended yet",
                body = "Days you turned up to appear here once attendance has been loaded.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(days) { index, day ->
                AttendedDayCard(rank = index + 1, day = day)
            }
            item { Gap(2) }
        }
    }
}

/** One ranked day. */
@Composable
private fun AttendedDayCard(rank: Int, day: AttendedDay) {
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
                    // The count behind the fraction, since the fraction alone cannot
                    // tell a full day from a single morning class.
                    if (day.wholeDay) {
                        "All ${day.recorded} ${classWord(day.recorded)} attended"
                    } else {
                        "${day.attended} of ${day.recorded} classes"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(6)
                AttendanceBar(
                    fraction = (day.percent / 100.0).toFloat(),
                    color = if (day.wholeDay) colors.safe else colors.watch,
                    modifier = Modifier.fillMaxWidth(0.75f),
                )
            }
            Text(
                day.percent.asPercentLabel1dp(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (day.wholeDay) colors.safe else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun classWord(count: Int): String = if (count == 1) "class" else "classes"
