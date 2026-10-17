package com.ezygo.attendance.ui.common

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * The question asked before anything is deleted.
 *
 * One dialog for every delete in the app, so the wording, the colour of the confirming
 * button and the order of the two buttons are the same wherever it appears — a reader
 * should never have to check which side "Delete" is on.
 *
 * [what] names the thing, because "Delete this?" is not a question anyone can answer.
 */
@Composable
fun ConfirmDeleteDialog(
    what: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Delete",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$confirmLabel $what?") },
        text = { Text("This cannot be undone.", style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Why a delete did not happen.
 *
 * Distinct from an error banner on purpose: nothing failed and there is nothing to
 * retry. The item is still there because it is not this app's to remove, and the only
 * useful response is to read the reason and carry on — so it gets one button.
 */
@Composable
fun CannotDeleteDialog(message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cannot delete this here") },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } },
    )
}
