package com.ezygo.attendance

import com.ezygo.attendance.data.remote.ApiError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * Locks in the mapping verified against the live EzyGo API.
 *
 * The written contract implied an unknown username returns 200 with an empty
 * `users` array and a bad password returns 401. In practice both endpoints answer
 * 422, so these cases are pinned here to stop a refactor quietly turning
 * "User not found" back into a generic "Something went wrong".
 */
class ApiErrorTest {

    private fun httpError(code: Int) = HttpException(
        Response.error<Any>(code, "{}".toResponseBody("application/json".toMediaType())),
    )

    @Test
    fun `lookup 422 means the account does not exist`() {
        assertEquals(ApiError.UserNotFound, ApiError.fromLookup(httpError(422)))
    }

    @Test
    fun `login 422 is a credential failure, not an expired session`() {
        assertEquals(ApiError.BadCredentials, ApiError.fromLogin(httpError(422)))
    }

    @Test
    fun `login 401 is also a credential failure`() {
        assertEquals(ApiError.BadCredentials, ApiError.fromLogin(httpError(401)))
    }

    @Test
    fun `401 on an authenticated endpoint means the session expired`() {
        assertEquals(ApiError.Unauthorized, ApiError.from(httpError(401)))
    }

    @Test
    fun `server errors and network failures stay distinct`() {
        assertTrue(ApiError.from(httpError(503)) is ApiError.Server)
        assertTrue(ApiError.from(IOException("offline")) is ApiError.Network)
    }

    @Test
    fun `an unexpected status on lookup is not reported as user not found`() {
        assertTrue(ApiError.fromLookup(httpError(500)) is ApiError.Server)
        assertTrue(ApiError.fromLookup(IOException("offline")) is ApiError.Network)
    }

    @Test
    fun `no user facing message leaks status codes or response bodies`() {
        val messages = listOf(
            ApiError.from(httpError(500)),
            ApiError.from(httpError(418)),
            ApiError.fromLookup(httpError(422)),
            ApiError.fromLogin(httpError(422)),
            ApiError.from(IOException("java.net.SocketTimeoutException at okhttp3")),
        ).map { it.userMessage }

        messages.forEach { message ->
            assertTrue(message, message.none { it.isDigit() })
            assertTrue(message, !message.contains("okhttp", ignoreCase = true))
            assertTrue(message, !message.contains("Exception", ignoreCase = true))
        }
    }
}
