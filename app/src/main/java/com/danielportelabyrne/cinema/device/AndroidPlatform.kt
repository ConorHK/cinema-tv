package com.danielportelabyrne.cinema.device

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Preferences
import com.danielportelabyrne.cinema.platform.SecretStore
import java.io.File

/** The shared code's view of this TV: private files, SharedPreferences, and the Keystore-backed secrets. */
class AndroidPlatform(context: Context) : Platform {
    private val app = context.applicationContext

    override val filesDir: File get() = app.filesDir
    override val cacheDir: File get() = app.cacheDir
    override val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    override fun preferences(name: String): Preferences = AndroidPreferences(app.getSharedPreferences(name, Context.MODE_PRIVATE))
    override fun hasPreferences(name: String): Boolean = File(app.applicationInfo.dataDir, "shared_prefs/$name.xml").exists()
    override fun deletePreferences(name: String): Boolean = app.deleteSharedPreferences(name)
    override fun readAsset(name: String): String? = try {
        app.assets.open(name).bufferedReader().use { it.readText() }
    } catch (_: java.io.FileNotFoundException) { null }

    override fun secretStore(): SecretStore = AndroidSecretStore(preferences("secrets"))
}

private class AndroidPreferences(private val prefs: SharedPreferences) : Preferences {
    override fun getString(key: String, defValue: String?): String? = prefs.getString(key, defValue)
    override fun getBoolean(key: String, defValue: Boolean): Boolean = prefs.getBoolean(key, defValue)
    override fun getLong(key: String, defValue: Long): Long = prefs.getLong(key, defValue)
    override fun getInt(key: String, defValue: Int): Int = prefs.getInt(key, defValue)
    override fun contains(key: String): Boolean = prefs.contains(key)
    override val all: Map<String, *> get() = prefs.all
    override fun edit(): Preferences.Editor = Editor(prefs.edit())

    private class Editor(private val e: SharedPreferences.Editor) : Preferences.Editor {
        override fun putString(key: String, value: String?) = apply { e.putString(key, value) }
        override fun putBoolean(key: String, value: Boolean) = apply { e.putBoolean(key, value) }
        override fun putLong(key: String, value: Long) = apply { e.putLong(key, value) }
        override fun putInt(key: String, value: Int) = apply { e.putInt(key, value) }
        override fun remove(key: String) = apply { e.remove(key) }
        override fun clear() = apply { e.clear() }
        override fun apply() = e.apply()
        override fun commit(): Boolean = e.commit()
    }
}
