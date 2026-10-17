package com.ezygo.attendance.data.remote

import java.io.IOException

/**
 * User-facing failure categories. Raw response bodies and status codes never
 * reach the UI — only these cases and the short message attached to each.
 */
sealed class ApiError(val userMessage: String, cause: Throwable? = null) :
    Exception(userMessage, cause) {

    /** The username lookup returned no matching account. */
    data object UserNotFound :
        ApiError("We couldn't find an account with that username.")

    data object BadCredentials :
        ApiError("That username and password combination didn't work.")

    /** Token rejected or expired; the session must be re-established. */
    data object Unauthorized :
        ApiError("Your session has expired. Please sign in again.")

    class Network(cause: Throwable? = null) :
        ApiError("Can't reach the server. Check your connection and try again.", cause)

    class Server(cause: Throwable? = null) :
        ApiError("The server is having trouble right now. Please try again shortly.", cause)

    class Unexpected(cause: Throwable? = null) :
        ApiError("Something went wrong. Please try again.", cause)

    /** No class group matches the selected period, so there is no timetable. */
    data object NoTimetable :
        ApiError("No class group is set up for this academic period.")

    companion object {
        /**
         * Laravel-style validation rejection. Verified against the live API: an
         * unknown username on /login/lookup and a wrong password on /login both come
         * back as 422, not as the 200-with-empty-users / 401 the written contract
         * suggested. Callers decide what a 422 means for their specific endpoint.
         */
        const val VALIDATION_FAILED = 422

        /** Maps any throwable from the network layer onto a safe user-facing case. */
        fun from(throwable: Throwable): ApiError = when (throwable) {
            is ApiError -> throwable
            is IOException -> Network(throwable)
            is retrofit2.HttpException -> when (throwable.code()) {
                401, 403 -> Unauthorized
                in 500..599 -> Server(throwable)
                else -> Unexpected(throwable)
            }
            else -> Unexpected(throwable)
        }

        /** A 422 from the lookup endpoint means the account does not exist. */
        fun fromLookup(throwable: Throwable): ApiError =
            if (statusCode(throwable) == VALIDATION_FAILED) UserNotFound else from(throwable)

        /**
         * A 422 or 401 from the login endpoint is a credential problem. The account
         * is already known to exist at this point, so it is not a stale session.
         */
        fun fromLogin(throwable: Throwable): ApiError =
            when (statusCode(throwable)) {
                VALIDATION_FAILED, 401, 403 -> BadCredentials
                else -> from(throwable)
            }

        private fun statusCode(throwable: Throwable): Int? =
            (throwable as? retrofit2.HttpException)?.code()
    }
}
