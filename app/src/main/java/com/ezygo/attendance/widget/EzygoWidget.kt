package com.ezygo.attendance.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import com.ezygo.attendance.MainActivity
import com.ezygo.attendance.domain.model.FeedSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.withTimeout

/**
 * A placed home-screen widget.
 *
 * Two things define it: the [family], fixed by which provider the launcher created
 * it from, and the [WidgetType] the user chose when adding it, stored per widget id.
 *
 * There is no loading state anywhere in here. The declared `initialLayout` is a
 * static card rather than a spinner, and [provideGlance] always reaches
 * `provideContent` — on a timeout, on a read failure, on anything. A widget that
 * cannot draw its data draws an error card with a working Refresh button instead,
 * because a widget with no way out of its own failure can only be fixed by
 * deleting and re-adding it.
 */
abstract class EzygoWidget(private val family: WidgetFamily) : GlanceAppWidget() {

    override val stateDefinition: GlanceStateDefinition<Preferences> =
        PreferencesGlanceStateDefinition

    override val sizeMode = SizeMode.Responsive(family.responsiveSizes())

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // The timeout matters as much as the catch: this runs inside a broadcast's
        // limited window, and on a cold start the first read has to build the Hilt
        // graph, open Room and initialise the keystore. Better to give up and offer
        // a Refresh than to overrun the window and deliver nothing at all.
        val initial = try {
            withTimeout(LOAD_TIMEOUT_MS) { loadWidgetSnapshot(context) }
        } catch (cancellation: CancellationException) {
            // A genuine cancellation of the enclosing scope is not ours to swallow,
            // but a timeout arrives as a TimeoutCancellationException and is.
            if (cancellation !is TimeoutCancellationException) throw cancellation
            Log.e(TAG, "Widget data read timed out after ${LOAD_TIMEOUT_MS}ms")
            null
        } catch (throwable: Throwable) {
            Log.e(TAG, "Widget data read failed", throwable)
            null
        }

        // Cold, so building it out here costs nothing and gives the composition a
        // Flow that survives recomposition. A failure stops the updates and leaves
        // the last good snapshot on screen rather than emptying the widget.
        val updates = widgetSnapshotFlow(context)
            .catch { Log.e(TAG, "Widget data update failed", it) }

        provideContent {
            // Read *inside* the composition. provideGlance runs once per session —
            // provideContent never returns — so anything read before it is frozen
            // for the whole life of that session. The type used to be read out
            // there, which is why choosing one changed nothing: the launcher binds
            // the widget and starts a session before the chooser is even answered,
            // and Glance answers a later update() on a live session by refreshing
            // its state and recomposing, never by re-running provideGlance.
            //
            // currentState reads the session's own state holder, which is exactly
            // what that refresh writes to, so a type chosen after the session
            // started still lands. It falls back to the family default rather than
            // failing: see WidgetType.fromKey.
            val type = WidgetType.fromKey(currentState(WIDGET_TYPE_KEY), family)

            // Read in the composition for the same reason as the type: it is chosen in
            // the configuration activity, which can finish after the session has
            // already started. Absent means a widget placed before this setting
            // existed, which keeps the behaviour it had — every feed but the academic
            // calendar, the same default the home screen uses.
            val sources = currentState(WIDGET_SOURCES_KEY) ?: FeedSource.DEFAULT_HOME

            // The data is collected here for the same reason, and it is what keeps
            // the widget's "last updated" agreeing with the app's: a refresh writes
            // Room, Room's invalidation reaches this collector, and a live widget
            // redraws with the new time instead of keeping the one it started with.
            val snapshot by updates.collectAsState(initial = initial)

            GlanceTheme { WidgetRoot(type = type, snapshot = snapshot, sources = sources) }
        }
    }

    companion object {
        const val EXTRA_COURSE_ID = "com.ezygo.attendance.widget.EXTRA_COURSE_ID"
        const val EXTRA_EVENT_ID = "com.ezygo.attendance.widget.EXTRA_EVENT_ID"
        internal const val TAG = "EzygoWidget"

        /** Where the chosen [WidgetType] is stored, per widget id. */
        internal val WIDGET_TYPE_KEY = stringPreferencesKey("widget_type")

        /**
         * Which feeds this particular widget shows, as [FeedSource] keys.
         *
         * Per widget rather than per app: two calendar widgets on the same home screen
         * are only worth having if they can show different things.
         */
        internal val WIDGET_SOURCES_KEY = stringSetPreferencesKey("widget_sources")

        /** Comfortably inside the broadcast window, with room for a cold start. */
        private const val LOAD_TIMEOUT_MS = 6_000L
    }
}

/**
 * Snapshot sizes the system can resize between without waking the app.
 *
 * One entry per cell footprint the layouts are actually written for, because with
 * [SizeMode.Responsive] `LocalSize` reports the declared size rather than the real
 * one — so a size declared far below what the launcher hands over lays the content
 * out for a box smaller than the widget, and leaves the difference empty. The
 * heights here are what a launcher row really comes to rather than the theoretical
 * `70n - 30` minimum, and every layout fills its box with weights on top of that.
 */
private fun WidgetFamily.responsiveSizes(): Set<DpSize> = when (this) {
    WidgetFamily.COMPACT -> setOf(
        DpSize(110.dp, 80.dp), // 2x1
        DpSize(175.dp, 80.dp), // 3x1
        DpSize(245.dp, 80.dp), // 4x1
        DpSize(315.dp, 80.dp), // 5x1
    )

    WidgetFamily.LARGE -> setOf(
        DpSize(175.dp, 80.dp), // 3x1, summary over the subjects in shortage
        DpSize(175.dp, 155.dp), // 3x2, every subject
        DpSize(245.dp, 155.dp), // 4x2
        DpSize(175.dp, 230.dp), // 3x3, every subject with what it costs to hold
        DpSize(245.dp, 230.dp), // 4x3
        DpSize(315.dp, 300.dp), // 5x4, expanded
    )
}

class CompactWidget : EzygoWidget(WidgetFamily.COMPACT)

class LargeWidget : EzygoWidget(WidgetFamily.LARGE)

/**
 * One instance of each widget, and the receiver that owns each family's widget ids.
 *
 * The class itself carries no per-widget state, so a fresh instance per call was
 * not incorrect — but it allocated a widget object on every broadcast for nothing,
 * and having exactly one of each removes any question about identity.
 */
internal object EzygoWidgets {
    val compact = CompactWidget()
    val large = LargeWidget()

    fun widgetFor(family: WidgetFamily): GlanceAppWidget = when (family) {
        WidgetFamily.COMPACT -> compact
        WidgetFamily.LARGE -> large
    }

    fun receiverFor(family: WidgetFamily): Class<out GlanceAppWidgetReceiver> = when (family) {
        WidgetFamily.COMPACT -> CompactWidgetReceiver::class.java
        WidgetFamily.LARGE -> LargeWidgetReceiver::class.java
    }
}

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EzygoWidgets.compact
}

class LargeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = EzygoWidgets.large
}

/** Asks every placed widget of both families to redraw itself. */
internal fun redrawAll(context: Context) {
    val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
    for (family in WidgetFamily.entries) {
        val component = ComponentName(context, EzygoWidgets.receiverFor(family))
        // IntArray, not a collection, so there is no orEmpty() to lean on.
        val ids = runCatching { appWidgetManager.getAppWidgetIds(component) }
            .getOrNull() ?: IntArray(0)
        requestRedraw(context, family, ids)
    }
}

/**
 * Asks one family's receiver to redraw [ids], through the framework.
 *
 * Deliberately a broadcast rather than a direct call to [GlanceAppWidget.update],
 * for two reasons that only bite a widget with a configuration activity — which
 * both of ours have.
 *
 * Glance does not draw on the calling thread. `update()` puts a session in an
 * in-process map, enqueues a `SessionWorker` through WorkManager and returns; the
 * composition happens in that worker afterwards. If the process is gone by the
 * time the worker runs, the worker finds no session in the fresh process, logs
 * "Session is not available", and returns success having drawn nothing — leaving
 * the widget on its `initialLayout` with nothing scheduled to try again, because
 * `updatePeriodMillis` is 0. Delivered as a broadcast, the redraw runs inside the
 * receiver's `goAsync()`, which holds the process up while the worker starts.
 *
 * The receiver's `onUpdate` is also the only thing that ever registers a widget
 * with `GlanceAppWidgetManager`: that registry is written from the receiver's
 * broadcast handlers and nowhere else, and a widget with a configuration activity
 * is never sent the initial `onUpdate`. Until it is registered, Glance's own
 * `updateAll()` resolves to an empty id list and silently updates nothing.
 */
internal fun requestRedraw(context: Context, family: WidgetFamily, ids: IntArray) {
    if (ids.isEmpty()) return
    val receiver = EzygoWidgets.receiverFor(family)
    // Explicit, so this is not one of the implicit broadcasts a manifest receiver
    // stopped being allowed to hear in Android 8.
    runCatching {
        context.sendBroadcast(
            Intent(context, receiver)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids),
        )
    }.onFailure {
        Log.w(EzygoWidget.TAG, "Could not ask ${receiver.simpleName} to redraw", it)
    }
}

// ---- intents -------------------------------------------------------------

internal fun homeIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

internal fun subjectIntent(context: Context, courseId: Int): Intent =
    homeIntent(context)
        .putExtra(EzygoWidget.EXTRA_COURSE_ID, courseId)
        // Without a unique action the system reuses the pending intent and every row
        // would open whichever subject was tapped first.
        .setAction("open_subject_$courseId")

internal fun eventIntent(context: Context, eventId: String): Intent =
    homeIntent(context)
        .putExtra(EzygoWidget.EXTRA_EVENT_ID, eventId)
        // Unique for the same reason as the subject rows: the extras are not part of
        // what the system compares when it decides two pending intents are the same.
        .setAction("open_event_$eventId")
