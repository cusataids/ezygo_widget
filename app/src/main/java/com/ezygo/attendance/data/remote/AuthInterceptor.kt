package com.ezygo.attendance.data.remote

import android.util.Log
import com.ezygo.attendance.BuildConfig
import com.ezygo.attendance.data.SessionManager
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Attaches the bearer token to authenticated calls and reacts to rejection.
 *
 * Login and lookup are deliberately excluded — sending a stale token to them would
 * be pointless and could mask a genuine credential error.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val sessionManager: SessionManager,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath
        val isAuthCall = path.endsWith("/login") || path.endsWith("/login/lookup")
        // Belt and braces. Nothing should be routing a third-party request through
        // this client, but a token attached to the wrong host is a leak that no
        // amount of care elsewhere can undo, so the host is checked rather than
        // assumed.
        val isEzygo = request.url.host.equals(EzygoApi.HOST, ignoreCase = true)

        val token = sessionManager.token()
        // Laravel-backed APIs branch on Accept: without it they can fall back to
        // session/redirect behaviour instead of returning JSON.
        val builder = request.newBuilder().header("Accept", "application/json")
        if (isEzygo && !isAuthCall && !token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val outgoing = builder.build()

        val response = chain.proceed(outgoing)

        // Debug builds surface the body of failed calls so an unexpected server
        // response can actually be diagnosed. Only non-2xx bodies are logged, and
        // those never carry an access token — a successful login response, which
        // does, is never touched. peekBody leaves the stream intact for Retrofit.
        if (BuildConfig.DEBUG && !response.isSuccessful) {
            runCatching {
                Log.d(
                    "EzygoApi",
                    "${response.code} $path -> ${response.peekBody(MAX_LOGGED_ERROR_BYTES).string()}",
                )
            }
        }

        // A rejected token on an authenticated call means the session is over.
        if (isEzygo && !isAuthCall && (response.code == 401 || response.code == 403)) {
            sessionManager.onUnauthorized()
        }
        return response
    }

    private companion object {
        const val MAX_LOGGED_ERROR_BYTES = 4096L
    }
}
