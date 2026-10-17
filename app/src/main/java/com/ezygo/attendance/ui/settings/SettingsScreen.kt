package com.ezygo.attendance.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.domain.model.FeedSource
import com.ezygo.attendance.domain.model.ThemeChoice
import com.ezygo.attendance.notifications.Reminders
import kotlin.math.roundToInt

/**
 * Everything the app lets you change, in one place.
 *
 * These settings used to be three sections scattered down the profile screen, between
 * the student's name, their courses and the sign-out button — so "what can I change?"
 * could only be answered by scrolling past things that are not settings at all. The
 * profile now answers who you are; this answers how the app behaves.
 *
 * Grouped by the question each setting answers rather than by the data it touches:
 * "Notifications" and "Home screen" read from the same list of feeds, but nobody opens
 * settings thinking about feeds — they open it because something interrupted them, or
 * because the home screen is showing the wrong thing.
 *
 * Rows are stacked inside one card per group with hairlines between them, rather than
 * each floating in a card of its own. At four groups the separate-cards style became a
 * column of disconnected slabs with no way to see which heading owned which row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.attendanceSettings.collectAsStateWithLifecycle()
    val theme by viewModel.theme.collectAsStateWithLifecycle(initialValue = ThemeChoice.DEFAULT)
    val reminderSources by viewModel.reminderSources
        .collectAsStateWithLifecycle(initialValue = emptySet())
    val homeSources by viewModel.homeSources
        .collectAsStateWithLifecycle(initialValue = emptySet())

    var editingTheme by remember { mutableStateOf(false) }
    var editingSources by remember { mutableStateOf<SourceSetting?>(null) }

    val context = LocalContext.current
    val notificationsAllowed = remember { Reminders.canPost(context) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                SettingsGroup("Appearance") {
                    SettingRow(
                        icon = Icons.Default.DarkMode,
                        title = "Theme",
                        subtitle = theme.label,
                        onClick = { editingTheme = true },
                    )
                }
            }

            item {
                SettingsGroup("Notifications") {
                    SettingRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Get notified about",
                        // The system-level block is the one state a count cannot
                        // describe: every switch can be on and nothing arrives.
                        subtitle = if (notificationsAllowed) {
                            sourceSummary(reminderSources)
                        } else {
                            "Turned off in system settings"
                        },
                        warn = !notificationsAllowed,
                        onClick = { editingSources = SourceSetting.NOTIFY },
                    )
                }
            }

            item {
                SettingsGroup("Home screen") {
                    SettingRow(
                        icon = Icons.Default.Dashboard,
                        title = "Show on home screen",
                        subtitle = sourceSummary(homeSources),
                        onClick = { editingSources = SourceSetting.HOME },
                    )
                }
            }

            item {
                SettingsGroup("Attendance") {
                    SliderRow(
                        icon = Icons.Default.TrackChanges,
                        title = "Target attendance",
                        value = settings.targetPercent,
                        valueLabel = "${settings.targetPercent}%",
                        range = 50f..100f,
                        steps = 49,
                        onChange = viewModel::setTarget,
                    )
                    RowDivider()
                    SliderRow(
                        icon = null,
                        title = "Warn before falling short",
                        value = settings.watchBufferPoints,
                        valueLabel = when (val points = settings.watchBufferPoints) {
                            0 -> "Off"
                            1 -> "1 point above"
                            else -> "$points points above"
                        },
                        range = 0f..20f,
                        steps = 19,
                        onChange = viewModel::setWatchBuffer,
                    )
                }
            }
        }
    }

    if (editingTheme) {
        ThemeDialog(
            current = theme,
            onPick = {
                viewModel.setTheme(it)
                editingTheme = false
            },
            onDismiss = { editingTheme = false },
        )
    }

    editingSources?.let { setting ->
        SourceDialog(
            setting = setting,
            enabled = when (setting) {
                SourceSetting.NOTIFY -> reminderSources
                SourceSetting.HOME -> homeSources
            },
            onToggle = { key, on ->
                when (setting) {
                    SourceSetting.NOTIFY -> viewModel.setReminderSource(key, on)
                    SourceSetting.HOME -> viewModel.setHomeSource(key, on)
                }
            },
            footer = if (setting == SourceSetting.NOTIFY && !notificationsAllowed) {
                "Notifications are turned off for this app in system settings, " +
                    "so nothing will be delivered."
            } else {
                null
            },
            onDismiss = { editingSources = null },
        )
    }
}

/** "3 of 5 on", which is the only thing about a list worth knowing unopened. */
private fun sourceSummary(enabled: Set<String>): String {
    val on = FeedSource.entries.count { it.key in enabled }
    return when (on) {
        0 -> "Nothing selected"
        FeedSource.entries.size -> "All ${FeedSource.entries.size}"
        else -> "$on of ${FeedSource.entries.size}"
    }
}

/** A heading and the rows that belong to it, in one card. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column { content() }
        }
    }
}

/** A hairline between rows in the same card, inset past the icon column. */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
    )
}

@Composable
private fun SettingRow(
    icon: ImageVector?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    warn: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSlot(icon)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (warn) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A slider with its current value said in words above it.
 *
 * The label carries the number so the thumb does not have to be read against a track
 * to find out what it is set to — "75%" is the answer, the thumb position is only how
 * you change it.
 */
@Composable
private fun SliderRow(
    icon: ImageVector?,
    title: String,
    value: Int,
    valueLabel: String,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconSlot(icon, topPadding = 2.dp)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Slider(
                value = value.toFloat(),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = range,
                steps = steps,
            )
        }
    }
}

/**
 * The icon column, drawn even when there is no icon.
 *
 * A second row in the same card with no icon would otherwise start 40dp to the left of
 * the one above it, and the group would stop reading as a group.
 */
@Composable
private fun IconSlot(icon: ImageVector?, topPadding: Dp = 0.dp) {
    Box(modifier = Modifier.padding(end = 16.dp, top = topPadding).size(24.dp)) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Which of the two feed lists is being edited. */
enum class SourceSetting(val title: String) {
    NOTIFY("Get notified about"),
    HOME("Show on home screen"),
}

@Composable
private fun ThemeDialog(
    current: ThemeChoice,
    onPick: (ThemeChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeChoice.entries.forEach { choice ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(choice) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(choice.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                choice.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        RadioButton(selected = choice == current, onClick = { onPick(choice) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

/**
 * One switch per feed, for whichever question is being asked.
 *
 * The same list serves both, so a feed added later appears in both without either
 * being edited.
 */
@Composable
private fun SourceDialog(
    setting: SourceSetting,
    enabled: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    footer: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(setting.title) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                FeedSource.entries.forEach { source ->
                    val on = source.key in enabled
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(source.key, !on) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(source.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                source.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = on, onCheckedChange = { onToggle(source.key, it) })
                    }
                }
                footer?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
