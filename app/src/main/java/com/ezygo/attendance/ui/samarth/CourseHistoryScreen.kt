package com.ezygo.attendance.ui.samarth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.repo.SamarthRepository
import com.ezygo.attendance.domain.model.SemesterCourses
import com.ezygo.attendance.domain.model.SemesterResult
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale
import javax.inject.Inject

data class CourseHistoryUiState(
    val semesters: List<SemesterCourses> = emptyList(),
    val results: List<SemesterResult> = emptyList(),
) {
    /**
     * Every credit read off a course line, across every semester.
     *
     * Deliberately summed from the course lines rather than taken from each card's
     * "Semester Credits" total: this screen lists the courses, and a cumulative figure
     * that did not equal the lines under it would be the one number nobody could check.
     */
    val totalCredits: Double get() = semesters.sumOf { it.creditsEarned }

    fun sgpaFor(semester: Int): Double? =
        results.firstOrNull { it.semester == semester }?.sgpa
}

@HiltViewModel
class CourseHistoryViewModel @Inject constructor(
    repository: SamarthRepository,
) : ViewModel() {

    val state: StateFlow<CourseHistoryUiState> = combine(
        repository.semesterCourses,
        repository.semesterResults,
    ) { semesters, results ->
        CourseHistoryUiState(semesters = semesters, results = results)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseHistoryUiState())
}

/**
 * Every completed course, by semester, with what it was worth and what it earned.
 *
 * Read out of the downloaded grade cards and nowhere else, so a semester appears here
 * only once its card has been fetched. Semesters whose card this app could not parse a
 * course table out of are named rather than omitted, so a gap is visible as a gap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseHistoryScreen(
    onBack: () -> Unit,
    viewModel: CourseHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Credit history") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.semesters.isEmpty()) {
                item {
                    EmptyState(
                        title = "No courses read yet",
                        body = "Download your grade cards, and the courses printed on " +
                            "them appear here by semester.",
                    )
                }
                return@LazyColumn
            }

            item { TotalCard(state) }

            items(state.semesters, key = { it.semester }) { semester ->
                SemesterCard(semester = semester, sgpa = state.sgpaFor(semester.semester))
            }

            // A card that was read for its SGPA but not for its course table would
            // otherwise simply be absent, which reads as a semester never taken.
            val missing = state.results
                .map { it.semester }
                .filterNot { read -> state.semesters.any { it.semester == read } }
            if (missing.isNotEmpty()) {
                item {
                    Text(
                        "No course list could be read from " +
                            missing.sorted().joinToString(", ") { "semester $it" } + ".",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalCard(state: CourseHistoryUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Credits earned",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(2)
            Text(
                creditLabel(state.totalCredits),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Gap(2)
            Text(
                state.semesters.size.let {
                    "$it " + if (it == 1) "semester" else "semesters"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun SemesterCard(semester: SemesterCourses, sgpa: Double?) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Semester ${semester.semester}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                sgpa?.let {
                    Text(
                        "SGPA " + String.format(Locale.getDefault(), "%.2f", it),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Gap(8)
            semester.courses.forEach { course ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Column(Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            course.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                        )
                        Text(
                            course.code,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        course.credits?.let { creditLabel(it) } ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        course.grade ?: "—",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Gap(6)
            HorizontalDivider()
            Gap(6)
            Row(Modifier.fillMaxWidth()) {
                Text(
                    "Semester credits",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    creditLabel(semester.creditsEarned),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

/** Whole credits print whole; a half credit keeps its half. */
private fun creditLabel(value: Double): String =
    if (value % 1.0 == 0.0) {
        value.toInt().toString()
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }
