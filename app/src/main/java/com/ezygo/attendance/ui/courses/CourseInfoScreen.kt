package com.ezygo.attendance.ui.courses

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.Classmate
import com.ezygo.attendance.domain.model.CourseInfo
import com.ezygo.attendance.domain.model.CourseModuleInfo
import com.ezygo.attendance.domain.model.CoursePropertyInfo
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.HomeSkeleton
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseInfoScreen(
    onBack: () -> Unit,
    viewModel: CourseInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.info?.name ?: "Course", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val info = state.info
        if (info == null) {
            if (state.isLoading) {
                HomeSkeleton(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                )
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    state.error?.let { InlineError(it, onRetry = viewModel::refresh) }
                    EmptyState(
                        title = "Course unavailable",
                        body = "This course's details could not be loaded.",
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            state.error?.let { message ->
                item { InlineError(message, onRetry = viewModel::refresh) }
            }

            item { HeaderCard(info) }

            info.property?.let { property ->
                item { SectionHeader("Syllabus") }
                item { PropertyCard(property) }
            }

            if (info.modules.isNotEmpty()) {
                item { SectionHeader("Modules", trailing = "${info.modules.size}") }
                items(info.modules, key = { "module-" + it.id }) { ModuleCard(it) }
            }

            if (info.outcomes.isNotEmpty()) {
                item { SectionHeader("Course outcomes", trailing = "${info.outcomes.size}") }
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            info.outcomes.forEachIndexed { index, outcome ->
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                                    outcome.code?.let {
                                        Text(
                                            it,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(end = 10.dp),
                                        )
                                    }
                                    Text(outcome.outcome, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (index != info.outcomes.lastIndex) {
                                    HorizontalDivider(Modifier.padding(horizontal = 14.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (info.staff.isNotEmpty()) {
                item { SectionHeader("Faculty", trailing = "${info.staff.size}") }
                items(info.staff, key = { "staff-" + it.institutionUserId }) { PersonRow(it) }
            }

            if (info.students.isNotEmpty()) {
                item { SectionHeader("Classmates", trailing = "${info.students.size}") }
                items(info.students, key = { "student-" + it.institutionUserId }) { PersonRow(it) }
            }

        }
    }
}

@Composable
private fun HeaderCard(info: CourseInfo) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(info.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Gap(6)
            listOfNotNull(
                info.code?.let { "Code" to it },
                info.type?.let { "Type" to it },
                info.property?.ltpCredits?.let { "L-T-P-Credits" to it },
                info.subgroupName?.let { "Class" to it },
                info.programmeName?.let { "Programme" to it },
            ).forEach { (label, value) -> LabelledLine(label, value) }
        }
    }
}

@Composable
private fun PropertyCard(property: CoursePropertyInfo) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Only the fields the institution actually filled in are shown; empty
            // headings would just be noise.
            listOfNotNull(
                property.preamble?.let { "Preamble" to it },
                property.aims?.let { "Aims" to it },
                property.objectives?.let { "Objectives" to it },
                property.preRequisites?.let { "Pre-requisites" to it },
                property.textBooks?.let { "Text books" to it },
                property.referenceDocs?.let { "References" to it },
                property.learningMaterials?.let { "Learning materials" to it },
                property.contentBeyondSyllabus?.let { "Beyond syllabus" to it },
                property.industrialRelevance?.let { "Industrial relevance" to it },
            ).forEach { (label, value) -> Paragraph(label, value) }
        }
    }
}

/** Long syllabus text collapses by default so the page stays scannable. */
@Composable
private fun Paragraph(label: String, value: String) {
    var expanded by remember { mutableStateOf(false) }
    val isLong = value.length > COLLAPSE_THRESHOLD
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .then(if (isLong) Modifier.clickable { expanded = !expanded } else Modifier),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            if (isLong) {
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Gap(2)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = if (expanded || !isLong) Int.MAX_VALUE else COLLAPSED_LINES,
        )
    }
}

@Composable
private fun ModuleCard(module: CourseModuleInfo) {
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    module.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                module.contactHours?.let {
                    Text(
                        "$it hrs",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Show less" else "Show more",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            module.lessons?.let {
                Gap(6)
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                )
            }
        }
    }
}

@Composable
private fun PersonRow(person: Classmate) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            modifier = Modifier.size(30.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    person.name.take(1).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Column(Modifier.padding(start = 10.dp)) {
            Text(person.name, style = MaterialTheme.typography.bodyMedium)
            val detail = listOfNotNull(
                if (person.isStaff) "Faculty" else null,
                person.enrollStatus?.takeIf { !person.isStaff && !it.equals("active", true) },
            ).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LabelledLine(label: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private const val COLLAPSE_THRESHOLD = 180
private const val COLLAPSED_LINES = 3
