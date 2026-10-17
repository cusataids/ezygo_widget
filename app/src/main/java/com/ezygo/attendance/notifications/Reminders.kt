package com.ezygo.attendance.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ezygo.attendance.MainActivity
import com.ezygo.attendance.R
import android.app.PendingIntent
import android.content.Intent

/**
 * The one notification channel this app posts to, and the plumbing around it.
 *
 * A single channel rather than one per calendar: Android's own per-channel controls
 * would then compete with the app's own switches, and a user who silenced a channel in
 * system settings would see the app's toggle still claiming to be on. The switches live
 * in one place — the app's settings — and decide what is scheduled at all.
 */
object Reminders {

    const val CHANNEL_ID = "ezygo_reminders"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Reminders",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Upcoming events and task deadlines"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    /** False when the user has not granted notifications, which is not an error. */
    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun post(context: Context, id: Int, title: String, body: String?) {
        if (!canPost(context)) return

        val open = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .apply { if (!body.isNullOrBlank()) setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)) }
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        // Permission is checked above; the catch is for an OEM that refuses anyway
        // rather than for the ordinary denied case.
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
