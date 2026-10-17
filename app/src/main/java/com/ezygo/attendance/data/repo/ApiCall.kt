package com.ezygo.attendance.data.repo

import com.ezygo.attendance.data.remote.ApiError

/**
 * Every network call goes through here so nothing but an [ApiError] escapes the
 * data layer. Raw exceptions, status codes and response bodies stop at this line.
 */
internal suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (throwable: Throwable) {
        throw ApiError.from(throwable)
    }

/**
 * For sections that may legitimately be absent. Returns [fallback] on any API
 * failure, but never swallows cancellation.
 */
internal suspend fun <T> optionalApiCall(fallback: T, block: suspend () -> T): T =
    try {
        block()
    } catch (cancellation: kotlinx.coroutines.CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        fallback
    }
