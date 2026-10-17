package com.ezygo.attendance.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.repo.CalendarRepository
import com.ezygo.attendance.data.repo.DeleteOutcome
import com.ezygo.attendance.domain.model.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
) : ViewModel() {

    /**
     * Straight from Room, so the calendar is on screen before any network call is
     * even considered — and stays there when there is no network at all.
     */
    val events: StateFlow<List<CalendarEvent>> = repository.events
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastSyncEpochMs: StateFlow<Long?> = repository.lastSyncEpochMs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        // Cache-first: this returns immediately without a request when the cached
        // feed is still young enough.
        sync(force = false)
    }

    fun refresh() = sync(force = true)

    fun dismissError() {
        _error.value = null
    }

    fun addEvent(title: String, date: LocalDate, time: LocalTime?, note: String?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addLocalEvent(title = trimmed, date = date, time = time, note = note)
        }
    }

    /** Rewrites an existing local event; [id] identifies which one. */
    fun updateEvent(id: String, title: String, date: LocalDate, time: LocalTime?, note: String?) {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.updateLocalEvent(
                id = id,
                title = trimmed,
                date = date,
                time = time,
                note = note,
            )
        }
    }

    /**
     * Why the last delete was refused, or null. Kept apart from [error], which is the
     * sync banner and carries a Retry — retrying is not the answer here, and the
     * message has to be dismissed rather than fixed.
     */
    private val _deleteError = MutableStateFlow<String?>(null)
    val deleteError: StateFlow<String?> = _deleteError.asStateFlow()

    fun dismissDeleteError() {
        _deleteError.value = null
    }

    fun deleteEvent(id: String) = viewModelScope.launch {
        val outcome = repository.deleteLocalEvent(id)
        if (outcome is DeleteOutcome.Refused) _deleteError.value = outcome.message
    }

    private fun sync(force: Boolean) {
        viewModelScope.launch {
            _isSyncing.value = true
            _error.value = null
            try {
                repository.sync(force = force)
            } catch (cancellation: kotlinx.coroutines.CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                // Cached events stay on screen; only the message is new.
                _error.value = ApiError.from(throwable).userMessage
            } finally {
                _isSyncing.value = false
            }
        }
    }
}
