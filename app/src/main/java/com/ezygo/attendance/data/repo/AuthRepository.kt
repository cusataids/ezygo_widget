package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.SessionManager
import com.ezygo.attendance.data.local.AppDatabase
import com.ezygo.attendance.data.local.CredentialStore
import com.ezygo.attendance.data.local.DraftStore
import com.ezygo.attendance.data.local.PreferencesStore
import com.ezygo.attendance.data.local.SavedCredentials
import com.ezygo.attendance.data.remote.ApiError
import com.ezygo.attendance.data.remote.EzygoApi
import com.ezygo.attendance.data.remote.dto.LoginRequest
import com.ezygo.attendance.data.remote.dto.LookupRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How an automatic sign-in with the stored password ended.
 *
 * A boolean used to be enough, back when every failure meant the same thing: show the
 * login screen. It is not enough now, because the two failures need opposite
 * treatment — a rejected password can only be fixed by the user typing a new one,
 * while a server that is down fixes itself and should not cost anyone their session.
 */
sealed interface StoredLoginResult {

    data object Succeeded : StoredLoginResult

    /**
     * The portal rejected the stored password, which on this API is an HTTP 422.
     *
     * [ApiError.fromLogin] folds 422, 401 and 403 together as [ApiError.BadCredentials]
     * because all three mean the same thing at the login endpoint, and the portal is
     * documented to answer a wrong password with 422 rather than 401. The password has
     * already been discarded by the time this is returned — retrying it would only
     * hammer the endpoint with something known not to work.
     */
    data object CredentialsRejected : StoredLoginResult

    /**
     * Something else went wrong: no network, a 5xx, an unexpected shape. The password
     * is still held and is still believed good, so this is worth trying again.
     */
    data class Unavailable(val error: ApiError) : StoredLoginResult

    /** Nothing stored to try with. Not a failure — there was never an attempt. */
    data object NoCredentials : StoredLoginResult
}

@Singleton
class AuthRepository @Inject constructor(
    private val api: EzygoApi,
    private val sessionManager: SessionManager,
    private val database: AppDatabase,
    private val courseCatalogRepository: CourseCatalogRepository,
    private val notificationsRepository: NotificationsRepository,
    private val calendarRepository: CalendarRepository,
    private val notesRepository: NotesRepository,
    private val samarthRepository: SamarthRepository,
    private val credentialStore: CredentialStore,
    private val drafts: DraftStore,
    private val preferences: PreferencesStore,
) {

    /**
     * Confirms the account exists before any password is sent.
     *
     * The portal echoes back the usernames it recognises; an empty list means no
     * such account, and login must not be attempted in that case.
     */
    suspend fun ensureUserExists(username: String) {
        val response = try {
            api.lookup(LookupRequest(username.trim()))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throw ApiError.fromLookup(throwable)
        }
        // A non-empty list means the portal recognised the account. Membership is not
        // matched exactly, because the portal may answer with a canonical username
        // that differs from what was typed (an email alias, for instance).
        if (response.users.isEmpty()) throw ApiError.UserNotFound
    }

    /**
     * Exchanges credentials for a bearer token.
     *
     * The password is always kept, sealed in [CredentialStore], so an expired session
     * renews itself without the user meeting the login screen again. It is never a
     * choice at sign-in; [forgetCredentials] and [signOut] are what remove it, and a
     * password the portal later rejects is discarded rather than retried.
     */
    suspend fun login(username: String, password: String) {
        val response = try {
            api.login(LoginRequest(username.trim(), password, stayLoggedIn = true))
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            throw ApiError.fromLogin(throwable)
        }
        if (response.accessToken.isBlank()) throw ApiError.BadCredentials

        preferences.setRememberPassword(true)
        credentialStore.save(username.trim(), password)
        sessionManager.onLoggedIn(response.accessToken)
    }

    /**
     * The stored credentials, if there are any still worth trying.
     *
     * The flag behind this is no longer a user preference but a record of whether a
     * usable password is held: [forgetCredentials] clears it when the portal rejects
     * one, which is what stops a changed password being retried on every launch.
     */
    suspend fun savedCredentials(): SavedCredentials? =
        if (preferences.currentRememberPassword()) credentialStore.current() else null

    /**
     * Re-authenticates in the background with stored credentials.
     *
     * A rejection means the password is no longer valid — it is discarded rather than
     * retried, so a changed password cannot leave the app hammering the login endpoint
     * on every launch. Every other failure keeps the password, because the password
     * was not what failed.
     */
    suspend fun tryStoredLogin(): StoredLoginResult {
        val saved = savedCredentials() ?: return StoredLoginResult.NoCredentials
        return try {
            login(saved.username, saved.password)
            StoredLoginResult.Succeeded
        } catch (cancellation: kotlinx.coroutines.CancellationException) {
            throw cancellation
        } catch (throwable: Throwable) {
            when (val error = ApiError.from(throwable)) {
                is ApiError.BadCredentials -> {
                    forgetCredentials()
                    StoredLoginResult.CredentialsRejected
                }
                else -> StoredLoginResult.Unavailable(error)
            }
        }
    }

    /** Drops the stored password but leaves the session alone. */
    suspend fun forgetCredentials() {
        preferences.setRememberPassword(false)
        credentialStore.clear()
    }

    /** Clears the session and every cached row belonging to it. */
    suspend fun signOut() {
        sessionManager.signOut()
        with(database.attendanceDao()) {
            clearAllRecords()
            clearAllCourses()
            clearAllEnrolled()
            clearAllSessions()
            clearAllPeriods()
        }
        with(database.timetableDao()) {
            clearAllSchedules()
            clearAllSessions()
            clearAllMeta()
            // The cohort counts go with the rest of the timetable cache: they
            // describe another account's class and must not outlive its session.
            clearAllCounts()
            // And the note of when they were read, or the next
            // account would inherit this one's freshness.
            clearAllClassCountsMeta()
        }
        database.profileDao().clear()
        courseCatalogRepository.clear()
        notificationsRepository.clear()
        // Local events go too. They are the one thing here the user created rather
        // than downloaded, which is why the sign-out dialog says so plainly before
        // this runs.
        calendarRepository.clear()
        // Notes are the user's own writing too, and go for the same reason.
        notesRepository.clear()
        // And the Samarth side entirely: its sign-in, the cached record, the grade
        // card PDFs. Signing out of one account must not leave another's data behind.
        samarthRepository.clear()
        // The estimator drafts belong to this account and go with it.
        drafts.clear()
        // Signing out is an explicit "forget me", so the stored password goes too.
        forgetCredentials()
    }
}
