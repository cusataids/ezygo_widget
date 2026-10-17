package com.ezygo.attendance.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.repo.AuthRepository
import com.ezygo.attendance.data.repo.DashboardStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Two-step sign in: confirm the account exists, then ask for the password. */
enum class LoginStep { Username, Password }

data class LoginUiState(
    val step: LoginStep = LoginStep.Username,
    val username: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    /**
     * True while [password] still holds the password restored from storage rather
     * than anything the user has typed.
     *
     * The screen offers no way to reveal it while this is set, and the first
     * keystroke replaces it outright — see [onPasswordChange] — so what can be
     * revealed afterwards is only ever what the user typed themselves.
     */
    val usingSavedPassword: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val dashboardStore: DashboardStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // Reaching the login screen with credentials stored means a silent re-login
        // was already tried and failed — most likely the password changed. The stored
        // password is restored so the account is still identified and a working one
        // needs no action at all, but it is never shown and never edited: the first
        // keystroke replaces it.
        viewModelScope.launch {
            val saved = authRepository.savedCredentials() ?: return@launch
            _state.update {
                it.copy(
                    username = saved.username,
                    password = saved.password,
                    usingSavedPassword = true,
                    step = LoginStep.Password,
                )
            }
        }
    }

    fun onUsernameChange(value: String) =
        _state.update { it.copy(username = value, error = null) }

    /**
     * Takes what the user typed, and only what the user typed.
     *
     * A restored password is never edited in place. Appending to it keeps the new
     * characters alone; anything else — a deletion, an edit in the middle — clears
     * the field. Either way no part of the stored password survives into a value the
     * user is then allowed to reveal.
     */
    fun onPasswordChange(value: String) = _state.update { current ->
        if (!current.usingSavedPassword) return@update current.copy(password = value, error = null)
        val appended = value.length > current.password.length &&
            value.startsWith(current.password)
        current.copy(
            password = if (appended) value.drop(current.password.length) else "",
            usingSavedPassword = false,
            error = null,
        )
    }

    fun backToUsername() = _state.update {
        it.copy(
            step = LoginStep.Username,
            password = "",
            usingSavedPassword = false,
            error = null,
        )
    }

    /** Looks the account up first, so a bad username never becomes a password attempt. */
    fun submitUsername() {
        val username = _state.value.username.trim()
        if (username.isEmpty()) {
            _state.update { it.copy(error = "Enter your username to continue.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching { authRepository.ensureUserExists(username) }
                .onSuccess { _state.update { it.copy(isBusy = false, step = LoginStep.Password) } }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(isBusy = false, error = ApiError.from(throwable).userMessage)
                    }
                }
        }
    }

    fun submitPassword() {
        val current = _state.value
        if (current.password.isEmpty()) {
            _state.update { it.copy(error = "Enter your password to continue.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isBusy = true, error = null) }
            runCatching {
                authRepository.login(
                    username = current.username.trim(),
                    password = current.password,
                )
            }
                .onSuccess {
                    // Drop the password from state the moment it is no longer
                    // needed. It is already sealed on disk by then.
                    _state.update {
                        it.copy(isBusy = false, password = "", usingSavedPassword = false)
                    }
                    // Warm the cache so the dashboard has data the instant it appears.
                    dashboardStore.initialLoad()
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isBusy = false,
                            password = "",
                            usingSavedPassword = false,
                            error = ApiError.from(throwable).userMessage,
                        )
                    }
                }
        }
    }
}
