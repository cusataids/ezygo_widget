package com.ezygo.attendance.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.R
import com.ezygo.attendance.ui.common.Gap
import com.ezygo.attendance.ui.common.InlineError

@Composable
fun LoginScreen(
    /**
     * Why the user is looking at this screen, when it was not their idea.
     *
     * Set when an automatic sign-in was turned down by the portal — a changed
     * password, most often. Kept apart from [LoginUiState.error], which belongs to
     * the attempt happening on this screen: this one explains the arrival, that one
     * explains a failure. Showing the arrival notice under the heading, before the
     * fields, is what makes it read as context rather than as a complaint about
     * something the user just did.
     */
    notice: String? = null,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val passwordFocus = remember { FocusRequester() }
    var passwordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(state.step) {
        if (state.step == LoginStep.Password) {
            passwordFocus.requestFocus()
        } else {
            passwordVisible = false
        }
    }

    Scaffold { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            // The app's own name, from the one place it is defined. Hardcoding it
            // here is what let the login screen keep calling the app "Attendance"
            // after it had been renamed everywhere the manifest reaches.
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Sign in with your EzyGo account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            notice?.let {
                Gap(16)
                InlineError(it)
            }
            Gap(28)

            OutlinedTextField(
                value = state.username,
                onValueChange = viewModel::onUsernameChange,
                label = { Text("Username or email") },
                singleLine = true,
                enabled = state.step == LoginStep.Username && !state.isBusy,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(onNext = { viewModel.submitUsername() }),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.step == LoginStep.Password) {
                // Revealing is offered only for a password the user typed. While the
                // field still holds the one restored from storage there is no control
                // to uncover it, and the transformation ignores the toggle outright
                // rather than trusting that the control is merely absent.
                val canReveal = !state.usingSavedPassword

                Gap(12)
                OutlinedTextField(
                    value = state.password,
                    onValueChange = viewModel::onPasswordChange,
                    label = { Text("Password") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    visualTransformation = if (canReveal && passwordVisible) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = if (!canReveal) {
                        null
                    } else {
                        {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) {
                                        Icons.Default.VisibilityOff
                                    } else {
                                        Icons.Default.Visibility
                                    },
                                    contentDescription = if (passwordVisible) {
                                        "Hide password"
                                    } else {
                                        "Show password"
                                    },
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.submitPassword() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(passwordFocus),
                )
            }

            state.error?.let {
                Gap(12)
                InlineError(it)
            }

            Gap(20)
            Button(
                onClick = {
                    if (state.step == LoginStep.Username) {
                        viewModel.submitUsername()
                    } else {
                        viewModel.submitPassword()
                    }
                },
                enabled = !state.isBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (state.step == LoginStep.Username) "Continue" else "Sign in")
                }
            }

            if (state.step == LoginStep.Password) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = viewModel::backToUsername, enabled = !state.isBusy) {
                        Text("Use a different account")
                    }
                }
            }

            Gap(24)
            // First contact is where someone might mistake this for an official app,
            // so the disclaimer belongs here as well as on the profile screen.
            Text(
                stringResource(R.string.disclaimer_short),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
