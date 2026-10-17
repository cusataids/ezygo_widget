package com.ezygo.attendance.data.remote

import com.ezygo.attendance.di.CalendarHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Outcome of asking the feed for its current contents. */
sealed interface IcsResponse {
    /** The feed has not changed since [IcsRequest.etag] was issued. */
    data object Unchanged : IcsResponse

    data class Content(val body: String, val etag: String?) : IcsResponse
}

/**
 * Fetches an iCalendar feed.
 *
 * Deliberately **not** the app's main OkHttp client. Two reasons, both of which
 * matter more than the duplication costs:
 *
 *  - the shared client attaches the EzyGo bearer token to outgoing requests, and
 *    that token must never be sent to a third-party host;
 *  - the shared client logs request URLs in debug builds, and a published-calendar
 *    address is a bearer secret in its own right — anyone holding the URL can read
 *    the calendar, so it must not reach logcat.
 */
@Singleton
class IcsClient @Inject constructor(
    @CalendarHttpClient private val client: OkHttpClient,
) {

    suspend fun fetch(url: String, etag: String?): IcsResponse = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url(url).header("Accept", "text/calendar")
        // A conditional request is what makes a routine sync nearly free: an
        // unchanged calendar answers 304 with no body at all.
        if (!etag.isNullOrBlank()) builder.header("If-None-Match", etag)

        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == HTTP_NOT_MODIFIED -> IcsResponse.Unchanged
                !response.isSuccessful ->
                    // Deliberately excludes the URL: it is the calendar's password.
                    throw IOException("Calendar feed returned ${response.code}")
                else -> IcsResponse.Content(
                    // OkHttp 5 made body non-null; the old safe call and orEmpty
                    // were guarding against a null that can no longer happen.
                    body = response.body.string(),
                    etag = response.header("ETag"),
                )
            }
        }
    }

    private companion object {
        const val HTTP_NOT_MODIFIED = 304
    }
}
