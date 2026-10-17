package com.ezygo.attendance.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay

/**
 * How long until a moment, as a short phrase that keeps itself up to date.
 *
 * "Tomorrow 10:00" says when; this says how soon, which is the question actually being
 * asked of something labelled "coming up". The two are different enough to be worth
 * both — a date tells you to plan, a countdown tells you to move.
 *
 * Returns null once the moment has passed. A countdown that keeps going negative is a
 * stopwatch, and nobody asked for one.
 */
@Composable
fun rememberCountdown(epochMs: Long, nowMs: () -> Long = System::currentTimeMillis): String? {
    // Ticking only as fast as the answer can change: once a minute is enough for
    // anything beyond an hour away, and the last minute is the only one worth
    // counting by seconds. A blanket one-second tick would wake the whole list
    // every second to redraw text that had not changed.
    val initial = remember(epochMs) { countdownLabel(epochMs - nowMs()) }
    return produceState(initialValue = initial, epochMs) {
        while (true) {
            val left = epochMs - nowMs()
            value = countdownLabel(left)
            if (left <= 0L) return@produceState
            delay(if (left < MINUTE_MS) SECOND_MS else MINUTE_MS)
        }
    }.value
}

/**
 * The phrase itself.
 *
 * One unit, or two where the second one changes the answer: "in 2d" is vague when it
 * could be "in 2d 6h", and "in 1h 05m" is the difference between finishing a coffee
 * and leaving now. Below a minute it stops counting and says the thing is happening,
 * because the exact second is never what the reader needed.
 */
internal fun countdownLabel(remainingMs: Long): String? = when {
    remainingMs <= 0L -> null
    remainingMs < MINUTE_MS -> "now"
    remainingMs < HOUR_MS -> "in ${remainingMs / MINUTE_MS}m"
    remainingMs < DAY_MS -> {
        val hours = remainingMs / HOUR_MS
        val minutes = (remainingMs % HOUR_MS) / MINUTE_MS
        if (minutes == 0L) "in ${hours}h" else "in ${hours}h ${minutes}m"
    }
    else -> {
        val days = remainingMs / DAY_MS
        val hours = (remainingMs % DAY_MS) / HOUR_MS
        if (hours == 0L) "in ${days}d" else "in ${days}d ${hours}h"
    }
}

private const val SECOND_MS = 1_000L
private const val MINUTE_MS = 60 * SECOND_MS
private const val HOUR_MS = 60 * MINUTE_MS
private const val DAY_MS = 24 * HOUR_MS
