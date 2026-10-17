package com.ezygo.attendance.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.domain.model.CourseStats
import com.ezygo.attendance.domain.model.RiskBand
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * One course, as a row you can open.
 *
 * Lived on the home screen until the attendance area needed the same thing. Shared
 * rather than copied: both lists are "the courses, with where each one stands", and
 * two versions of that would be two places for the figure or the wording to drift.
 *
 * It renders a row, not a card — the caller supplies the surface it sits on, which is
 * what lets the home screen group them inside one card while the attendance area
 * spaces them out.
 */
@Composable
fun CourseAttendanceRow(course: CourseStats, targetPercent: Int, onClick: () -> Unit) {
    val stats = course.stats
    val color = LocalStatusColors.current.forBand(stats.band)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                course.courseName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
            )
            // The actionable line, not the raw tally: how many classes to attend to
            // recover, or how many can still be missed.
            Text(
                if (stats.hasData) {
                    stats.shortageMessage(targetPercent)
                } else {
                    "No classes recorded"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (stats.band == RiskBand.AT_RISK) color else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (stats.band == RiskBand.AT_RISK) FontWeight.Medium else FontWeight.Normal,
            )
            Gap(6)
            AttendanceBar(
                fraction = ((stats.percentage ?: 0.0) / 100.0).toFloat(),
                color = color,
                modifier = Modifier.fillMaxWidth(0.75f),
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                stats.percentLabel(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Gap(3)
            BandChip(stats.band)
        }
        // Standard Android affordance that the row opens something.
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}
