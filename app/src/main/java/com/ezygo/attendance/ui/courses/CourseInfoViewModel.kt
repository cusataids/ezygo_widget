package com.ezygo.attendance.ui.courses

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.repo.CourseCatalogRepository
import com.ezygo.attendance.domain.model.CourseInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CourseInfoUiState(
    val info: CourseInfo? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
)

@HiltViewModel
class CourseInfoViewModel @Inject constructor(
    private val repository: CourseCatalogRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val courseId: Int = savedStateHandle.get<Int>(ARG_COURSE_ID) ?: -1

    private val _state = MutableStateFlow(CourseInfoUiState())
    val state: StateFlow<CourseInfoUiState> = _state.asStateFlow()

    init {
        load(forceRefresh = false)
    }

    fun refresh() = load(forceRefresh = true)

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repository.courseInfo(courseId, forceRefresh) }
                .onSuccess { info -> _state.value = CourseInfoUiState(info = info, isLoading = false) }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isLoading = false, error = ApiError.from(throwable).userMessage)
                    }
                }
        }
    }

    companion object {
        const val ARG_COURSE_ID = "courseId"
    }
}
