package com.ezygo.attendance

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ezygo.attendance.data.SessionState
import com.ezygo.attendance.notifications.Reminders
import com.ezygo.attendance.ui.MainNavigation
import com.ezygo.attendance.ui.common.HomeSkeleton
import com.ezygo.attendance.ui.login.LoginScreen
import com.ezygo.attendance.ui.theme.EzygoTheme
import com.ezygo.attendance.ui.theme.resolveDark
import com.ezygo.attendance.widget.EzygoWidget
import com.ezygo.attendance.widget.redrawAll
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /**
     * Course the app was asked to open, from a widget tap. Held as state rather than
     * read once, because the activity is singleTop: tapping a second widget row
     * arrives through [onNewIntent] on the running instance.
     *
     * Deliberately not restored across a recreation. It is a request that has already
     * been carried out, not a piece of the screen's state, and reinstating it would
     * push the subject back on top of wherever the user had got to.
     */
    private val requestedCourseId = MutableStateFlow<Int?>(null)
    private val courseRequests: StateFlow<Int?> get() = requestedCourseId.asStateFlow()

    /** Calendar event a widget row asked for, held for the same reason. */
    private val requestedEventId = MutableStateFlow<String?>(null)
    private val eventRequests: StateFlow<String?> get() = requestedEventId.asStateFlow()

    /**
     * Asked for once, on first launch.
     *
     * The result is not acted on: reminders simply schedule nothing while the
     * permission is missing, and the profile screen says so rather than nagging.
     */
    private val askForNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only on a genuine first creation. A recreated activity — a rotation, a theme
        // change, a restore after the process was killed — is handed the same launch
        // intent again, and reading it a second time re-navigated to whatever the
        // widget row had asked for, however far the user had moved on since. A later
        // tap on a widget arrives through onNewIntent instead and is acted on there.
        if (savedInstanceState == null) consume(intent)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !Reminders.canPost(this)
        ) {
            askForNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            // Resolved above EzygoTheme, because the theme wraps everything below it
            // and cannot read state that only exists inside itself. The activity is
            // the ViewModelStoreOwner either way, so this is the same instance
            // RootScreen would otherwise have resolved for itself — passing it down
            // overrides the default argument rather than creating a second one.
            val rootViewModel: RootViewModel = hiltViewModel()
            val theme by rootViewModel.theme.collectAsStateWithLifecycle()
            EzygoTheme(darkTheme = theme.resolveDark()) {
                RootScreen(
                    viewModel = rootViewModel,
                    courseRequests = courseRequests,
                    onCourseHandled = { requestedCourseId.value = null },
                    eventRequests = eventRequests,
                    onEventHandled = { requestedEventId.value = null },
                )
            }
        }
    }

    /**
     * Repairs the home-screen widgets whenever the app is opened.
     *
     * Widgets were previously only redrawn at the end of a successful dashboard
     * refresh, so a widget that failed to draw when it was added stayed broken for
     * anyone who was offline, signed out, or whose refresh errored — the one group
     * most likely to go looking at the widget in the first place. This reads the
     * Room cache and costs no network.
     */
    override fun onStart() {
        super.onStart()
        redrawAll(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        val courseId = intent?.getIntExtra(EzygoWidget.EXTRA_COURSE_ID, -1) ?: -1
        if (courseId > 0) requestedCourseId.value = courseId

        val eventId = intent?.getStringExtra(EzygoWidget.EXTRA_EVENT_ID)
        if (!eventId.isNullOrBlank()) requestedEventId.value = eventId
    }
}

/**
 * Chooses between login and the app based on the restored session.
 *
 * The login screen appears only once the session is known to be gone. While the
 * stored token is being read, and while stored credentials are being used to sign
 * in again, the dashboard skeleton shows instead — otherwise the login form flashes
 * past on cold start and after every expiry, even though the user never has to
 * touch it.
 */
@Composable
private fun RootScreen(
    courseRequests: StateFlow<Int?>,
    onCourseHandled: () -> Unit,
    eventRequests: StateFlow<String?>,
    onEventHandled: () -> Unit,
    viewModel: RootViewModel = hiltViewModel(),
) {
    val sessionState by viewModel.sessionState.collectAsStateWithLifecycle()
    val autoLoginFailure by viewModel.autoLoginFailure.collectAsStateWithLifecycle()
    val signedOutNotice by viewModel.signedOutNotice.collectAsStateWithLifecycle()
    val requestedCourseId by courseRequests.collectAsStateWithLifecycle()
    val requestedEventId by eventRequests.collectAsStateWithLifecycle()

    when (sessionState) {
        // Both "still reading the token" and "signing in behind the scenes" show the
        // dashboard skeleton. Showing LoginScreen here is what made the login page
        // flash past on launch even when stored credentials were about to work.
        SessionState.Unknown,
        SessionState.Authenticating,
        // Surface, not a bare Box: this is the one branch with no Scaffold of
        // its own, so without something painting the theme background the skeleton
        // showed through to the window behind it.
        -> Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            // The failure replaces the skeleton rather than sitting under it: a
            // skeleton says "nearly there", and once the attempt has failed that is
            // no longer true. It appears only once the view model has decided the
            // grace period is up, so this branch does no timing of its own.
            val failure = autoLoginFailure
            if (failure == null) {
                HomeSkeleton()
            } else {
                AutoLoginFailed(
                    message = failure.message,
                    onRetry = viewModel::retryAutoLogin,
                    onSignIn = viewModel::signInManually,
                )
            }
        }

        SessionState.SignedOut -> LoginScreen(notice = signedOutNotice)
        SessionState.SignedIn -> MainNavigation(
            requestedCourseId = requestedCourseId,
            onCourseHandled = onCourseHandled,
            requestedEventId = requestedEventId,
            onEventHandled = onEventHandled,
        )
    }
}

/**
 * An automatic sign-in that did not work, and the two ways out of it.
 *
 * Shown in place of the loading skeleton, and only for failures that are not the
 * password's fault — a rejected password goes straight to the login screen instead,
 * because no amount of retrying will fix it.
 *
 * Retry is the primary action because the stored password is still believed good and
 * the usual cause is a network that has since come back. Signing in by hand is
 * offered beside it all the same: without it, anyone whose server stays unreachable
 * would be held on this screen with no way through to a login form at all.
 */
@Composable
private fun AutoLoginFailed(
    message: String,
    onRetry: () -> Unit,
    onSignIn: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Could not sign you in",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) { Text("Retry") }
        TextButton(onClick = onSignIn) { Text("Sign in with a password") }
    }
}
