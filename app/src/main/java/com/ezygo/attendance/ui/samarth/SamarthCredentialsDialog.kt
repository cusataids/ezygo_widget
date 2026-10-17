package com.ezygo.attendance.ui.samarth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ezygo.attendance.R
import com.ezygo.attendance.ui.common.Gap

/**
 * Asks for the Samarth sign-in, once.
 *
 * Shown by whichever screen needs the portal and finds no credentials stored, and by
 * either of them again if the portal later rejects what was saved. The password can be
 * revealed here because everything in this dialog is what the user is typing right
 * now; nothing restored from storage is ever put in front of it.
 */
@Composable
fun SamarthCredentialsDialog(
    onSave: (username: String, password: String) -> Unit,
    onDismiss: () -> Unit,
    error: String? = null,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sign in to Samarth") },
        text = {
            Column {
                Text(
                    "Your Samarth sign-in is stored encrypted on this device and used " +
                        "to fetch your profile and grade cards. You will not be asked again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(8)
                // Above the fields rather than under them: this is the second portal
                // the app signs in to on the user's behalf and the second place they
                // hand over a password, so it is said before anything is typed.
                Text(
                    stringResource(R.string.disclaimer_samarth),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Gap(14)
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Samarth username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(10)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Samarth password") },
                    singleLine = true,
                    visualTransformation = if (visible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { visible = !visible }) {
                            Icon(
                                imageVector = if (visible) {
                                    Icons.Default.VisibilityOff
                                } else {
                                    Icons.Default.Visibility
                                },
                                contentDescription = if (visible) "Hide password" else "Show password",
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) {
                    Gap(10)
                    Text(
                        error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(username.trim(), password) },
                enabled = username.isNotBlank() && password.isNotEmpty(),
            ) { Text("Save and continue") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
