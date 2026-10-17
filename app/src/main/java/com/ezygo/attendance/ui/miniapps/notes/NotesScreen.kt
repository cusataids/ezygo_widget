package com.ezygo.attendance.ui.miniapps.notes

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.NotesRepository
import com.ezygo.attendance.domain.model.Note
import com.ezygo.attendance.ui.common.ConfirmDeleteDialog
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class NotesListUiState(
    val query: String = "",
    val notes: List<Note> = emptyList(),
    /** Distinguishes "nothing written yet" from "nothing matches the search". */
    val hasAnyNote: Boolean = false,
)

@HiltViewModel
class NotesListViewModel @Inject constructor(
    private val repository: NotesRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")

    val state: StateFlow<NotesListUiState> =
        combine(repository.notes, query) { notes, q ->
            NotesListUiState(
                query = q,
                // Filtered in Kotlin rather than in SQL: the set is small, and matching
                // here needs no escaping of whatever the user typed.
                notes = notes.filter { it.matches(q) },
                hasAnyNote = notes.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesListUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun delete(ids: Set<String>) = viewModelScope.launch {
        ids.forEach { repository.delete(it) }
    }
}

/**
 * Every note, newest first.
 *
 * Its own app rather than half of the tasks one: a note is something to read and a task
 * is something owed, and the two were only ever together because a task used to be a
 * line inside a note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onCreateNote: () -> Unit,
    viewModel: NotesListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(emptySet<String>()) }
    var confirmingDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()

    // Leaving selection by the back gesture rather than the screen, which is what a
    // reader who selected something by accident will reach for first.
    BackHandler(enabled = selecting) { selected = emptySet() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${selected.size} selected" else "Notes") },
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
                    if (selecting) {
                        IconButton(onClick = { confirmingDelete = true }) {
                            Icon(
                                Icons.Default.DeleteOutline,
                                contentDescription = "Delete selected notes",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!selecting) {
                FloatingActionButton(onClick = onCreateNote) {
                    Icon(Icons.Default.Add, contentDescription = "New note")
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // The search box is only worth its space once there is something to search,
            // and it is not what the screen is for while things are being picked out.
            if (state.hasAnyNote && !selecting) {
                item {
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        label = { Text("Search notes") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            if (state.notes.isEmpty()) {
                item {
                    EmptyState(
                        title = if (state.hasAnyNote) "Nothing matches" else "No notes yet",
                        body = if (state.hasAnyNote) {
                            "No note contains \"" + state.query.trim() + "\"."
                        } else {
                            "Tap the button to write one."
                        },
                    )
                }
            } else {
                items(state.notes, key = { it.id }) { note ->
                    NoteRow(
                        note = note,
                        selected = note.id in selected,
                        onClick = {
                            if (selecting) {
                                selected = selected.toggle(note.id)
                            } else {
                                onOpenNote(note.id)
                            }
                        },
                        onLongClick = { selected = selected.toggle(note.id) },
                    )
                }
            }

            item { Gap(72) }
        }
    }

    if (confirmingDelete) {
        ConfirmDeleteDialog(
            what = if (selected.size == 1) "this note" else "${selected.size} notes",
            onConfirm = {
                viewModel.delete(selected)
                selected = emptySet()
                confirmingDelete = false
            },
            onDismiss = { confirmingDelete = false },
        )
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(
    note: Note,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                note.displayTitle.ifBlank { "Untitled" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (note.body.isNotBlank()) {
                Gap(4)
                Text(
                    // Without its markers: two lines of preview should not be spent on
                    // asterisks and hyphens.
                    NoteMarkdown.plain(note.body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            Gap(6)
            // An actual clock time rather than an age: "3 hours ago" is only true for
            // as long as the row is on screen, and a note is looked at to find out when
            // it was written.
            Text(
                noteTime(note.updatedEpochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** When the note was last written, as a clock time that stays true. */
private fun noteTime(epochMs: Long): String {
    val moment = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault())
    val day = moment.toLocalDate()
    val today = LocalDate.now()
    val clock = "%02d:%02d".format(moment.hour, moment.minute)
    return when (day) {
        today -> clock
        today.minusDays(1) -> "Yesterday $clock"
        else -> "${day.dayOfMonth} " +
            day.month.name.take(3).lowercase().replaceFirstChar { it.uppercase() } +
            " " + clock
    }
}
