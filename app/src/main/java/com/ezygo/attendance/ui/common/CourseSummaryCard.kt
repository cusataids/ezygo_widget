package com.ezygo.attendance.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.domain.model.CourseStats
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * One course in full: the percentage, the band, the bar and the counts behind it.
 *
 * This is the block at the top of a course's detail page, lifted out so the
 * attendance area can list the same card rather than a smaller imitation of it. Two
 * renderings of "how this course is going" would be two things to keep in step, and
 * the list is a way into the detail page — it should look like where it leads.
 *
 * [showName] is the one difference between the two uses. On the detail page the top
 * app bar already carries the course name, so repeating it in the card below would
 * say it twice; in a list of cards it is the only thing telling them apart.
 */
@Composable
fun CourseSummaryCard(
    course: CourseStats,
    targetPercent: Int,
    modifier: Modifier = Modifier,
    showName: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val stats = course.stats
    val color = LocalStatusColors.current.forBand(stats.band)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (showName) {
                Text(
                    course.courseName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Gap(6)
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    stats.percentLabel(),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                course.courseCode?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                    )
                }
                Box(Modifier.weight(1f))
                BandChip(stats.band)
            }
            Gap(10)
            AttendanceBar(
                fraction = ((stats.percentage ?: 0.0) / 100.0).toFloat(),
                color = color,
                modifier = Modifier.fillMaxWidth(),
            )
            Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                CourseCount("Present", stats.present)
                CourseCount("Absent", stats.absent)
                CourseCount("Duty leave", stats.dutyLeave)
                CourseCount("Total", stats.total)
            }
            Gap(10)
            Text(
                stats.shortageMessage(targetPercent),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun CourseCount(label: String, value: Int) {
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
