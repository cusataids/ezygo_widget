package com.ezygo.attendance.ui.timetable

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.data.repo.CourseShortCodeRepository
import com.ezygo.attendance.domain.model.SavedCourse
import com.ezygo.attendance.domain.model.ScheduleSlot
import com.ezygo.attendance.domain.model.TimetableDay
import com.ezygo.attendance.domain.model.TimetableOverride
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

/**
 * The course mapping table: one short code per course.
 *
 * Only this semester's courses. A code given to a course from another semester is kept
 * and comes back on its own when that semester is selected again — but listing them
 * here would mean a screen headed by one semester quietly showing another's subjects.
 *
 * Edited in place with no save button, because there is nothing to save: each field
 * writes as it is typed, and clearing one removes the mapping and puts the full name
 * back on the grid.
 */
@Composable
fun CourseMappingDialog(
    courses: List<MappableCourse>,
    errors: Map<String, String>,
    onSet: (MappableCourse, String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Course short codes") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                if (courses.isEmpty()) {
                    Text(
                        "No courses loaded yet. Pull down on the timetable to fetch them.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                courses.forEach { course ->
                    MappingRow(
                        course = course,
                        error = errors[course.courseKey],
                        onSet = { onSet(course, it) },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * One course and its code.
 *
 * The field holds its own [TextFieldValue] rather than reading the stored code back on
 * every keystroke. Rendering a value that has been to the database and back means the
 * text field is momentarily a character behind the keyboard, and the IME corrects for
 * that by moving the cursor — which is what made it jump left mid-word. Typing is local
 * and immediate; the write goes outward and nothing comes back to fight it.
 *
 * Keeping the whole [TextFieldValue] rather than a plain string is what preserves the
 * selection through the length clamp, including on keyboards that replace a composing
 * region wholesale instead of appending one character at a time.
 */
@Composable
private fun MappingRow(course: MappableCourse, error: String?, onSet: (String) -> Unit) {
    var field by remember(course.courseKey) {
        val initial = course.shortCode.orEmpty()
        mutableStateOf(TextFieldValue(initial, TextRange(initial.length)))
    }

    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f).padding(end = 10.dp, top = 8.dp)) {
            Text(course.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            course.courseCode?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedTextField(
            value = field,
            onValueChange = { typed ->
                val text = typed.text.take(CourseShortCodeRepository.MAX_LENGTH)
                field = if (text == typed.text) {
                    typed
                } else {
                    // Clamped: the selection has to come back inside the text it
                    // describes, or the field throws on the next frame.
                    typed.copy(
                        text = text,
                        selection = TextRange(typed.selection.end.coerceAtMost(text.length)),
                    )
                }
                onSet(text)
            },
            label = { Text("Code") },
            singleLine = true,
            isError = error != null,
            supportingText = error?.let {
                {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            modifier = Modifier.width(130.dp),
        )
    }
}

/**
 * What a grid cell could not say.
 *
 * The grid holds a short code and nothing else, which is the point of it — this is
 * where the rest lives, and it is why tapping a filled period opens a card rather than
 * throwing the reader back to the day list to find the same class again.
 */
@Composable
fun SlotDetailDialog(
    day: TimetableDay,
    slot: ScheduleSlot,
    onEdit: () -> Unit,
    onRemoveOverride: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(slot.courseName ?: slot.sessionName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("Course code", slot.courseCode)
                DetailLine("Course name", slot.courseName)
                DetailLine(
                    "Teacher",
                    slot.instructors.joinToString(", ").takeIf { it.isNotBlank() },
                )
                DetailLine(
                    "When",
                    day.weekdayName + " · " + slot.sessionName,
                )
                slot.cancelledReason?.let {
                    Text(
                        "Cancelled: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                // Editing a period the portal published writes an override over it,
                // which is the only way to correct a published class at all.
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
        dismissButton = {
            // Only for a class the user put there. The portal's own rows are not this
            // screen's to delete.
            if (onRemoveOverride != null) {
                TextButton(onClick = onRemoveOverride) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String?) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Places or corrects a class of the user's own.
 *
 * Opened two ways. From a free cell on the grid, [fixedDay] and [fixedSession] are set
 * and the day and period are not asked for — the cell that was tapped already names
 * them both, and re-asking would put a form between a tap and the thing it plainly
 * meant. From the list of your classes there is no such cell, so both are chosen here.
 *
 * The published row for that period is replaced rather than joined, since an override
 * exists precisely because the published one is wrong. It is a standing weekly change,
 * so it applies to that period on that weekday every week.
 */
@Composable
fun OverrideEditorDialog(
    /** The class being corrected, or null when placing a new one. */
    existing: TimetableOverride?,
    fixedDay: DayOfWeek?,
    fixedSession: Int?,
    fixedLabel: String?,
    periods: List<Pair<Int, String>>,
    choices: List<SavedCourse>,
    onForgetCourse: (SavedCourse) -> Unit,
    onSave: (TimetableOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    var day by remember(existing) {
        mutableStateOf(fixedDay ?: existing?.dayOfWeek ?: DayOfWeek.MONDAY)
    }
    var sessionId by remember(existing, periods) {
        mutableStateOf(fixedSession ?: existing?.sessionId ?: periods.firstOrNull()?.first)
    }
    var name by remember(existing) { mutableStateOf(existing?.courseName.orEmpty()) }
    var code by remember(existing) { mutableStateOf(existing?.courseCode.orEmpty()) }
    var shortCode by remember(existing) { mutableStateOf(existing?.shortCode.orEmpty()) }
    var teacher by remember(existing) { mutableStateOf(existing?.instructor.orEmpty()) }

    val fixed = fixedDay != null && fixedSession != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add a class" else "Edit class") },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (fixed) {
                    Text(
                        fixedLabel.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    PickerButton(day.getDisplayName(TextStyle.FULL, Locale.getDefault())) { close ->
                        DayOfWeek.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(option.getDisplayName(TextStyle.FULL, Locale.getDefault()))
                                },
                                onClick = {
                                    day = option
                                    close()
                                },
                            )
                        }
                    }
                    PickerButton(
                        periods.firstOrNull { it.first == sessionId }?.second ?: "Period",
                    ) { close ->
                        periods.forEach { (id, periodName) ->
                            DropdownMenuItem(
                                text = { Text(periodName) },
                                onClick = {
                                    sessionId = id
                                    close()
                                },
                            )
                        }
                    }
                }

                // Every course described before, plus the way out for one that has
                // not been. Choosing fills the whole form rather than only the name.
                PickerButton(
                    if (name.isBlank()) "Choose a course" else name,
                ) { close ->
                    choices.forEach { course ->
                        DropdownMenuItem(
                            text = { Text(course.name) },
                            trailingIcon = {
                                // Only for a course the user invented. The portal's own
                                // would come back with the next refresh, so a cross on
                                // them would be a button that does nothing.
                                if (course.stored) {
                                    IconButton(onClick = { onForgetCourse(course) }) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Forget " + course.name,
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            },
                            onClick = {
                                name = course.name
                                code = course.code.orEmpty()
                                shortCode = course.shortCode.orEmpty()
                                teacher = course.teacher.orEmpty()
                                close()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Custom course…") },
                        onClick = {
                            name = ""
                            code = ""
                            shortCode = ""
                            teacher = ""
                            close()
                        },
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Course name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Course code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = shortCode,
                    onValueChange = {
                        shortCode = it.take(CourseShortCodeRepository.MAX_LENGTH)
                    },
                    label = { Text("Short code") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("Teacher name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && sessionId != null,
                onClick = {
                    val session = sessionId ?: return@TextButton
                    onSave(
                        TimetableOverride(
                            dayOfWeek = day,
                            sessionId = session,
                            courseId = existing?.courseId,
                            courseName = name.trim(),
                            courseCode = code.trim().takeIf { it.isNotBlank() },
                            shortCode = shortCode.trim().takeIf { it.isNotBlank() },
                            instructor = teacher.trim().takeIf { it.isNotBlank() },
                            deliveryMethod = existing?.deliveryMethod,
                            classType = existing?.classType,
                        ),
                    )
                },
            ) { Text(if (existing == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A labelled button that opens a menu; the menu closes itself on any choice. */
@Composable
private fun PickerButton(label: String, content: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, modifier = Modifier.weight(1f), maxLines = 1)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            content { open = false }
        }
    }
}

/**
 * The classes the user has placed: add one, correct one, take one off.
 *
 * The grid can only offer a free period, so this is where a class is added on a day
 * that already has something on it, and the only place one can be edited at all.
 */
@Composable
fun OverridesDialog(
    overrides: List<TimetableOverride>,
    periods: List<Pair<Int, String>>,
    onAdd: () -> Unit,
    onEdit: (TimetableOverride) -> Unit,
    onRemove: (TimetableOverride) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your classes") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                overrides.forEach { override ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onEdit(override) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                override.courseName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                            )
                            Text(
                                override.dayOfWeek
                                    .getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                                    " · " +
                                    (periods.firstOrNull { it.first == override.sessionId }?.second
                                        ?: "Period ${override.sessionId}"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onRemove(override) }) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Remove this class",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                TextButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("  Add a class")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
