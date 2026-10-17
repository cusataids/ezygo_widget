package com.ezygo.attendance.ui.miniapps.whatif

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.ClaimDay
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.asPercentLabel1dp
import com.ezygo.attendance.ui.theme.LocalStatusColors
import java.util.Locale

/**
 * The attendance simulator.
 *
 * Every figure on this screen is arithmetic on classes the student has actually had,
 * extended by classes they say they will attend or miss. It forecasts nothing, which
 * is why it can be trusted to the decimal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatIfScreen(
    onBack: () -> Unit,
    viewModel: WhatIfViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(false) }

    // Which step of adding a claim is open, if any. Held here and not in the view
    // model because it is about what is on screen; the claim itself lives in the
    // state, which is why closing a dialog mid-claim loses nothing.
    var claimEntry by remember { mutableStateOf(false) }
    var claimCalendar by remember { mutableStateOf(false) }
    var claimSubjectPicker by remember { mutableStateOf(false) }
    var claimSubject by remember { mutableStateOf<ClaimCourse?>(null) }
    var claimDetails by remember { mutableStateOf<ClaimDay?>(null) }
    var explainingClaim by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("What if") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Always offered, matching the SGPA estimator. It used to appear
                    // only once something had changed, which made it a button that
                    // came and went; now that the work is saved between visits, the
                    // way to discard it has to be somewhere predictable.
                    TextButton(onClick = viewModel::resetAll) { Text("Reset") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The semester average is the answer whether or not anything has been
            // added yet, so the card leads regardless: with nothing added it simply
            // reads as where the student stands today.
            if (state.semesterCourses.isNotEmpty()) {
                item { AverageCard(state) }
            }

            if (state.subjects.isEmpty()) {
                // The add control stands where the subjects will: with nothing else on
                // screen it is the only thing to do, so it is the thing you see.
                item { AddSubjectButton(large = true, onClick = { picking = true }) }
            } else {
                item { SectionHeader("Subjects", trailing = "${state.subjects.size}") }
                items(state.subjects, key = { it.key }) { subject ->
                    SubjectCard(
                        subject = subject,
                        onAttend = { viewModel.setAttend(subject.key, it) },
                        onMiss = { viewModel.setMiss(subject.key, it) },
                        onRemove = { viewModel.remove(subject.key) },
                        onExplainClaim = { explainingClaim = true },
                    )
                }
                // Once there is a list, adding is one more thing you can do rather than
                // the only one, so it shrinks and follows what is already there.
                item { AddSubjectButton(large = false, onClick = { picking = true }) }
            }

            if (state.semesterCourses.isNotEmpty()) {
                item {
                    SectionHeader(
                        "Claims",
                        trailing = state.claimedSessions.size
                            .takeIf { it > 0 }
                            ?.let { if (it == 1) "1 session" else "$it sessions" },
                    )
                }
                items(state.claims, key = { it.dateKey }) { claim ->
                    ClaimSummaryRow(
                        claim = claim,
                        covered = viewModel.coveredBy(claim),
                        onRemove = { viewModel.removeClaimDate(claim.date) },
                        onDetails = { claimDetails = claim },
                        onClick = { claimCalendar = true },
                    )
                }
                item {
                    if (state.claims.isEmpty()) {
                        AddClaimButton(onClick = { claimEntry = true })
                    } else {
                        // Side by side only once there is something to clear. Adding
                        // and clearing are opposite enough that putting them on one
                        // line needs the full width between them.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AddClaimButton(
                                onClick = { claimEntry = true },
                                modifier = Modifier.weight(1f),
                            )
                            ClearClaimsButton(
                                onClick = viewModel::clearClaims,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }

    claimDetails?.let { claim ->
        ClaimDetailsDialog(
            claim = claim,
            sessions = viewModel.sessionsClaimedOn(claim),
            onDismiss = { claimDetails = null },
        )
    }

    if (explainingClaim) {
        ClaimedSubjectDialog(onDismiss = { explainingClaim = false })
    }

    if (claimEntry) {
        ClaimEntryDialog(
            onPick = { entry ->
                claimEntry = false
                when (entry) {
                    ClaimEntry.BY_DATE -> claimCalendar = true
                    ClaimEntry.BY_SUBJECT -> claimSubjectPicker = true
                }
            },
            onDismiss = { claimEntry = false },
        )
    }

    if (claimCalendar) {
        ClaimCalendarDialog(
            claims = state.claims,
            absenceDates = viewModel.absenceDates(),
            sessionsFor = viewModel::sessionsOnDate,
            coveredFor = viewModel::coveredBy,
            onAddDate = viewModel::addClaimDate,
            onRemoveDate = viewModel::removeClaimDate,
            onSetType = viewModel::setClaimType,
            onToggleSession = viewModel::toggleClaimSession,
            onDone = { claimCalendar = false },
        )
    }

    if (claimSubjectPicker) {
        ClaimCoursePickerDialog(
            courses = viewModel.coursesWithAbsences(),
            onPick = {
                claimSubject = it
                claimSubjectPicker = false
            },
            onDismiss = { claimSubjectPicker = false },
        )
    }

    claimSubject?.let { course ->
        ClaimSubjectDialog(
            courseName = course.name,
            absences = viewModel.absencesFor(course.courseId),
            alreadyClaimed = state.claimedSessions.mapTo(mutableSetOf()) {
                it.dateKey to it.sessionId
            },
            onConfirm = { records ->
                viewModel.claimRecords(records)
                claimSubject = null
            },
            onDismiss = { claimSubject = null },
        )
    }

    if (picking) {
        CoursePickerDialog(
            available = state.available,
            onPick = {
                viewModel.addCourse(it)
                picking = false
            },
            onOther = {
                picking = false
                manual = true
            },
            onDismiss = { picking = false },
        )
    }

    if (manual) {
        ManualSubjectDialog(
            onAdd = { name, attended, total ->
                viewModel.addManual(name, attended, total)
                manual = false
            },
            onDismiss = { manual = false },
        )
    }
}

/**
 * Add a subject, at two sizes.
 *
 * One control rather than two: the same action should not move between an app-bar icon
 * and a button in the list depending on what happens to be on screen.
 */
@Composable
private fun AddSubjectButton(large: Boolean, onClick: () -> Unit) {
    if (large) {
        // A dashed outline reads as a slot waiting to be filled rather than as a
        // button floating in empty space, which is what the bare circle looked like.
        val stroke = MaterialTheme.colorScheme.outline
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            modifier = Modifier.fillMaxWidth().height(148.dp),
        ) {
            Box(
                modifier = Modifier.drawBehind {
                    drawRoundRect(
                        color = stroke,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
                        ),
                        cornerRadius = CornerRadius(20.dp.toPx()),
                    )
                },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Gap(10)
                    Text(
                        "Add a subject",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        return
    }

    // Below a list it is one more action rather than the only one, so it states what
    // it does in a line instead of taking a block of its own.
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "  Add another subject",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

// ---- the answer ----------------------------------------------------------

/**
 * Where the average lands, which is what the whole screen is asking.
 *
 * The figure on the left is the one the home screen shows — the average over every
 * enrolled subject, not over the handful added below — until a subject is entered by
 * hand, at which point it covers that too and the heading says so. A hand-entered
 * subject is one the student is taking and the portal has not got; leaving it out of
 * the answer made the card ignore the very thing the user had just added.
 */
@Composable
private fun AverageCard(state: WhatIfUiState) {
    val colors = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                // "Semester" is dropped once a hand-entered subject is in the mix:
                // the number is then no longer the semester's as the portal has it,
                // and the heading should not claim otherwise.
                if (state.manualSubjects > 0) "Average attendance" else "Semester average attendance",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(6)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.currentAverage.asPercentLabel1dp(),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (state.hasChanges) {
                    Text(
                        "  →  ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        state.projectedAverage.asPercentLabel1dp(),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor(state.averageDelta, colors.safe, colors.risk),
                    )
                }
            }
            val delta = state.averageDelta
            Gap(4)
            Text(
                buildString {
                    if (state.hasChanges && delta != null) {
                        append(deltaLabel(delta))
                        append(" across ")
                    } else {
                        append("Across ")
                    }
                    append(state.averagedSubjects)
                    append(if (state.averagedSubjects == 1) " subject" else " subjects")
                    // Which subjects, said plainly: the count is otherwise one more
                    // than the semester has and there is nothing on screen to explain
                    // where the extra came from.
                    if (state.manualSubjects > 0) {
                        append(", including ")
                        append(state.manualSubjects)
                        append(" entered by hand")
                    } else {
                        append(" this semester")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- one subject ---------------------------------------------------------

@Composable
private fun SubjectCard(
    subject: WhatIfSubject,
    onAttend: (Int) -> Unit,
    onMiss: (Int) -> Unit,
    onRemove: () -> Unit,
    onExplainClaim: () -> Unit,
) {
    val colors = LocalStatusColors.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            subject.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        // Marks a row a claim put here, without making the row look
                        // like a different kind of thing: same card, same controls,
                        // one small glyph that explains itself when tapped.
                        if (subject.fromClaim) {
                            IconButton(
                                onClick = onExplainClaim,
                                modifier = Modifier.size(28.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = "Why this subject is listed",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    Text(
                        "${subject.attended} of ${subject.total} so far" +
                            if (subject.isManual) " · entered by hand" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove " + subject.name,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Gap(8)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    subject.currentPercent.asPercentLabel1dp(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (subject.hasChanges) {
                    Text(
                        "  →  ",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        subject.projectedPercent.asPercentLabel1dp(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = deltaColor(subject.deltaPoints, colors.safe, colors.risk),
                    )
                }
            }

            Gap(10)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Gap(10)

            Stepper(
                label = "Will attend",
                value = subject.attend,
                onChange = onAttend,
                tint = colors.safe,
            )
            Gap(6)
            Stepper(
                label = "Will miss",
                value = subject.miss,
                onChange = onMiss,
                tint = colors.risk,
            )
        }
    }
}

/** Plus and minus rather than a text field: the numbers here are small and exact. */
@Composable
private fun Stepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    tint: Color,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = { onChange(value - 1) },
            enabled = value > 0,
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "One fewer", Modifier.size(16.dp))
        }
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (value > 0) tint else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange(value + 1) },
            modifier = Modifier.size(32.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "One more", Modifier.size(16.dp))
        }
    }
}

// ---- adding --------------------------------------------------------------

@Composable
private fun CoursePickerDialog(
    available: List<WhatIfCandidate>,
    onPick: (WhatIfCandidate) -> Unit,
    onOther: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a subject") },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                if (available.isEmpty()) {
                    Text(
                        "Every subject from your portal is already here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    available.forEach { course ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(course) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text(course.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (course.total == 0) {
                                    "No classes recorded yet"
                                } else {
                                    "${course.attended} of ${course.total} attended"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Gap(6)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                // For a subject the portal has never heard of, which is the case a
                // list of the student's own courses cannot cover.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOther)
                        .padding(vertical = 10.dp),
                ) {
                    Text("Other subject", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Enter the attended and total counts yourself",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ManualSubjectDialog(
    onAdd: (String, Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var attended by remember { mutableStateOf("") }
    var total by remember { mutableStateOf("") }

    // Blank counts as zero, so a course that has not met yet can be added by typing
    // only its name. The one rule left is the one that is actually impossible: being
    // marked present more often than the class was held.
    val attendedValue = attended.toIntOrNull() ?: 0
    val totalValue = total.toIntOrNull() ?: 0
    val valid = attendedValue <= totalValue

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Other subject") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(10)
                OutlinedTextField(
                    value = attended,
                    onValueChange = { attended = it.filter(Char::isDigit).take(4) },
                    label = { Text("Classes attended") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(10)
                OutlinedTextField(
                    value = total,
                    onValueChange = { total = it.filter(Char::isDigit).take(4) },
                    label = { Text("Classes held") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                // Only the impossible case is worth saying; the rest of the form
                // explains itself.
                if (attendedValue > totalValue) {
                    Gap(6)
                    Text(
                        "Attended cannot be more than held.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name, attendedValue, totalValue) },
                enabled = valid,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- labels --------------------------------------------------------------

private fun deltaLabel(delta: Double): String = when {
    delta > 0.05 -> String.format(Locale.getDefault(), "Up %.1f points", delta)
    delta < -0.05 -> String.format(Locale.getDefault(), "Down %.1f points", -delta)
    else -> "No change"
}

@Composable
private fun deltaColor(delta: Double?, up: Color, down: Color): Color = when {
    delta == null -> MaterialTheme.colorScheme.onSurface
    delta > 0.05 -> up
    delta < -0.05 -> down
    else -> MaterialTheme.colorScheme.onSurface
}
