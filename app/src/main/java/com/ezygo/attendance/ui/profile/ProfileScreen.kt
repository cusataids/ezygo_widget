package com.ezygo.attendance.ui.profile

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.R
import com.ezygo.attendance.domain.model.Period
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.relativeTime
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onOpenCourses: () -> Unit,
    onOpenSamarthProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val dashboard by viewModel.dashboard.collectAsStateWithLifecycle()
    val activePeriod by viewModel.activePeriod.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmRemoveSamarth by remember { mutableStateOf(false) }
    val hasSamarthData by viewModel.hasSamarthData.collectAsStateWithLifecycle(initialValue = false)

    val period = activePeriod ?: Period(
        profile?.defaultAcademicYear ?: Period.ACADEMIC_YEARS.last(),
        profile?.defaultSemester ?: Period.SEMESTER_ODD,
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        error?.let { message ->
            item { InlineError(message, onRetry = viewModel::refresh) }
        }

        item {
            val name = listOfNotNull(profile?.firstName, profile?.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { profile?.username ?: "Student" }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    profile?.username?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Gap(12)
                    // The bearer token and password are deliberately absent here and
                    // from every other surface in the app.
                    InfoRow("Institution", profile?.institutionName)
                    InfoRow("Role", profile?.institutionRole)
                    InfoRow("Enrolment", profile?.enrollStatus)
                    InfoRow("Email", profile?.email)
                    InfoRow("Period", period.displayLabel)
                }
            }
        }

        // Directly under the profile it expands on, with no heading of its own: a
        // section header for a single row says nothing the row does not already say.
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenSamarthProfile),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "See detailed profile",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Enrolment number, ABC ID, department and contact details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Academic period") }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    // Both halves are always written, never copied through. With the
                    // account on All the other half is "0", and copying it would send
                    // "0" straight back — so every change appeared to do nothing and
                    // the setting could not be left from here at all.
                    if (period.isAll) {
                        Text(
                            "Your EzyGo account is set to " + period.displayLabel.lowercase() +
                                ". Attendance is only meaningful for one semester at a " +
                                "time, so pick a year and semester below.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Gap(10)
                    }
                    AcademicYearPicker(
                        selected = period.academicYear,
                        enabled = !isRefreshing,
                        onSelect = {
                            val chosen = Period.concrete(it, period.semester)
                            if (chosen != period) viewModel.selectPeriod(chosen)
                        },
                    )
                    Gap(12)
                    Text("Semester", style = MaterialTheme.typography.labelMedium)
                    Gap(6)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Period.SEMESTERS.forEach { semester ->
                            FilterChip(
                                selected = period.semester == semester,
                                enabled = !isRefreshing,
                                onClick = {
                                    val chosen = Period.concrete(period.academicYear, semester)
                                    if (chosen != period) viewModel.selectPeriod(chosen)
                                },
                                label = {
                                    Text(semester.replaceFirstChar { it.uppercaseChar() })
                                },
                            )
                        }
                    }
                    Gap(10)
                    Text(
                        "Changing these updates the default academic year and semester " +
                            "on your EzyGo account, not just in this app.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Courses") }
        item {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenCourses),
            ) {
                Row(
                    Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "My courses",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Syllabus, modules, outcomes and who else is enrolled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("App") }
        item {
            // One way in, rather than three sections of switches between the student's
            // courses and the sign-out button. Everything behind it is about how the
            // app behaves; everything on this screen is about who is using it.
            SettingSummaryRow(
                title = "Settings",
                subtitle = "Theme, notifications, home screen and attendance target",
                onClick = onOpenSettings,
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = viewModel::refresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, Modifier.size(18.dp))
                        Text("  Refresh")
                    }
                }
                TextButton(onClick = { confirmSignOut = true }) { Text("Sign out") }
            }
        }

        // Beside sign-out, because it is the same kind of action on a smaller scale:
        // forgetting data held on this device. Offered only when there is some.
        if (hasSamarthData) {
            item {
                TextButton(
                    onClick = { confirmRemoveSamarth = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Remove Samarth data") }
            }
        }

        item {
            Text(
                relativeTime(dashboard?.lastUpdatedEpochMs),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                Gap(14)
                Text(
                    text = buildAnnotatedString {
                        append("Made with ")

                        withStyle(
                            SpanStyle(textDecoration = TextDecoration.LineThrough)
                        ) {
                            append("love")
                        }

                        append(" Faah's Claude ❤️")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(10)
                Text(
                    stringResource(R.string.disclaimer_full),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }

    if (confirmRemoveSamarth) {
        AlertDialog(
            onDismissRequest = { confirmRemoveSamarth = false },
            title = { Text("Remove Samarth data?") },
            text = {
                Text(
                    "This deletes your saved Samarth sign-in, the fetched profile and " +
                        "every downloaded grade card from this device. Your EzyGo " +
                        "account is not affected.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRemoveSamarth = false
                    viewModel.removeSamarthData()
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveSamarth = false }) { Text("Cancel") }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                // Named explicitly because local calendar events are the one thing
                // here that exists nowhere else: everything else can be refetched,
                // and those cannot.
                Text(
                    "Logging out will remove locally stored events, your notes, " +
                        "any saved Samarth data and cached personal data from this " +
                        "device.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    viewModel.signOut()
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }

}

/**
 * A settings row that opens something, for settings that are not one of the source
 * lists.
 *
 * [SourceSummaryRow] cannot be reused here: it counts FeedSource entries to build
 * its own subtitle, which only means anything for the two calendar lists.
 */
@Composable
private fun SettingSummaryRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The three themes, as a single choice.
 *
 * Radio buttons rather than the switches the source dialogs use, because these are
 * mutually exclusive: exactly one is always in force. Picking closes the dialog —
 * there is nothing further to decide, and the result is visible instantly behind it.
 */
@Composable
private fun AcademicYearPicker(
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text("Academic year", style = MaterialTheme.typography.labelMedium)
        Gap(6)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = enabled) {
                // "0" is the portal's own value for All, and shows as itself otherwise.
                Text(if (selected == Period.ALL) "All years" else selected)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Period.ACADEMIC_YEARS.asReversed().forEach { year ->
                    DropdownMenuItem(
                        text = { Text(year) },
                        onClick = {
                            expanded = false
                            // Whether this is a change is not a question the picker can
                            // answer: with the semester on All, re-picking the same year
                            // still moves the account off it. The caller compares the
                            // whole period instead.
                            onSelect(year)
                        },
                    )
                }
            }
        }
    }
}

/**
 * One of the two lists, closed.
 *
 * Built as its own card rather than as a row inside a shared one, so it reads as the
 * same kind of thing as "My courses" and "See detailed profile" — every button on this
 * screen opens something, and they should all look alike doing it.
 *
 * The count trails the description: "3 of 5" says whether anything has been turned off,
 * which is the only part of the list worth knowing without opening it.
 */
@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column {
        Row(Modifier.padding(vertical = 5.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(96.dp),
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
    }
}
