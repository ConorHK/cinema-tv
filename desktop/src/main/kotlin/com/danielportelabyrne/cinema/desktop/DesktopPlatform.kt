package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.platform.Platform
import com.danielportelabyrne.cinema.platform.Preferences
import com.danielportelabyrne.cinema.platform.SecretStore
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * The shared code's view of a Linux desktop or a Steam Deck: files under the XDG folders ([AppDirs]), preferences as
 * small JSON files, and secrets sealed in [DesktopSecretStore]. [root] and [cache] can be pointed elsewhere by tests.
 */
class DesktopPlatform(val root: File = AppDirs.data, private val cache: File = AppDirs.cache) : Platform {
    override val filesDir: File get() = File(root, "files").apply { mkdirs() }
    override val cacheDir: File get() = cache.apply { mkdirs() }
    override val deviceName: String get() = DeviceName.value

    override fun preferences(name: String): Preferences = FilePreferences.open(prefsFile(name))
    override fun hasPreferences(name: String): Boolean = prefsFile(name).exists()
    override fun deletePreferences(name: String): Boolean {
        FilePreferences.forget(prefsFile(name))
        return prefsFile(name).delete()
    }

    override fun secretStore(): SecretStore = DesktopSecretStore(preferences("secrets"), File(root, "secrets.key"))

    private fun prefsFile(name: String) = File(root, "prefs/$name.json")
}

/**
 * What Jellyfin's dashboard shows for this device, "MANUFACTURER MODEL". A Steam Deck reports itself through DMI as
 * "Valve Jupiter" (LCD) or "Valve Galileo" (OLED).
 */
private object DeviceName {
    private fun dmi(name: String): String? = try {
        File("/sys/devices/virtual/dmi/id/$name").takeIf { it.canRead() }?.readText()?.trim()?.ifBlank { null }
    } catch (_: Exception) { null }

    val value: String by lazy {
        val product = dmi("product_name")
        val isDeck = product == "Jupiter" || product == "Galileo"
        val manufacturer = if (isDeck) "Valve" else (dmi("sys_vendor") ?: System.getProperty("os.name") ?: "Linux")
        val model = when {
            isDeck -> "Steam Deck"
            else -> try { InetAddress.getLocalHost().hostName } catch (_: Exception) { product ?: "Desktop" }
        }
        "$manufacturer $model".trim()
    }
}

/**
 * Preferences kept as one small JSON file each, readable by the owner only. Like Android's, reads come from memory,
 * and an edit is applied to memory at once and written to disk before [Preferences.Editor.apply] returns (the
 * files are a few hundred bytes, so there is no need for a background writer). One instance per file, so every
 * client sharing a name sees the same values.
 */
class FilePreferences private constructor(private val file: File) : Preferences {
    private val values = HashMap<String, Any>()

    init {
        try {
            if (file.exists()) JSONObject(file.readText()).let { o -> o.keys().forEach { k -> values[k] = o.get(k) } }
        } catch (_: Exception) { /* unreadable: start empty, as Android does with a corrupt file */ }
    }

    @Synchronized override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    @Synchronized override fun getBoolean(key: String, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    @Synchronized override fun getLong(key: String, defValue: Long): Long = (values[key] as? Number)?.toLong() ?: defValue
    @Synchronized override fun getInt(key: String, defValue: Int): Int = (values[key] as? Number)?.toInt() ?: defValue
    @Synchronized override fun contains(key: String): Boolean = key in values
    override val all: Map<String, *> @Synchronized get() = HashMap(values)

    override fun edit(): Preferences.Editor = object : Preferences.Editor {
        private val changes = LinkedHashMap<String, Any?>()   // null = remove
        private var clearFirst = false
        override fun putString(key: String, value: String?) = apply { changes[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { changes[key] = value }
        override fun putLong(key: String, value: Long) = apply { changes[key] = value }
        override fun putInt(key: String, value: Int) = apply { changes[key] = value }
        override fun remove(key: String) = apply { changes[key] = null }
        override fun clear() = apply { clearFirst = true }
        override fun apply() { commit() }
        override fun commit(): Boolean = synchronized(this@FilePreferences) {
            if (clearFirst) values.clear()
            changes.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
            save()
        }
    }

    private fun save(): Boolean = try {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSONObject(values as Map<*, *>).toString())
        AppDirs.ownerOnly(tmp)
        if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
        true
    } catch (_: Exception) { false }

    companion object {
        private val open = ConcurrentHashMap<String, FilePreferences>()
        fun open(file: File): FilePreferences = open.computeIfAbsent(file.absolutePath) { FilePreferences(file) }
        fun forget(file: File) { open.remove(file.absolutePath) }
    }
}
