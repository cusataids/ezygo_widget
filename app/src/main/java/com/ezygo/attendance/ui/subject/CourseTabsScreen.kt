package com.ezygo.attendance.ui.subject

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.AttendancePlan
import com.ezygo.attendance.ui.common.CourseSummaryCard
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.asPercentLabel1dp
import com.ezygo.attendance.ui.theme.LocalStatusColors

/**
 * Where the home screen's average card leads: the figures behind it, then the
 * subjects behind those.
 *
 * This replaced a tab strip carrying one full subject page per tab. That put the
 * whole subject screen inside a pager, so arriving here meant landing in whichever
 * subject happened to be first and swiping to find the one you wanted. A list that
 * opens the subject page is the same information with the choosing handed back to
 * the reader — and it reuses the detail page rather than re-hosting it.
 *
 * The summary cards sit above the list because they are what the average card
 * promised; the list is how you get past them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseTabsScreen(
    onBack: () -> Unit,
    onOpenCourse: (Int) -> Unit,
    onOpenClassStats: () -> Unit,
    onOpenAbsenceDays: () -> Unit,
    onOpenAttendedDays: () -> Unit,
    viewModel: CourseTabsViewModel = hiltViewModel(),
) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val plan by viewModel.plan.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attendance") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val data = summary
        if (data == null || data.courses.isEmpty()) {
            EmptyState(
                title = "No subjects yet",
                body = "Subjects appear here once attendance has been loaded for this period.",
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                // Two across rather than three: at phone widths three columns leave
                // each percentage about forty points wide, and a figure that has to
                // wrap is not a summary. The third card takes the full width below,
                // which also suits it being the only one that opens anything.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    // Only one of the two carries a caption, so left to themselves the
                    // cards would end at different heights and the pair would look
                    // misaligned rather than deliberately different. Measuring the row
                    // to its taller child and filling both to it keeps the bottom edge
                    // straight whatever either card ends up containing.
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    MetricCard(
                        label = "Average attendance",
                        value = data.averagePercent.asPercentLabel1dp(),
                        valueColor = LocalStatusColors.current.forBand(data.averageBand),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                    MetricCard(
                        label = "Overall attendance",
                        value = data.overallPercent.asPercentLabel1dp(),
                        caption = "${data.attended} of ${data.conducted} classes",
                        // Uncoloured on purpose: on this screen colour means "against
                        // your target", and that judgement belongs to the average.
                        valueColor = null,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }

            item { ClassStatsCard(data, onClick = onOpenClassStats) }

            plan?.let { item { CatchUpCard(it) } }

            // Two halves of the same question, in the same shape as the class card
            // above them: a heading, a line, and the whole story a tap away. The
            // worst days first, because that is the list anybody opens first.
            item { DayRankCard(DayRank.ABSENT, onClick = onOpenAbsenceDays) }
            item { DayRankCard(DayRank.PRESENT, onClick = onOpenAttendedDays) }

            item { SectionHeader("Subjects", trailing = "${data.courses.size}") }

            items(data.courses, key = { it.courseId }) { course ->
                // The detail page's own header card, not the compact home row: this
                // list is the way into those pages, and a card that matches where it
                // leads makes the journey obvious.
                CourseSummaryCard(
                    course = course,
                    targetPercent = data.targetPercent,
                    showName = true,
                    onClick = { onOpenCourse(course.courseId) },
                )
            }
        }
    }
}

/**
 * The two day rankings, as the one card they share a shape with.
 *
 * Both are the same object — a heading, a line, a chevron — so they are one composable
 * taking which of the two it is. Written as two copies they would be two places for
 * the padding to drift apart.
 */
private enum class DayRank(val heading: String, val line: String) {
    ABSENT(
        heading = "Days with the most absences",
        line = "If you're looking for which days you need to forge a medical " +
            "certificate, this is the place.",
    ),
    PRESENT(
        heading = "Days with the most present",
        line = "If you're looking for proof that your college fees weren't entirely " +
            "a donation, this is the place.",
    ),
}

@Composable
private fun DayRankCard(rank: DayRank, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    rank.heading,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    rank.line,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/**
 * One figure and what it means.
 *
 * Deliberately plain: these sit side by side and the only thing that should separate
 * them is the number, so they share a shape, a weight and a type scale.
 */
@Composable
private fun MetricCard(
    label: String,
    value: String,
    valueColor: Color?,
    modifier: Modifier = Modifier,
    /** Omitted where the figure speaks for itself and a gloss only adds noise. */
    caption: String? = null,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = modifier,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Gap(4)
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            caption?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

/**
 * How the cohort is doing, in one line, and the way through to the rest.
 *
 * Only a headline here. The full picture — per subject, over time, best and worst —
 * is a page of its own, because none of it fits in a card this size without becoming
 * a wall of small numbers.
 */
@Composable
private fun ClassStatsCard(summary: AttendanceSummary, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "How is the class doing?",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (summary.classAveragePercent == null) {
                        "No cohort attendance recorded yet"
                    } else {
                        summary.classAveragePercent.asPercentLabel1dp() +
                            " class average · ${summary.classMeetings} classes"
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

/**
 * What it would take to get back to target, as a number and a short plan.
 *
 * Three states, because the honest answer differs: already there, a route back, or
 * nothing recorded to plan from. Only the middle one is a list of subjects.
 */
@Composable
private fun CatchUpCard(plan: AttendancePlan) {
    val colors = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            when {
                plan.alreadyMet -> {
                    Text(
                        "You are at or above ${plan.targetPercent}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = colors.safe,
                    )
                    Text(
                        "Across ${plan.courseCount} " +
                            if (plan.courseCount == 1) "subject" else "subjects",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                !plan.hasPlan -> Text(
                    "Not enough recorded attendance to work out a plan yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            plan.classesNeeded.toString(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.risk,
                        )
                        Text(
                            if (plan.classesNeeded == 1) {
                                "  more class to reach ${plan.targetPercent}%"
                            } else {
                                "  more classes to reach ${plan.targetPercent}%"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(bottom = 3.dp),
                        )
                    }
                    Text(
                        // Said plainly: this is the fewest, not merely one answer.
                        "The fewest possible, attending these in turn",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(8)
                    plan.perCourse.take(PLAN_SUBJECTS_SHOWN).forEach { need ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                            Text(
                                need.courseName,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "+${need.classes}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    val rest = plan.perCourse.drop(PLAN_SUBJECTS_SHOWN)
                    if (rest.isNotEmpty()) {
                        HorizontalDivider(
                            Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        Text(
                            "and +${rest.sumOf { it.classes }} across ${rest.size} " +
                                if (rest.size == 1) "other subject" else "other subjects",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Enough to act on without the card running away down the screen. */
private const val PLAN_SUBJECTS_SHOWN = 3
