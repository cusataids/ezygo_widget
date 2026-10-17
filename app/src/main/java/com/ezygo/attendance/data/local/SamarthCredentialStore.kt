package com.ezygo.attendance.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The Samarth portal sign-in, kept so the user is only ever asked for it once. */
data class SamarthCredentials(val username: String, val password: String)

/**
 * Storage for the Samarth portal credentials.
 *
 * Sealed with the same [KeystoreCrypto] key as the EzyGo session and held in the same
 * DataStore, which is already excluded from cloud and device-transfer backup. Kept
 * deliberately separate from [CredentialStore]: these are a different institution's
 * credentials, they are cleared by their own button, and nothing that clears one
 * should quietly clear the other.
 *
 * The portal has no token to exchange a password for — every scrape signs in again —
 * so unlike the EzyGo password this one is not an optimisation but the only way the
 * feature can work without asking on every tap. It is never logged, never rendered,
 * and leaves this class only to be posted to the portal's login form over HTTPS.
 */
class SamarthCredentialStore(private val context: Context) {

    private val usernameCipherKey = stringPreferencesKey("samarth_username_cipher")
    private val usernameIvKey = stringPreferencesKey("samarth_username_iv")
    private val passwordCipherKey = stringPreferencesKey("samarth_password_cipher")
    private val passwordIvKey = stringPreferencesKey("samarth_password_iv")

    private val credentialsFlow: Flow<SamarthCredentials?> = context.preferencesFlow.map { prefs ->
        val username = KeystoreCrypto.unseal(
            prefs[usernameCipherKey] ?: return@map null,
            prefs[usernameIvKey] ?: return@map null,
        ) ?: return@map null
        val password = KeystoreCrypto.unseal(
            prefs[passwordCipherKey] ?: return@map null,
            prefs[passwordIvKey] ?: return@map null,
        ) ?: return@map null
        SamarthCredentials(username, password)
    }

    /** Whether the user has ever signed in, without unsealing the password to find out. */
    val hasCredentials: Flow<Boolean> =
        context.preferencesFlow.map { it[usernameCipherKey] != null }

    suspend fun current(): SamarthCredentials? = credentialsFlow.first()

    suspend fun save(username: String, password: String) {
        val sealedUsername = KeystoreCrypto.seal(username.trim())
        val sealedPassword = KeystoreCrypto.seal(password)
        context.appDataStore.edit { prefs ->
            prefs[usernameCipherKey] = sealedUsername.payload
            prefs[usernameIvKey] = sealedUsername.iv
            prefs[passwordCipherKey] = sealedPassword.payload
            prefs[passwordIvKey] = sealedPassword.iv
        }
    }

    suspend fun clear() {
        context.appDataStore.edit { prefs ->
            prefs.remove(usernameCipherKey)
            prefs.remove(usernameIvKey)
            prefs.remove(passwordCipherKey)
            prefs.remove(passwordIvKey)
        }
    }
}
