package com.danielportelabyrne.cinema.data.settings

import android.content.Context
import android.util.Log
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.movies.ClientKind
import com.danielportelabyrne.cinema.data.movies.SeerrClient
import org.json.JSONObject

/** A username and password from the preset. */
data class PresetLogin(val username: String, val password: String)

data class PresetServer(val url: String, val login: PresetLogin?)

/**
 * Setup baked into the APK at build time (see README, "Building with a preset"), so a TV needs no typing. Values are
 * written once per distinct preset, so changes made in Settings survive restarts; Reset everything brings them back.
 * Sign-ins are retried on every start until they work.
 */
data class Preset(
    val jellyfin: PresetServer?,
    val seerr: PresetServer?,
    val seerrLocal: Boolean,
    val sonarr: ManualArr?,
    val radarr: ManualArr?,
    val clients: Map<ClientKind, PresetLogin>,
    val region: String?,
    val requestHelperUrl: String?,
    val streamServiceUrl: String?,
    val fingerprint: String,
) {
    /** Writes the addresses and keys, once per preset. Cheap; safe on the main thread. */
    fun applySettings(context: Context, settings: CinemaSettings) {
        val prefs = context.applicationContext.getSharedPreferences("cinema_settings", Context.MODE_PRIVATE)
        if (prefs.getString(APPLIED_KEY, null) == fingerprint) return
        jellyfin?.let { JellyfinClient(context).setServer(it.url) }
        seerr?.let { SeerrClient(context).setServer(it.url) }
        sonarr?.let { settings.setManualArr(Arr.SONARR, it) }
        radarr?.let { settings.setManualArr(Arr.RADARR, it) }
        region?.let { settings.region = it }
        requestHelperUrl?.let { settings.requestHelperUrl = it }
        streamServiceUrl?.let { settings.streamServiceUrl = it }
        if (jellyfin != null) settings.setupDone = true
        prefs.edit().putString(APPLIED_KEY, fingerprint).apply()
    }

    /** Signs in to Jellyfin and Seerr where not signed in yet. Blocking. */
    fun signIn(jellyfinClient: JellyfinClient, seerrClient: SeerrClient) {
        val jf = jellyfin?.login
        if (jf != null && jellyfinClient.hasServer && !jellyfinClient.isConnected) {
            jellyfinClient.signInByName(jf.username, jf.password)?.let { Log.w(TAG, "Jellyfin preset sign-in failed") }
        }
        val sr = seerr?.login ?: jf.takeUnless { seerrLocal }
        if (sr != null && seerrClient.hasServer && !seerrClient.isSignedIn) {
            val error = if (seerrLocal) seerrClient.signInLocal(sr.username, sr.password) else seerrClient.signIn(sr.username, sr.password)
            error?.let { Log.w(TAG, "Seerr preset sign-in failed") }
        }
    }

    val needsSignIn: Boolean get() = jellyfin?.login != null || seerr != null

    companion object {
        private const val TAG = "Preset"
        private const val ASSET = "cinema-preset.json"
        private const val APPLIED_KEY = "preset_applied"

        @Volatile private var cached: Preset? = null
        @Volatile private var loaded = false

        /** The preset in this APK, or null when it was built without one. */
        fun load(context: Context): Preset? {
            if (loaded) return cached
            cached = try {
                context.assets.open(ASSET).bufferedReader().use { parse(it.readText()) }
            } catch (_: java.io.FileNotFoundException) {
                null
            } catch (e: Exception) {
                Log.w(TAG, "preset unreadable: ${e.javaClass.simpleName}"); null
            }
            loaded = true
            return cached
        }

        fun parse(json: String): Preset {
            val o = JSONObject(json)
            fun JSONObject.str(name: String): String? = optString(name, "").trim().takeIf { has(name) && !isNull(name) && it.isNotEmpty() }
            fun login(s: JSONObject): PresetLogin? {
                val user = s.str("username") ?: s.str("email") ?: return null
                return PresetLogin(user, s.str("password").orEmpty())
            }
            fun server(name: String): PresetServer? {
                val s = o.optJSONObject(name) ?: return null
                val url = CinemaSettings.cleanUrl(s.str("url")) ?: return null
                return PresetServer(url, login(s))
            }
            fun arr(name: String): ManualArr? {
                val s = o.optJSONObject(name) ?: return null
                val url = CinemaSettings.cleanUrl(s.str("url")) ?: return null
                return ManualArr(url, s.str("apiKey") ?: return null)
            }
            val clients = ClientKind.entries.mapNotNull { kind ->
                val s = o.optJSONObject(kind.label.lowercase()) ?: return@mapNotNull null
                kind to PresetLogin(s.str("username").orEmpty(), s.str("password") ?: return@mapNotNull null)
            }.toMap()
            return Preset(
                jellyfin = server("jellyfin"),
                seerr = server("seerr"),
                seerrLocal = o.optJSONObject("seerr")?.str("signIn").equals("local", ignoreCase = true),
                sonarr = arr("sonarr"),
                radarr = arr("radarr"),
                clients = clients,
                region = o.str("region")?.takeIf { it.length == 2 },
                requestHelperUrl = o.str("requestHelperUrl"),
                streamServiceUrl = o.str("streamServiceUrl"),
                fingerprint = Integer.toHexString(json.hashCode()),
            )
        }
    }
}
