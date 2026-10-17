package com.ezygo.attendance.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** A stored secret: ciphertext plus the IV it was produced with. */
data class Sealed(val payload: String, val iv: String)

/**
 * AES/GCM encryption backed by a key that lives in the AndroidKeyStore and never
 * leaves it, so what lands on disk is useless by itself.
 *
 * Shared by [TokenStore] and [CredentialStore] rather than written twice — crypto
 * copied between classes is how the two copies end up subtly different.
 *
 * Note the key is deliberately not bound to user authentication: the session has to
 * be restorable in the background, including from a widget update, with no user
 * present to authenticate. That is the trade-off this app accepts.
 */
object KeystoreCrypto {

    fun seal(plaintext: String): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Sealed(encrypted.encode(), cipher.iv.encode())
    }

    /**
     * Returns null when the value cannot be recovered — a rotated or invalidated
     * keystore key, or a corrupt entry. Callers treat that as "no stored value"
     * rather than as an error, which degrades to asking the user to sign in again.
     */
    fun unseal(payload: String, iv: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv.decode()))
        String(cipher.doFinal(payload.decode()), Charsets.UTF_8)
    }.getOrNull()

    /** Fetches the existing keystore key, generating it on first use. */
    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private fun ByteArray.encode(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String.decode(): ByteArray = Base64.decode(this, Base64.NO_WRAP)

    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    // Kept as the original alias so existing sessions survive the refactor.
    private const val KEY_ALIAS = "ezygo_session_token"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
}
