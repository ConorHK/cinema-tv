package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.settings.Arr
import com.danielportelabyrne.cinema.data.settings.ManualArr
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Sonarr or Radarr typed in by hand (address and API key), for owners whose Seerr will not share its settings with a
 * non-admin account. The quality profile and root folder Seerr would have supplied are read from the server itself:
 * its first profile and first root folder. Every call blocks and none throw.
 */
object ArrSetup {
    private val resolved = ConcurrentHashMap<String, SonarrConfig>()

    /** A usable config for [manual], or null when the server cannot be reached with that key. Cached per address and key. */
    fun resolve(manual: ManualArr): SonarrConfig? {
        val cacheKey = manual.baseUrl + "|" + manual.apiKey
        resolved[cacheKey]?.let { return it }
        val bare = SonarrConfig(manual.baseUrl, manual.apiKey, 0, "", true, "standard")
        val profile = arrCall(bare, "GET", "/api/v3/qualityprofile")?.let { firstProfileId(it) } ?: return null
        val folder = arrCall(bare, "GET", "/api/v3/rootfolder")?.let { firstRootFolder(it) }.orEmpty()
        return bare.copy(profileId = profile, rootFolder = folder).also { resolved[cacheKey] = it }
    }

    /**
     * Checks an address and key before they are saved. Null when it is the expected app and the key works, otherwise a
     * short reason to show.
     */
    fun check(arr: Arr, manual: ManualArr): String? {
        val bare = SonarrConfig(manual.baseUrl, manual.apiKey, 0, "", true, "standard")
        val status = arrCall(bare, "GET", "/api/v3/system/status") ?: return "Couldn't reach ${arr.label} with that address and API key"
        val app = appName(status)
        return if (app != null && !app.equals(arr.label, ignoreCase = true)) "That address is $app, not ${arr.label}" else null
    }

    fun forget() = resolved.clear()

    fun firstProfileId(json: String): Int? = try {
        JSONArray(json).optJSONObject(0)?.optInt("id")?.takeIf { it > 0 }
    } catch (_: Exception) { null }

    fun firstRootFolder(json: String): String? = try {
        JSONArray(json).optJSONObject(0)?.optString("path")?.ifBlank { null }
    } catch (_: Exception) { null }

    fun appName(json: String): String? = try { JSONObject(json).optString("appName").ifBlank { null } } catch (_: Exception) { null }
}
