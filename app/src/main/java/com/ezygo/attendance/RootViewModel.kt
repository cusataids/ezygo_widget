package com.ezygo.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ezygo.attendance.data.SessionManager
import com.ezygo.attendance.data.SessionState
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.repo.AuthRepository
import com.ezygo.attendance.data.repo.DashboardStore
import com.ezygo.attendance.data.repo.StoredLoginResult
import com.ezygo.attendance.domain.model.ThemeChoice
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A failed automatic sign-in, once it is worth telling the user about.
 *
 * Only ever set after the grace period, so the mere existence of this is the signal
 * to offer Retry — the screen does not do any timing of its own.
 */
data class AutoLoginFailure(val message: String)

@HiltViewModel
class RootViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val dashboardStore: DashboardStore,
    private val authRepository: AuthRepository,
    preferences: PreferencesStore,
) : ViewModel() {

    val sessionState = sessionManager.state

    /**
     * The chosen theme, read here because it has to be applied above the navigation
     * graph rather than inside it.
     *
     * Eagerly, and seeded with the default rather than a null: the very first frame
     * is drawn before any preference read can complete, and starting from anything
     * other than the app's own default would show a flash of the wrong theme on
     * every cold start.
     */
    val theme: StateFlow<ThemeChoice> = preferences.theme
        .stateIn(viewModelScope, SharingStarted.Eagerly, ThemeChoice.DEFAULT)

    /**
     * Set when an automatic sign-in has failed for a reason retrying might fix, and
     * only once [RETRY_GRACE_MS] has passed since that attempt began.
     */
    private val _autoLoginFailure = MutableStateFlow<AutoLoginFailure?>(null)
    val autoLoginFailure: StateFlow<AutoLoginFailure?> = _autoLoginFailure.asStateFlow()

    /**
     * What to tell the user on the login screen about why they are looking at it.
     *
     * Null for an ordinary signed-out start — nobody needs telling they are not
     * signed in when they have never signed in.
     */
    private val _signedOutNotice = MutableStateFlow<String?>(null)
    val signedOutNotice: StateFlow<String?> = _signedOutNotice.asStateFlow()

    /** The attempt in flight, so a user-driven retry replaces it rather than racing it. */
    private var attempt: Job? = null

    init {
        viewModelScope.launch {
            // Read this before restoring, so the branch below can move straight from
            // "no token" into Authenticating without the login page appearing in the
            // gap. There is no suspension between the two, so no frame can observe
            // the intermediate SignedOut.
            val canSilentAuth = authRepository.savedCredentials() != null

            sessionManager.restore()

            when {
                // A stored token is taken at face value. Verifying it with a login
                // call on every launch would be a needless round trip; the token is
                // only replaced once the server actually rejects it with a 401.
                sessionManager.state.value == SessionState.SignedIn ->
                    dashboardStore.initialLoad()

                // No usable token, but the user asked to be remembered. Sign in
                // behind the loading state instead of showing the login page.
                canSilentAuth -> signInSilently(parkLoading = true)

                // Nothing to go on: a genuine signed-out start.
                else -> sessionManager.onSilentAuthFailed()
            }
        }

        viewModelScope.launch {
            sessionManager.expired.collectLatest {
                // onUnauthorized has dropped the in-memory token and latched the
                // renewal. Every path out of here has to release that latch and leave
                // the session either signed in, signed out, or holding a retry — the
                // finally covers the one path that is not ours to choose, this scope
                // being cancelled with the activity while the sign-in is still in
                // flight.
                try {
                    sessionManager.clearStoredToken()

                    // Rate limit rather than trust the failure modes. A wrong password
                    // is discarded by tryStoredLogin, but an account that authenticates
                    // and is then rejected on every call would otherwise sign in, 401,
                    // and sign in again in a tight loop.
                    val now = System.currentTimeMillis()
                    val withinCooldown = now - lastSilentAttemptMs < SILENT_RETRY_COOLDOWN_MS
                    if (withinCooldown) {
                        sessionManager.onSilentAuthFailed()
                        return@collectLatest
                    }
                    // Not parkLoading: onUnauthorized deliberately leaves a
                    // session that is already showing cached attendance alone,
                    // and forcing a loading state here would throw away content
                    // that is on screen and still true.
                    signInSilently(parkLoading = false)
                } finally {
                    sessionManager.onRenewalSettled()
                }
            }
        }

        // A session that comes good — however it came good — has nothing left to
        // explain or to retry.
        viewModelScope.launch {
            sessionManager.state.collectLatest { state ->
                if (state == SessionState.SignedIn) {
                    _signedOutNotice.value = null
                    _autoLoginFailure.value = null
                }
            }
        }
    }

    /** Try the stored password again, at the user's asking. */
    fun retryAutoLogin() {
        // The cooldown exists to stop the app looping on its own. Someone pressing
        // Retry is not a loop, so it does not apply to them.
        attempt?.cancel()
        attempt = viewModelScope.launch { signInSilently(parkLoading = true) }
    }

    /** Give up on the automatic path and let the user type a password. */
    fun signInManually() {
        attempt?.cancel()
        _autoLoginFailure.value = null
        sessionManager.onSilentAuthFailed()
    }

    /**
     * One automatic sign-in, and what each outcome means for the screen.
     *
     *  - Rejected password: straight to the login screen with an explanation. Nothing
     *    is gained by waiting, because nothing but the user can fix it.
     *  - Anything else, with nothing on screen: hold the loading state and offer
     *    Retry, but not before [RETRY_GRACE_MS] has passed since this attempt
     *    started. A slow network that succeeds on its own at eight seconds should
     *    never have shown a Retry button at two.
     *  - Anything else, with cached attendance already showing: the login screen,
     *    carrying the reason. See the branch for why Retry cannot serve that case.
     *
     * [parkLoading] is false for the renewal that follows a 401 mid-session, where
     * taking over the screen with a loading state would discard content the user is
     * reading and that is still correct.
     */
    private suspend fun signInSilently(parkLoading: Boolean) {
        val startedAt = System.currentTimeMillis()
        lastSilentAttemptMs = startedAt
        _autoLoginFailure.value = null
        if (parkLoading) sessionManager.beginSilentAuth()

        when (val result = authRepository.tryStoredLogin()) {
            StoredLoginResult.Succeeded -> {
                _signedOutNotice.value = null
                dashboardStore.initialLoad()
            }

            StoredLoginResult.CredentialsRejected -> {
                _signedOutNotice.value = LOGGED_OUT_MESSAGE
                sessionManager.onSilentAuthFailed()
            }

            StoredLoginResult.NoCredentials -> sessionManager.onSilentAuthFailed()

            is StoredLoginResult.Unavailable ->
                if (sessionManager.state.value == SessionState.SignedIn) {
                    // A renewal that failed underneath a screen full of cached
                    // attendance. Holding a Retry prompt here is not an option: the
                    // prompt lives on the loading state, and the session has no token
                    // left, so every call the user makes from here would 401. They go
                    // to the login screen — as they always did — but now it says why.
                    _signedOutNotice.value = result.error.userMessage
                    sessionManager.onSilentAuthFailed()
                } else {
                    holdForRetry(result.error.userMessage, startedAt)
                }
        }
    }

    /**
     * Keeps the loading state, then offers Retry once the grace period is up.
     *
     * The session is deliberately left on Authenticating rather than moved to
     * SignedOut: the stored password is still believed good, and dropping the user on
     * a login form because the server was briefly unreachable would ask them to solve
     * a problem that is not theirs. The renewal latch is released even so, so a later
     * 401 is not swallowed while this sits here.
     */
    private suspend fun holdForRetry(message: String, startedAt: Long) {
        sessionManager.onRenewalSettled()
        val waited = System.currentTimeMillis() - startedAt
        if (waited < RETRY_GRACE_MS) delay(RETRY_GRACE_MS - waited)
        _autoLoginFailure.value = AutoLoginFailure(message)
    }

    private var lastSilentAttemptMs = 0L

    private companion object {
        /** Minimum gap between background sign-in attempts the app starts itself. */
        const val SILENT_RETRY_COOLDOWN_MS = 30_000L

        /**
         * How long an automatic sign-in is given before Retry is offered.
         *
         * Measured from the start of the attempt, not from its failure, so a request
         * that fails fast still waits this out. Offering Retry a second after launch
         * invites a tap that cannot help, and the request it would replace is usually
         * about to answer anyway.
         */
        const val RETRY_GRACE_MS = 10_000L

        const val LOGGED_OUT_MESSAGE = "You have been logged out. Please log in again."
    }
}
