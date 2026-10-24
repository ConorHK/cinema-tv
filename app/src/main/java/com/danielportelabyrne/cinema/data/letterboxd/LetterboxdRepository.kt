package com.danielportelabyrne.cinema.data.letterboxd

import android.app.Activity
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Letterboxd for the Cinema page: who is followed, and every page it shows, kept on disk so a page opens at once from
 * the last copy and is then refreshed in the background (a page behind Letterboxd's browser check takes several
 * seconds to fetch). Pages are stored without their scripts and drawings, which are most of their size.
 */
/** [allowBrowser] false (the player) never starts the hidden browser: plain requests and the saved copies only. */
class LetterboxdRepository(activity: Activity, allowBrowser: Boolean = true) {
    val web = LetterboxdWeb(activity, allowBrowser)
    private val dir = File(activity.cacheDir, "letterboxd").apply { mkdirs() }
    private val prefs = activity.getSharedPreferences(PREFS, Activity.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val pool = Executors.newFixedThreadPool(2)
    private val pages = PageCache(dir, { path -> web.page(path)?.let { slim(it) } })
    private data class Parsed(val html: String, val value: Any)
    private data class Snapshot(val digest: String, val value: Any) : java.io.Serializable
    private val parsed = LinkedHashMap<String, Parsed>(32, 0.75f, true)

    // --- Who is followed ----------------------------------------------------------------------------

    /** Usernames followed here, in the order they were added. Empty until the owner follows someone. */
    var following: List<String>
        get() = prefs.getString("users", null)?.let { s -> (0 until JSONArray(s).length()).map { JSONArray(s).getString(it) } } ?: DEFAULT
        private set(value) { prefs.edit().putString("users", JSONArray(value).toString()).apply() }

    fun follow(user: String) { if (following.none { it.equals(user, true) }) following = following + user }
    fun unfollow(user: String) { following = following.filterNot { it.equals(user, true) } }

    // --- Pages ----------------------------------------------------------------------------------------

    /**
     * Calls [done] on the main thread with what [parse] makes of [path]: first from the saved copy, if there is one,
     * then again from a fresh copy when the saved one is older than [maxAgeMs] (only if it changed). Null means the
     * page could not be had at all.
     */
    fun <T> load(path: String, maxAgeMs: Long, parse: (String) -> T?, done: (T?) -> Unit) {
        val started = android.os.SystemClock.elapsedRealtime()
        // Each public URL has one model shape. Lambda class names contain runtime identities and cannot be disk keys.
        val key = path
        pages.load(path, maxAgeMs, { html ->
            val snapshot = File(dir, com.danielportelabyrne.cinema.ui.ImageCache.sha1("parsed-v2:" + key) + ".bin")
            val digest by lazy { java.security.MessageDigest.getInstance("SHA-256").digest(html.toByteArray())
                .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') } }
            @Suppress("UNCHECKED_CAST")
            val hit = synchronized(parsed) { parsed[key]?.takeIf { it.html == html }?.value as? T } ?: runCatching {
                java.io.ObjectInputStream(snapshot.inputStream().buffered()).use { it.readObject() as Snapshot }
                    .takeIf { it.digest == digest }?.value as? T
            }.getOrNull()
            val parseStart = android.os.SystemClock.elapsedRealtime()
            val value = hit ?: runCatching { parse(html) }.getOrNull()
            com.danielportelabyrne.cinema.ui.Perf.took("letterboxd parse $path", android.os.SystemClock.elapsedRealtime() - parseStart)
            if (value != null) synchronized(parsed) {
                parsed[key] = Parsed(html, value as Any)
                while (parsed.size > 32) parsed.remove(parsed.keys.first())
            }
            main.post {
                com.danielportelabyrne.cinema.ui.Perf.took("letterboxd data $path", android.os.SystemClock.elapsedRealtime() - started)
                done(value)
            }
            // Retain parsed public data across process restarts. A digest and schema version invalidate old snapshots.
            // Only app-private, self-written files are read; corrupt or incompatible copies fall back to HTML parsing.
            if (hit == null && value is java.io.Serializable) runCatching {
                val tmp = File.createTempFile(snapshot.name, ".tmp", dir)
                try {
                    java.io.ObjectOutputStream(tmp.outputStream().buffered()).use { it.writeObject(Snapshot(digest, value)) }
                    if (!tmp.renameTo(snapshot)) tmp.delete()
                } finally { tmp.delete() }
            }
        }, { main.post { done(null) } })
    }

    /** Blocking: the page now, saved copy first when it is recent enough. Null when it cannot be had. */
    fun pageNow(path: String, maxAgeMs: Long): String? {
        return pages.pageNow(path, maxAgeMs)
    }

    /** True when [path] is known not to exist (a member's film page they never logged, a username with no account). */
    fun missing(html: String?) = html != null && html.contains("<title>Letterboxd - Not Found</title>")

    // --- Posters --------------------------------------------------------------------------------------

    private val posters = ConcurrentHashMap<String, String>()
    private val posterFile = File(dir, "posters.json")
    @Volatile private var postersLoaded = false

    /** Blocking: a poster address for [slug] (460 x 690), from Letterboxd's small poster lookup. Kept for good. */
    fun posterUrl(slug: String): String? {
        if (!postersLoaded) loadPosters()
        posters[slug]?.let { return it }
        val json = web.json("/film/$slug/poster/std/230/") ?: return null
        val url = runCatching { JSONObject(json).optString("url2x").ifBlank { null } }.getOrNull() ?: return null
        posters[slug] = url
        savePostersSoon()
        return url
    }

    @Synchronized private fun loadPosters() {
        if (postersLoaded) return
        runCatching { val o = JSONObject(posterFile.readText()); o.keys().forEach { posters[it] = o.getString(it) } }
        postersLoaded = true
    }

    private val saveTask = Runnable { pool.execute { runCatching { File(dir, "posters.tmp").also { it.writeText(JSONObject(posters as Map<*, *>).toString()) }.renameTo(posterFile) } } }
    private fun savePostersSoon() { main.removeCallbacks(saveTask); main.postDelayed(saveTask, 2000) }

    fun close() { main.removeCallbacks(saveTask); pages.close(); pool.shutdownNow(); web.close() }

    companion object {
        /** Nobody to begin with: the owner follows whoever they like from the Letterboxd page. */
        val DEFAULT = emptyList<String>()
        private const val PREFS = "letterboxd_follow"

        /** How many people are followed, without opening the page cache (for Settings). */
        fun followingCount(context: android.content.Context): Int = try {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString("users", null)?.let { JSONArray(it).length() } ?: DEFAULT.size
        } catch (_: Exception) { 0 }

        /** Unfollows everyone (Settings, Reset everything). */
        fun forgetFollowing(context: android.content.Context) {
            context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().remove("users").apply()
        }
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE

        /** Drops scripts, styles and inline drawings: a review page goes from 1.4 MB to a fraction of that. */
        fun slim(html: String): String = html
            // Every script but the film's data block (its rating, and how many have rated and reviewed it).
            .replace(Regex("<script\\b(?![^>]*application/ld\\+json).*?</script>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style\\b.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<svg\\b(?:(?!aria-label=\"(?:[★½]+|Liked)\").)*?</svg>", RegexOption.DOT_MATCHES_ALL), "")
    }
}
