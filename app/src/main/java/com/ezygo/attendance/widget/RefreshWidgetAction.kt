package com.ezygo.attendance.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The widget's refresh button.
 *
 * This is the only path from a widget to the network. Everything else it shows
 * comes from the Room cache, so a widget sitting on the home screen never causes a
 * request on its own.
 */
class RefreshWidgetAction : ActionCallback {

    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        try {
            val store = context.widgetEntryPoint().dashboardStore()
            // The store reports failures through its own error state rather than
            // throwing, and leaves the cached snapshot untouched when the network is
            // down — so the widget simply redraws with the previous data.
            store.refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            Log.e(EzygoWidget.TAG, "Widget refresh failed", throwable)
        }

        // Redraw either way: after a failed refresh the widget should still come back
        // with its cached data rather than sitting in the error state. redrawAll
        // reports its own failures and does not throw.
        redrawAll(context)
    }
}

/**
 * Pushes a fresh snapshot to every placed widget.
 *
 * Injected into the refresh path so the widget follows the app: change the
 * semester or pull to refresh, and the home screen catches up without polling.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun updateAll() = redrawAll(context)
}
