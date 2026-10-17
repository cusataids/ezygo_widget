package com.ezygo.attendance.ui.miniapps.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.Task
import com.ezygo.attendance.domain.model.TaskGroup
import com.ezygo.attendance.domain.model.TaskSource
import com.ezygo.attendance.ui.common.CannotDeleteDialog
import com.ezygo.attendance.ui.common.ConfirmDeleteDialog
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.dueLabel

/**
 * Everything owed, grouped by when it is due.
 *
 * Grouped by deadline rather than by source: an overdue essay from the shared calendar
 * and one you typed yourself are equally overdue, so where a task came from is a mark
 * on its row rather than a division of the list.
 *
 * Adding and editing go through the same dialog, so a task is described the same way
 * whether it is being written or corrected, and a deadline can be set at the moment it
 * is created rather than only afterwards.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleteError by viewModel.deleteError.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Task?>(null) }
    var adding by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    val now = System.currentTimeMillis()

    // Leaving selection by the back gesture rather than the screen, which is what a
    // reader who selected something by accident will reach for first.
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            selecting -> "${selected.size} selected"
                            state.openCount > 0 -> "Tasks (${state.openCount})"
                            else -> "Tasks"
                        },
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selecting) selected = emptySet() else onBack() },
                    ) {
                        Icon(
                            if (selecting) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selecting) "Cancel selection" else "Back",
                        )
                    }
                },
                actions = {
                    when {
                        selecting -> IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete selected tasks",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }

                        state.isSyncing -> {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Gap(12)
                        }

                        else -> IconButton(onClick = viewModel::sync) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync tasks")
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // A labelled button rather than a floating one: it says what it does, and
            // it sits above the list it adds to instead of covering the last row of it.
            if (!selecting) {
                item(key = "add") {
                    Button(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text("  Add a task")
                    }
                }
            }

            state.error?.let { item { InlineError(it, onRetry = viewModel::sync) } }

            tasksList(
                state = state,
                nowEpochMs = now,
                viewModel = viewModel,
                selected = selected,
                selecting = selecting,
                onEdit = { editing = it },
                onToggleSelect = { id -> selected = selected.toggle(id) },
            )

            item { Gap(24) }
        }
    }

    if (adding) {
        TaskEditorDialog(
            task = null,
            onSave = { title, detail, due, allDay ->
                viewModel.add(title, detail, due, allDay)
                adding = false
            },
            onDelete = null,
            onDismiss = { adding = false },
        )
    }

    deleteError?.let { message ->
        CannotDeleteDialog(message, onDismiss = viewModel::dismissDeleteError)
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            what = if (selected.size == 1) "this task" else "${selected.size} tasks",
            onConfirm = {
                viewModel.delete(selected)
                selected = emptySet()
                confirmingDelete = false
            },
            onDismiss = { confirmingDelete = false },
        )
    }

    editing?.let { task ->
        TaskEditorDialog(
            task = task,
            onSave = { title, detail, due, allDay ->
                viewModel.edit(task.id, title, detail, due, allDay)
                editing = null
            },
            onDelete = {
                viewModel.delete(task.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

private fun LazyListScope.tasksList(
    state: TasksUiState,
    nowEpochMs: Long,
    viewModel: TasksViewModel,
    selected: Set<String>,
    selecting: Boolean,
    onEdit: (Task) -> Unit,
    onToggleSelect: (String) -> Unit,
) {
    if (state.sections.isEmpty() && state.recentlyDone.isEmpty()) {
        item {
            EmptyState(
                title = "Nothing to do",
                body = "Add a task above. Deadlines from the shared tasks calendar " +
                    "arrive here too.",
            )
        }
        return
    }

    state.sections.forEach { section ->
        item(key = "head-" + section.group.name) {
            GroupHeader(
                label = section.group.label,
                count = section.tasks.size,
                overdue = section.group == TaskGroup.OVERDUE,
            )
        }
        items(section.tasks, key = { it.id }) { task ->
            TaskRow(
                task = task,
                nowEpochMs = nowEpochMs,
                selected = task.id in selected,
                selecting = selecting,
                onDone = { viewModel.setDone(task.id, it) },
                onClick = {
                    if (selecting) onToggleSelect(task.id) else if (task.isEditable) onEdit(task)
                },
                onLongClick = { onToggleSelect(task.id) },
            )
        }
    }

    if (state.recentlyDone.isNotEmpty()) {
        item(key = "head-done") {
            GroupHeader(label = "Done", count = state.recentlyDone.size, overdue = false)
        }
        items(state.recentlyDone, key = { it.id }) { task ->
            TaskRow(
                task = task,
                nowEpochMs = nowEpochMs,
                selected = task.id in selected,
                selecting = selecting,
                onDone = { viewModel.setDone(task.id, it) },
                onClick = {
                    if (selecting) onToggleSelect(task.id) else if (task.isEditable) onEdit(task)
                },
                onLongClick = { onToggleSelect(task.id) },
            )
        }
    }
}

@Composable
private fun GroupHeader(label: String, count: Int, overdue: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            // Overdue is the one heading that is not neutral information.
            color = if (overdue) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    nowEpochMs: Long,
    selected: Boolean,
    selecting: Boolean,
    onDone: (Boolean) -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Ticking off is not what the checkbox means while things are being picked
            // out, so it steps aside rather than offering two answers to one tap.
            Checkbox(checked = task.done, onCheckedChange = onDone, enabled = !selecting)
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                    color = if (task.done) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    listOfNotNull(
                        task.dueEpochMs?.let { dueLabel(it, task.isAllDay) },
                        // Said on the row rather than in a paragraph under the list:
                        // it explains why this one cannot be edited, exactly where
                        // someone would try to edit it.
                        if (task.source == TaskSource.SYNCED) "Synced" else null,
                    ).joinToString(" · ").ifEmpty { "No deadline" },
                    style = MaterialTheme.typography.labelSmall,
                    // Overdue is the one thing on this row worth colouring.
                    color = if (task.isOverdue(nowEpochMs)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                if (!task.detail.isNullOrBlank()) {
                    Gap(2)
                    Text(
                        task.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                    )
                }
            }
        }
    }
}
