package com.danielportelabyrne.cinema.platform

import java.io.File

/** A [Platform] held in memory, for tests of code that keeps settings and secrets. */
class MemoryPlatform : Platform {
    val prefs = HashMap<String, MemoryPreferences>()
    val secrets = MemorySecrets()

    override val filesDir = File("unused")
    override val cacheDir = File("unused")
    override val deviceName = "test"
    override fun preferences(name: String) = prefs.getOrPut(name) { MemoryPreferences() }
    override fun hasPreferences(name: String) = name in prefs
    override fun deletePreferences(name: String) = prefs.remove(name) != null
    override fun readAsset(name: String): String? = null
    override fun secretStore() = secrets
}

class MemoryPreferences : Preferences {
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

class MemorySecrets : SecretStore {
    val values = HashMap<String, String>()
    override fun get(name: String) = values[name]
    override fun put(name: String, value: String?) { if (value.isNullOrBlank()) values.remove(name) else values[name] = value }
    override fun clear() = values.clear()
}
