package com.ezygo.attendance.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Credentials the user asked the app to remember. */
data class SavedCredentials(val username: String, val password: String)

/**
 * Optional storage for the sign-in credentials, used to re-authenticate silently
 * once the bearer token expires.
 *
 * Sealed with the same [KeystoreCrypto] key as the session token and held in the
 * same DataStore, which is already excluded from cloud and device-transfer backup.
 * The password is never logged, never rendered, and never leaves this class except
 * to be posted to the login endpoint over HTTPS.
 *
 * A stored, reusable password is inherently weaker than a stored token: the
 * keystore key is not bound to user authentication, so anyone who can use the
 * unlocked device can use the app as this user. That is why it is opt-in, off by
 * default, and cleared the moment the option is switched off or the user signs out.
 */
class CredentialStore(private val context: Context) {

    private val usernameCipherKey = stringPreferencesKey("credential_username_cipher")
    private val usernameIvKey = stringPreferencesKey("credential_username_iv")
    private val passwordCipherKey = stringPreferencesKey("credential_password_cipher")
    private val passwordIvKey = stringPreferencesKey("credential_password_iv")

    private val credentialsFlow = context.preferencesFlow.map { prefs ->
        val username = KeystoreCrypto.unseal(
            prefs[usernameCipherKey] ?: return@map null,
            prefs[usernameIvKey] ?: return@map null,
        ) ?: return@map null
        val password = KeystoreCrypto.unseal(
            prefs[passwordCipherKey] ?: return@map null,
            prefs[passwordIvKey] ?: return@map null,
        ) ?: return@map null
        SavedCredentials(username, password)
    }

    suspend fun current(): SavedCredentials? = credentialsFlow.first()

    suspend fun save(username: String, password: String) {
        val sealedUsername = KeystoreCrypto.seal(username)
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
