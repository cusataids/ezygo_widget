package com.ezygo.attendance.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

/**
 * Lets a dialog use the width it actually needs.
 *
 * Compose's `AlertDialog` sizes itself to the platform default, which on a phone is a
 * good deal narrower than the screen. That is right for a short confirmation and
 * wrong for the dialogs in this app that carry a calendar, a session picker or a list
 * of class meetings — those were wrapping names onto three lines with empty gutters
 * either side of them.
 *
 * A fraction rather than a fixed width, so the margin scales with the device, with a
 * ceiling so the same dialog does not stretch into an unreadable line on a tablet.
 * The dialog still sits inside the window, so insets and system bars are respected by
 * the host rather than by each caller.
 */
val WideDialogProperties = DialogProperties(usePlatformDefaultWidth = false)

/** The matching modifier. Use with [WideDialogProperties]; neither works alone. */
@Composable
fun wideDialogModifier(): Modifier =
    Modifier.fillMaxWidth(WIDE_DIALOG_FRACTION).widthIn(max = WIDE_DIALOG_MAX_WIDTH)

/** Enough of the screen to be roomy, with a margin that still reads as a dialog. */
private const val WIDE_DIALOG_FRACTION = 0.94f

/** Beyond this a dialog stops being a dialog and the lines get hard to track. */
private val WIDE_DIALOG_MAX_WIDTH = 640.dp
