package com.ezygo.attendance.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import com.ezygo.attendance.domain.model.FeedSource
import com.ezygo.attendance.domain.model.ThemeChoice
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.theme.EzygoTheme
import com.ezygo.attendance.ui.theme.resolveDark
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Asks which widget to add, before the widget appears on the home screen.
 *
 * This is the standard `android:configure` activity: the launcher starts it on
 * drop and only places the widget if it returns `RESULT_OK`. Backing out cancels
 * the placement, which is why the result is seeded as cancelled.
 *
 * One of these per family, because the chooser only offers types that fit the
 * footprint the user actually dropped.
 */
abstract class WidgetConfigActivity(private val family: WidgetFamily) : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        // Cancelled unless a type is actually chosen: an abandoned chooser must not
        // leave a widget behind.
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContent {
            // The chooser is app UI, not a widget, so it follows the app's theme
            // rather than the launcher's. This activity is a plain ComponentActivity
            // and cannot inject, so the preference comes through the entry point the
            // widgets already use.
            val themeFlow = remember { widgetEntryPoint().preferencesStore().theme }
            val theme by themeFlow.collectAsState(initial = ThemeChoice.DEFAULT)
            EzygoTheme(darkTheme = theme.resolveDark()) {
                // Null until a type is picked. The calendar is the one type that needs
                // a second question, so the chooser becomes two steps only for it and
                // stays a single tap for everything else.
                var picked by rememberSaveable { mutableStateOf<WidgetType?>(null) }

                if (picked == WidgetType.CALENDAR) {
                    SourceChooser(
                        onBack = { picked = null },
                        onConfirm = { sources ->
                            choose(appWidgetId, WidgetType.CALENDAR, sources)
                        },
                    )
                } else {
                    WidgetChooser(
                        types = family.types,
                        onPick = { type ->
                            if (type == WidgetType.CALENDAR) {
                                picked = type
                            } else {
                                choose(appWidgetId, type, FeedSource.DEFAULT_HOME)
                            }
                        },
                    )
                }
            }
        }
    }

    /**
     * Stores the choice and draws the widget once, then reports the outcome.
     *
     * Every step here can fail — the widget id may not resolve, and the state write
     * goes to disk — and an uncaught failure in this coroutine would take the app
     * down while leaving the launcher waiting. So the result reflects what actually
     * happened: `RESULT_OK` only once the type is saved and the widget has been
     * drawn, `RESULT_CANCELED` otherwise, and the activity closes cleanly either
     * way rather than sitting there.
     */
    private fun choose(appWidgetId: Int, type: WidgetType, sources: Set<String>) {
        lifecycleScope.launch {
            try {
                val glanceId = GlanceAppWidgetManager(this@WidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@WidgetConfigActivity, glanceId) { preferences ->
                    preferences[EzygoWidget.WIDGET_TYPE_KEY] = type.name
                    // Written for every type, not only the calendar: the store is per
                    // widget id and ids are recycled by the launcher, so leaving the
                    // key unset would let a new widget inherit a deleted one's feeds.
                    preferences[EzygoWidget.WIDGET_SOURCES_KEY] = sources
                }

                // The result is set *before* the widget is drawn. Until the host
                // has been told the configuration succeeded the widget is not yet
                // committed, and a draw aimed at it can be discarded — which leaves
                // it showing the static placeholder with nothing scheduled to try
                // again, because updatePeriodMillis is 0.
                setResult(RESULT_OK, resultIntent(appWidgetId))

                // Drawn twice, on purpose, because a widget with a configuration
                // activity is never sent the initial onUpdate and these are its only
                // two chances. The direct call composes now, while this process is
                // certainly alive and its Room and Hilt graph are already warm.
                EzygoWidgets.widgetFor(family).update(this@WidgetConfigActivity, glanceId)

                // The broadcast then does it again through the receiver, which is
                // what registers this widget with Glance and what keeps the process
                // up while Glance's own worker composes. See requestRedraw: without
                // it, one lost draw leaves the widget on its placeholder for good.
                requestRedraw(this@WidgetConfigActivity, family, intArrayOf(appWidgetId))
            } catch (cancellation: CancellationException) {
                // The activity is going away; the seeded RESULT_CANCELED stands.
                throw cancellation
            } catch (throwable: Throwable) {
                // The widget type and the failure, and nothing else: this activity
                // never holds a token or any of the user's data, and none belongs
                // in a log regardless.
                Log.e(
                    EzygoWidget.TAG,
                    "Could not configure the ${type.name} widget",
                    throwable,
                )
                setResult(RESULT_CANCELED, resultIntent(appWidgetId))
            }
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int) =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

class CompactWidgetConfigActivity : WidgetConfigActivity(WidgetFamily.COMPACT)

class LargeWidgetConfigActivity : WidgetConfigActivity(WidgetFamily.LARGE)


/**
 * Which calendars and task lists a calendar widget shows.
 *
 * Asked once, when the widget is added, and stored against that widget's id, so two
 * calendar widgets can sit side by side showing different feeds. Everything is on by
 * default except the academic calendar, which is a long reference list that would bury
 * the handful of dates a widget has room for.
 */
@Composable
private fun SourceChooser(onBack: () -> Unit, onConfirm: (Set<String>) -> Unit) {
    var selected by rememberSaveable { mutableStateOf(FeedSource.DEFAULT_HOME) }
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "What should it show?",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Pick the calendars and task lists for this widget.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(2)

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(FeedSource.entries, key = { it.key }) { source ->
                    val on = source.key in selected
                    SourceRow(
                        source = source,
                        checked = on,
                        onToggle = {
                            selected = if (on) selected - source.key else selected + source.key
                        },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back")
                }
                Button(
                    onClick = { onConfirm(selected) },
                    // Nothing selected would place a widget that can never show
                    // anything, so the way out is Back rather than a blank widget.
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Add widget")
                }
            }
        }
    }
}

@Composable
private fun SourceRow(source: FeedSource, checked: Boolean, onToggle: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    source.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    source.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Checkbox(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun WidgetChooser(types: List<WidgetType>, onPick: (WidgetType) -> Unit) {
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column {
                    Text(
                        "Choose a widget",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Gap(4)
                    Text(
                        "You can add more than one, each showing something different.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Gap(6)
                }
            }

            items(types, key = { it.name }) { type ->
                TypeRow(type) { onPick(type) }
            }
        }
    }
}

@Composable
private fun TypeRow(type: WidgetType, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        type.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "  " + type.recommendedSize,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Gap(3)
                Text(
                    type.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
