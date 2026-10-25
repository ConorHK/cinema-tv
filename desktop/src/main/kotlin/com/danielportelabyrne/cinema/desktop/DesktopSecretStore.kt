package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.platform.Preferences
import com.danielportelabyrne.cinema.platform.SecretStore
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The desktop's [SecretStore], for passwords and API keys that have to outlive a restart.
 * A desktop has no hardware keystore the app can reach from inside a Flatpak, so each value is sealed with AES-GCM
 * under a random key kept in its own owner-only file next to the preferences. That keeps secrets out of the plain
 * preferences file and out of backups of it, but anyone who can read the user's home folder can read both, so it is
 * no stronger than the user account itself. Values are never logged; none of these calls throw.
 */
class DesktopSecretStore(private val prefs: Preferences, private val keyFile: File) : SecretStore {

    override fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return try {
            val bytes = Base64.getDecoder().decode(stored)
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "a saved secret could not be read: ${e.javaClass.simpleName}")
            null
        }
    }

    /** Saves [value], or forgets the name when it is null or blank. */
    override fun put(name: String, value: String?) {
        if (value.isNullOrEmpty()) { prefs.edit().remove(name).apply(); return }
        try {
            val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.ENCRYPT_MODE, key(), GCMParameterSpec(128, iv))
            val sealed = iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            prefs.edit().putString(name, Base64.getEncoder().encodeToString(sealed)).apply()
        } catch (e: Exception) {
            Log.w(TAG, "a secret could not be saved: ${e.javaClass.simpleName}")
        }
    }

    override fun clear() { prefs.edit().clear().apply() }

    @Synchronized
    private fun key(): SecretKey {
        if (keyFile.exists()) keyFile.readBytes().takeIf { it.size == 32 }?.let { return SecretKeySpec(it, "AES") }
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        keyFile.parentFile?.mkdirs()
        keyFile.writeBytes(bytes)
        AppDirs.ownerOnly(keyFile)
        return SecretKeySpec(bytes, "AES")
    }

    private companion object {
        const val TAG = "SecretStore"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        val random = SecureRandom()
    }
}
