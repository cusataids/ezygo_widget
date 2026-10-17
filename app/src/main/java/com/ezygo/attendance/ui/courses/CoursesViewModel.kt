package com.ezygo.attendance.ui.courses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.repo.CourseCatalogRepository
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.domain.model.CourseSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoursesUiState(
    val courses: List<CourseSummary> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class CoursesViewModel @Inject constructor(
    private val repository: CourseCatalogRepository,
    private val store: DashboardStore,
) : ViewModel() {

    private val _state = MutableStateFlow(CoursesUiState())
    val state: StateFlow<CoursesUiState> = _state.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.courses(store.activePeriod.value, forceRefresh) }
                .onSuccess { courses ->
                    _state.value = CoursesUiState(courses = courses, isLoading = false)
                }
                .onFailure { throwable ->
                    _state.update {
                        // Keep whatever was already listed; a failed reload should not
                        // blank a list the student was reading.
                        it.copy(isLoading = false, error = ApiError.from(throwable).userMessage)
                    }
                }
        }
    }
}
