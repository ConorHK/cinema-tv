package com.danielportelabyrne.cinema.data.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Passwords and API keys that have to outlive a restart (a download client's password, a Sonarr key typed in by hand).
 * Each value is encrypted with an AES key that lives in the Android Keystore and never leaves it, so the private
 * preferences file on its own is useless. Values are never logged. Every call is safe on any thread and none throw:
 * a value that can no longer be read (the key was wiped) simply reads as missing and has to be entered again.
 */
class SecretStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("secrets", Context.MODE_PRIVATE)

    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "a saved secret could not be read: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Saves [value], or forgets the name when it is null or blank. */
    fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) { prefs.edit().remove(name).apply(); return }
        try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "a secret could not be saved: ${e.javaClass.simpleName}")
        }
    }

    fun clear() { prefs.edit().clear().apply() }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecretStore"
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "cinema_secrets"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
    }
}
