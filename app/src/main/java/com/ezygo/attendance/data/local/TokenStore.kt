package com.ezygo.attendance.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Holds the bearer token for the authenticated session.
 *
 * The token is sealed with [KeystoreCrypto], so the value on disk is useless on its
 * own. The plaintext token is only ever handed to the Authorization header — it is
 * never logged, never put in a UI string, and never included in a backup
 * (see data_extraction_rules.xml).
 */
class TokenStore(private val context: Context) {

    private val cipherKey = stringPreferencesKey("session_token_cipher")
    private val ivKey = stringPreferencesKey("session_token_iv")

    /** Emits the decrypted token, or null when there is no usable session. */
    val tokenFlow: Flow<String?> = context.preferencesFlow.map { prefs ->
        val payload = prefs[cipherKey] ?: return@map null
        val iv = prefs[ivKey] ?: return@map null
        KeystoreCrypto.unseal(payload, iv)
    }

    suspend fun currentToken(): String? = tokenFlow.first()

    suspend fun save(token: String) {
        val sealed = KeystoreCrypto.seal(token)
        context.appDataStore.edit { prefs ->
            prefs[cipherKey] = sealed.payload
            prefs[ivKey] = sealed.iv
        }
    }

    suspend fun clear() {
        context.appDataStore.edit { prefs ->
            prefs.remove(cipherKey)
            prefs.remove(ivKey)
        }
    }
}
