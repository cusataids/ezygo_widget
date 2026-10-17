package com.ezygo.attendance.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LookupRequest(val username: String)

/**
 * The lookup response lists the usernames the portal recognises. An empty list
 * means the account does not exist and login must not be attempted.
 */
@Serializable
data class LookupResponse(
    val users: List<String> = emptyList(),
    val parentlogin: Boolean = false,
    val auditorlogin: Boolean = false,
)

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
    @SerialName("stay_logged_in") val stayLoggedIn: Boolean = true,
)

@Serializable
data class LoginResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String? = null,
)
