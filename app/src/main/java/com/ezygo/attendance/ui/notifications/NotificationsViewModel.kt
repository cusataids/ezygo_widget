package com.ezygo.attendance.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.repo.NotificationsRepository
import com.ezygo.attendance.domain.model.UserNotification
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationsUiState(
    val notifications: List<UserNotification> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationsUiState())
    val state: StateFlow<NotificationsUiState> = _state.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.notifications(forceRefresh) }
                .onSuccess { notifications ->
                    _state.value = NotificationsUiState(
                        notifications = notifications,
                        isLoading = false,
                    )
                }
                .onFailure { throwable ->
                    // Keep whatever is already listed; a failed reload should not
                    // blank a list the student was reading.
                    _state.update {
                        it.copy(isLoading = false, error = ApiError.from(throwable).userMessage)
                    }
                }
        }
    }
}
