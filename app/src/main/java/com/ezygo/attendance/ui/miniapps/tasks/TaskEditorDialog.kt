package com.ezygo.attendance.ui.miniapps.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ezygo.attendance.domain.model.Task
import com.ezygo.attendance.ui.common.ConfirmDeleteDialog
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.dueLabel
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One task: the same dialog for writing a new one and for correcting an old one.
 *
 * [task] is null when adding, which is the only difference between the two — a task is
 * described the same way either way, and a deadline can be given at the moment it is
 * created rather than only afterwards.
 *
 * Only the user's own tasks are ever edited here. A synced task's wording and deadline
 * belong upstream, and an edit would be undone by the next sync, so its row does not
 * open this at all.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorDialog(
    task: Task?,
    onSave: (title: String, detail: String?, dueEpochMs: Long?, isAllDay: Boolean) -> Unit,
    /** Null while adding: there is nothing yet to delete. */
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val key = task?.id
    var title by remember(key) { mutableStateOf(task?.title.orEmpty()) }
    var detail by remember(key) { mutableStateOf(task?.detail.orEmpty()) }
    var due by remember(key) { mutableStateOf(task?.dueEpochMs) }
    var allDay by remember(key) { mutableStateOf(task?.isAllDay == true) }

    var confirmingDelete by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (task == null) "Add a task" else "Task") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    label = { Text("Detail (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { pickingDate = true },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            due?.let { dueLabel(it, allDay) } ?: "Set a deadline",
                            maxLines = 1,
                        )
                    }
                    if (due != null) {
                        TextButton(onClick = {
                            due = null
                            allDay = false
                        }) { Text("Clear") }
                    }
                }
                if (onDelete != null) {
                    Gap(2)
                    TextButton(onClick = { confirmingDelete = true }) {
                        Text("Delete this task", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = { onSave(title, detail.takeIf { it.isNotBlank() }, due, allDay) },
            ) { Text(if (task == null) "Add" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (confirmingDelete && onDelete != null) {
        ConfirmDeleteDialog(
            what = "this task",
            onConfirm = {
                confirmingDelete = false
                onDelete()
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    if (pickingDate) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = due ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDate = pickerState.selectedDateMillis
                    pickingDate = false
                    if (pendingDate != null) pickingTime = true
                }) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = {
                    // A day with no time is a perfectly good deadline for something
                    // due "on Friday", so leaving here keeps the date as all-day
                    // rather than throwing the choice away.
                    val day = pickerState.selectedDateMillis
                    if (day != null) {
                        due = Instant.ofEpochMilli(day)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        allDay = true
                    }
                    pickingDate = false
                }) { Text("All day") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingTime) {
        val existing = due?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
        val timeState = rememberTimePickerState(
            initialHour = existing?.hour ?: 9,
            initialMinute = existing?.minute ?: 0,
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val day = pendingDate
                    if (day != null) {
                        // The date picker works in UTC millis by contract, so the day
                        // is taken from UTC and the time applied in the local zone.
                        due = Instant.ofEpochMilli(day)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .atTime(timeState.hour, timeState.minute)
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                            .toEpochMilli()
                        allDay = false
                    }
                    pickingTime = false
                }) { Text("Set") }
            },
            dismissButton = {
                TextButton(onClick = { pickingTime = false }) { Text("Cancel") }
            },
        )
    }
}
