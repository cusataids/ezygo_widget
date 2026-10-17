package com.ezygo.attendance.ui.miniapps.sgpa

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.SectionHeader
import java.util.Locale

/**
 * SGPA from course types and grades, and what it does to the CGPA.
 *
 * Everything recalculates as it is entered: the figures at the top are a plain function
 * of the rows below them, so there is no button to press and nothing to be out of date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SgpaScreen(
    onBack: () -> Unit,
    viewModel: SgpaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("SGPA estimator") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::clearAll) { Text("Reset") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = viewModel::addCourse,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Course") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { ResultCard(state) }

            if (state.pastResults.isNotEmpty()) {
                item { CgpaImpactCard(state) }
            }

            item { SectionHeader("Courses", trailing = "${state.courses.size}") }

            items(state.courses, key = { it.id }) { course ->
                CourseCard(
                    course = course,
                    known = viewModel.availableNames(course),
                    canRemove = state.courses.size > 1,
                    onPickName = { viewModel.setName(course.id, it) },
                    onCustomName = { viewModel.setCustomName(course.id, it) },
                    onTypedName = { viewModel.setTypedName(course.id, it) },
                    onType = { viewModel.setType(course.id, it) },
                    onEntry = { viewModel.setEntry(course.id, it) },
                    onMarks = { viewModel.setMarks(course.id, it) },
                    onGrade = { viewModel.setGrade(course.id, it) },
                    onRemove = { viewModel.removeCourse(course.id) },
                )
            }

            item { Gap(72) }
        }
    }
}

@Composable
private fun ResultCard(state: SgpaUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "SGPA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(4)
            Text(
                state.sgpa?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "--",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(4)
            Text(
                text = if (state.sgpa == null) {
                    "Pick a course type and enter either a mark or a grade."
                } else {
                    val credits = String.format(Locale.getDefault(), "%.0f", state.totalCredits)
                    "${state.countedCourses} of ${state.courses.size} courses · $credits credits" +
                        if (state.incompleteCourses > 0) " · rest incomplete" else ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Where the CGPA lands if this semester turns out as entered.
 *
 * Only shown once a grade card has been read: without a history there is no CGPA to
 * move, and a "projected CGPA" equal to the SGPA would say nothing while looking as
 * though it said something.
 */
@Composable
private fun CgpaImpactCard(state: SgpaUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "CGPA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(4)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    state.currentCgpa?.let { String.format(Locale.getDefault(), "%.2f", it) } ?: "--",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                val projected = state.projectedCgpa
                if (projected != null) {
                    Text(
                        "  \u2192  ",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        String.format(Locale.getDefault(), "%.2f", projected),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Gap(4)
            val delta = state.cgpaDelta
            Text(
                text = when {
                    delta == null ->
                        "Enter this semester's marks to see where the CGPA lands."
                    delta > 0.005 ->
                        String.format(Locale.getDefault(), "Up %.2f", delta) +
                            " over ${state.pastResults.size} completed " +
                            if (state.pastResults.size == 1) "semester" else "semesters"
                    delta < -0.005 ->
                        String.format(Locale.getDefault(), "Down %.2f", -delta) +
                            " over ${state.pastResults.size} completed " +
                            if (state.pastResults.size == 1) "semester" else "semesters"
                    else -> "No change to the CGPA"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CourseCard(
    course: SgpaCourse,
    known: List<String>,
    canRemove: Boolean,
    onPickName: (String) -> Unit,
    onCustomName: (Boolean) -> Unit,
    onTypedName: (String) -> Unit,
    onType: (CourseType?) -> Unit,
    onEntry: (ScoreEntry) -> Unit,
    onMarks: (String) -> Unit,
    onGrade: (Grade?) -> Unit,
    onRemove: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    if (course.customName) {
                        OutlinedTextField(
                            value = course.name,
                            onValueChange = onTypedName,
                            label = { Text("Course name") },
                            singleLine = true,
                            trailingIcon = {
                                TextButton(onClick = { onCustomName(false) }) { Text("List") }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        CourseNamePicker(
                            selected = course.name,
                            known = known,
                            onPick = onPickName,
                            onCustom = { onCustomName(true) },
                        )
                    }
                }
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Remove course",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // The credits follow from the type, so the type is what is asked for.
            CourseTypePicker(selected = course.type, onSelect = onType)

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ScoreEntry.entries.forEachIndexed { index, entry ->
                    SegmentedButton(
                        selected = course.entry == entry,
                        onClick = { onEntry(entry) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = ScoreEntry.entries.size,
                        ),
                        label = { Text(if (entry == ScoreEntry.MARK) "Mark" else "Grade") },
                    )
                }
            }

            when (course.entry) {
                ScoreEntry.MARK -> OutlinedTextField(
                    value = course.marks,
                    onValueChange = onMarks,
                    label = { Text("Mark out of 100") },
                    singleLine = true,
                    isError = course.hasBadMark,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Chips rather than a dropdown: six grades fit across a phone, and one
                // tap beats opening a menu to choose between six things.
                ScoreEntry.GRADE -> Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Grade.entries.forEach { grade ->
                        FilterChip(
                            selected = course.grade == grade,
                            onClick = { onGrade(grade) },
                            label = { Text(grade.letter) },
                        )
                    }
                }
            }

            val effective = course.effectiveGrade
            Text(
                text = when {
                    course.hasBadMark -> "A mark has to be between 0 and 100."
                    course.type == null -> "Pick a course type for its credits."
                    effective == null -> "No grade yet."
                    course.entry == ScoreEntry.MARK ->
                        "Mark ${course.marks} → ${effective.letter} · ${effective.label} · " +
                            String.format(Locale.getDefault(), "%.1f", effective.weight)
                    else ->
                        "${effective.letter} · ${effective.label} · " +
                            String.format(Locale.getDefault(), "%.1f", effective.weight)
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (course.hasBadMark) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun CourseNamePicker(
    selected: String,
    known: List<String>,
    onPick: (String) -> Unit,
    onCustom: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected.ifBlank { "Choose a course" },
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            known.forEach { name ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onPick(name)
                        open = false
                    },
                )
            }
            // For anything the portal has never heard of: an elective taken elsewhere,
            // a course from a previous semester, a grade being planned rather than sat.
            DropdownMenuItem(
                text = { Text("Custom…") },
                onClick = {
                    onCustom()
                    open = false
                },
            )
        }
    }
}

@Composable
private fun CourseTypePicker(selected: CourseType?, onSelect: (CourseType?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(
                selected?.creditLabel ?: "Course type",
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CourseType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.creditLabel) },
                    onClick = {
                        onSelect(type)
                        open = false
                    },
                )
            }
        }
    }
}
