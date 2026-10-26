package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.music.SubsonicConfig
import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Preferences
import com.danielportelabyrne.cinema.platform.SecretStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class MusicSettingsTest {
    private class MemoryPrefs : Preferences {
        val values = HashMap<String, Any?>()
        override fun getString(key: String, defValue: String?) = values[key] as String? ?: defValue
        override fun getBoolean(key: String, defValue: Boolean) = values[key] as Boolean? ?: defValue
        override fun getLong(key: String, defValue: Long) = values[key] as Long? ?: defValue
        override fun getInt(key: String, defValue: Int) = values[key] as Int? ?: defValue
        override fun contains(key: String) = key in values
        override val all: Map<String, *> get() = values
        override fun edit(): Preferences.Editor = object : Preferences.Editor {
            override fun putString(key: String, value: String?) = apply { values[key] = value }
            override fun putBoolean(key: String, value: Boolean) = apply { values[key] = value }
            override fun putLong(key: String, value: Long) = apply { values[key] = value }
            override fun putInt(key: String, value: Int) = apply { values[key] = value }
            override fun remove(key: String) = apply { values.remove(key) }
            override fun clear() = apply { values.clear() }
            override fun apply() {}
            override fun commit() = true
        }
    }

    private class MemorySecrets : SecretStore {
        val values = HashMap<String, String>()
        override fun get(name: String) = values[name]
        override fun put(name: String, value: String?) { if (value.isNullOrBlank()) values.remove(name) else values[name] = value }
        override fun clear() = values.clear()
    }

    private val prefs = MemoryPrefs()
    private val secrets = MemorySecrets()
    private val platform = object : Platform {
        override val filesDir = File("unused")
        override val cacheDir = File("unused")
        override val deviceName = "test"
        override fun preferences(name: String) = prefs
        override fun hasPreferences(name: String) = true
        override fun deletePreferences(name: String) = true
        override fun readAsset(name: String): String? = null
        override fun secretStore() = secrets
    }

    @Test fun `saves the server and keeps the password secret`() {
        val settings = CinemaSettings(platform)
        settings.subsonic = SubsonicConfig("music.example:4533/", "listener", "pw-example")
        assertEquals(SubsonicConfig("http://music.example:4533", "listener", "pw-example"), settings.subsonic)
        assertFalse(prefs.values.values.contains("pw-example"))
    }

    @Test fun `turning music off forgets the secret`() {
        val settings = CinemaSettings(platform)
        settings.subsonic = SubsonicConfig("http://music.example", "", apiKey = "key-example")
        settings.subsonic = null
        assertNull(settings.subsonic)
        assertNull(secrets.get("subsonic_key"))
    }
}
