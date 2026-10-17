package com.ezygo.attendance.data

import com.ezygo.attendance.data.local.TokenStore
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether a usable session exists.
 *
 * [Unknown] only until the stored token is read; [Authenticating] while a
 * background sign-in is in flight. Both mean "not decided yet", and the UI waits
 * on a loading state rather than showing the login page, which would otherwise
 * flash past whenever stored credentials were about to restore the session.
 */
enum class SessionState { Unknown, Authenticating, SignedIn, SignedOut }

/**
 * Single owner of the live session.
 *
 * The token is kept in memory for the interceptor to read synchronously, and
 * mirrored to encrypted storage so the app can restore the session on next launch
 * without showing the login screen again.
 */
@Singleton
class SessionManager @Inject constructor(
    private val tokenStore: TokenStore,
) {
    @Volatile
    private var inMemoryToken: String? = null

    /**
     * True while a background sign-in is under way.
     *
     * A refresh fires several calls at once, so an expired token comes back as a burst
     * of 401s rather than one. Without this every one of them emitted [expired], the
     * collector's `collectLatest` cancelled the renewal already in flight and started
     * another, and the second attempt landed inside its own retry cooldown — which
     * signed the user out with perfectly good credentials.
     */
    @Volatile
    private var renewing = false

    private val _state = MutableStateFlow(SessionState.Unknown)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    /** Emits when the server rejected the token so the UI can route back to login. */
    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    /** Read synchronously by the auth interceptor; never logged. */
    fun token(): String? = inMemoryToken

    /** Hydrates the in-memory token from storage at startup. */
    suspend fun restore() {
        val stored = tokenStore.currentToken()
        inMemoryToken = stored
        renewing = false
        _state.value = if (stored.isNullOrBlank()) SessionState.SignedOut else SessionState.SignedIn
    }

    suspend fun onLoggedIn(token: String) {
        inMemoryToken = token
        tokenStore.save(token)
        renewing = false
        _state.value = SessionState.SignedIn
    }

    suspend fun signOut() {
        inMemoryToken = null
        tokenStore.clear()
        renewing = false
        _state.value = SessionState.SignedOut
    }

    /**
     * Called from the network layer on a 401/403. Clears the in-memory token
     * immediately so no further request goes out with it; cached attendance is
     * deliberately left in place so the user still sees their data.
     *
     * The state is only parked on [SessionState.Authenticating] when nothing has been
     * shown yet. Once the app is displaying cached attendance, that data is still on
     * screen and still true, and a token being renewed behind it is not news: dropping
     * the whole UI for a loading skeleton to renew a token was the app throwing away
     * what it already knew and had already drawn.
     *
     * Whoever collects [expired] is responsible for resolving this to SignedIn or
     * SignedOut — including when their attempt is cancelled, or no further 401 will
     * ever start another.
     */
    fun onUnauthorized() {
        if (_state.value == SessionState.SignedOut) return
        inMemoryToken = null
        // One renewal per burst. See [renewing].
        if (renewing) return
        renewing = true
        if (_state.value == SessionState.Unknown) _state.value = SessionState.Authenticating
        _expired.tryEmit(Unit)
    }

    /** A background sign-in has started; hold the UI on its loading state. */
    fun beginSilentAuth() {
        renewing = true
        _state.value = SessionState.Authenticating
    }

    /** A background sign-in finished without a session, so the user must sign in. */
    fun onSilentAuthFailed() {
        renewing = false
        _state.value = SessionState.SignedOut
    }

    /**
     * Releases the renewal latch however the attempt ended, cancellation included.
     *
     * Without this a renewal cancelled halfway — the activity being destroyed while it
     * ran is enough — would leave the latch closed for the life of the process, and
     * every later 401 would be swallowed in silence.
     */
    fun onRenewalSettled() {
        renewing = false
    }

    /** Completes the sign-out that [onUnauthorized] started, off the network thread. */
    suspend fun clearStoredToken() = tokenStore.clear()
}
