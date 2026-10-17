package com.ezygo.attendance.ui.samarth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.SamarthProfile
import com.ezygo.attendance.ui.common.EmptyState
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError
import com.ezygo.attendance.ui.common.SectionHeader
import com.ezygo.attendance.ui.common.relativeTime

/**
 * The full student record from Samarth.
 *
 * Deliberately inert on open: it shows the copy stored the last time Refresh was
 * pressed, and nothing else ever writes it. That is what lets the line at the bottom
 * state, truthfully, when this was last read from the portal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SamarthProfileScreen(
    onBack: () -> Unit,
    viewModel: SamarthProfileViewModel = hiltViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detailed profile") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Gap(12)
                    } else {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh from Samarth")
                        }
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
            state.error?.let { message ->
                item { InlineError(message, onRetry = viewModel::refresh) }
            }

            val current = profile
            if (current == null) {
                item {
                    EmptyState(
                        title = "Nothing fetched yet",
                        body = if (state.hasCredentials) {
                            "Tap refresh to read your record from Samarth."
                        } else {
                            "Tap refresh and sign in to Samarth once. You will not be " +
                                "asked again."
                        },
                    )
                }
                item {
                    Button(
                        onClick = viewModel::refresh,
                        enabled = !state.isRefreshing,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Fetch from Samarth") }
                }
            } else {
                item { ProfileCard(current) }

                item { SectionHeader("Programme") }
                item {
                    DetailCard(
                        listOf(
                            "Enrolment number" to current.enrolmentNumber,
                            "ABC ID" to current.abcId,
                            "Department" to current.department,
                        ),
                    )
                }

                item { SectionHeader("Personal") }
                item {
                    DetailCard(
                        listOf(
                            "Date of birth" to current.dateOfBirth,
                            "Category" to current.category,
                            "Email" to current.email,
                            "Phone" to current.phone,
                            "Address" to current.address.ifBlank { SamarthProfile.NOT_AVAILABLE },
                        ),
                    )
                }

                item {
                    Text(
                        relativeTime(current.fetchedEpochMs) + " · only Refresh changes this",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (state.askForCredentials) {
        SamarthCredentialsDialog(
            onSave = viewModel::saveCredentials,
            onDismiss = viewModel::dismissCredentials,
            error = state.credentialsError,
        )
    }
}

@Composable
private fun ProfileCard(profile: SamarthProfile) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                profile.fullName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (profile.department != SamarthProfile.NOT_AVAILABLE) {
                Gap(4)
                Text(
                    profile.department,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

@Composable
private fun DetailCard(rows: List<Pair<String, String>>) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = 4.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(0.42f),
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(0.58f),
                    )
                }
            }
        }
    }
}
