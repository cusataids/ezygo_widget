package com.ezygo.attendance.ui.miniapps.notes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.NotesRepository
import com.ezygo.attendance.domain.model.Note
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NoteEditorUiState(
    val title: String = "",
    val body: String = "",
    /** False until the stored note has been read, so the fields never load blank. */
    val isReady: Boolean = false,
    /**
     * True for a note being reopened rather than written.
     *
     * A note that already exists is opened to be read far more often than to be
     * changed, so it opens rendered; a new one has nothing to render and opens with
     * the keyboard.
     */
    val isExisting: Boolean = false,
)

/**
 * One note being written.
 *
 * The note is held here and written on demand rather than on every keystroke: a note
 * is edited as a whole, and saving each character would put the list in front of the
 * user reordering itself while they type into it.
 */
@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: NotesRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /** Null for a note that does not exist yet. */
    private val noteId: String? =
        savedStateHandle.get<String>(ARG_NOTE_ID)?.takeIf { it.isNotBlank() }

    private var loaded: Note = repository.draft(System.currentTimeMillis())

    private val _state = MutableStateFlow(NoteEditorUiState())
    val state: StateFlow<NoteEditorUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = noteId?.let { repository.note(it).first() }
            if (existing != null) loaded = existing
            _state.value = NoteEditorUiState(
                title = loaded.title,
                body = loaded.body,
                isReady = true,
                isExisting = existing != null,
            )
        }
    }

    fun onTitleChange(value: String) = _state.update { it.copy(title = value) }

    fun onBodyChange(value: String) = _state.update { it.copy(body = value) }

    /**
     * Writes the note.
     *
     * Also called on the way out, so leaving by the back button keeps the work instead
     * of quietly discarding it. An untouched note saves as nothing at all — see
     * [NotesRepository.save].
     */
    fun save() {
        if (!_state.value.isReady) return
        val current = _state.value
        val note = loaded.copy(title = current.title, body = current.body)
        viewModelScope.launch { repository.save(note, System.currentTimeMillis()) }
    }

    fun delete() {
        viewModelScope.launch { repository.delete(loaded.id) }
    }

    companion object {
        const val ARG_NOTE_ID = "noteId"
    }
}
