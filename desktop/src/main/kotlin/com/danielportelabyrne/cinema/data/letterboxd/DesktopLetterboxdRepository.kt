package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.platform.Platform
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * The desktop's LetterboxdRepository, with the TV's calls: who is followed, and every page shown, kept on disk so a
 * page opens at once from the last copy and is then refreshed in the background. Answers are handed to [post], which
 * runs them on the UI thread (the TV's main Handler).
 */
class LetterboxdRepository(platform: Platform, private val post: (() -> Unit) -> Unit, allowBrowser: Boolean = true) {
    val web = LetterboxdWeb(allowBrowser)
    private val dir = File(platform.cacheDir, "letterboxd").apply { mkdirs() }
    private val prefs = platform.preferences(LetterboxdPages.PREFS)
    private val pool = Executors.newFixedThreadPool(2)
    private val pages = PageCache(dir, { path -> web.page(path)?.let { slim(it) } })
    private data class Parsed(val html: String, val value: Any)
    private val parsed = LinkedHashMap<String, Parsed>(32, 0.75f, true)

    var following: List<String>
        get() = prefs.getString("users", null)?.let { s -> runCatching { val a = JSONArray(s); (0 until a.length()).map { a.getString(it) } }.getOrNull() } ?: DEFAULT
        private set(value) { prefs.edit().putString("users", JSONArray(value).toString()).apply() }

    fun follow(user: String) { if (following.none { it.equals(user, true) }) following = following + user }
    fun unfollow(user: String) { following = following.filterNot { it.equals(user, true) } }

    /**
     * Calls [done] on the UI thread with what [parse] makes of [path]: first from the saved copy, if there is one, then
     * again from a fresh copy when the saved one is older than [maxAgeMs] (only if it changed). Null: no page at all.
     */
    fun <T> load(path: String, maxAgeMs: Long, parse: (String) -> T?, done: (T?) -> Unit) {
        pages.load(path, maxAgeMs, { html ->
            @Suppress("UNCHECKED_CAST")
            val hit = synchronized(parsed) { parsed[path]?.takeIf { it.html == html }?.value as? T }
            val value = hit ?: runCatching { parse(html) }.getOrNull()
            if (value != null) synchronized(parsed) {
                parsed[path] = Parsed(html, value as Any)
                while (parsed.size > 32) parsed.remove(parsed.keys.first())
            }
            post { done(value) }
        }, { post { done(null) } })
    }

    fun pageNow(path: String, maxAgeMs: Long): String? = pages.pageNow(path, maxAgeMs)

    fun missing(html: String?) = html != null && html.contains("<title>Letterboxd - Not Found</title>")

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

    @Volatile private var saveDue = false
    private fun savePostersSoon() {
        if (saveDue) return
        saveDue = true
        pool.execute {
            Thread.sleep(2000); saveDue = false
            runCatching { File(dir, "posters.tmp").also { it.writeText(JSONObject(posters as Map<*, *>).toString()) }.renameTo(posterFile) }
        }
    }

    fun close() { pages.close(); pool.shutdownNow(); web.close() }

    companion object {
        val DEFAULT get() = LetterboxdPages.DEFAULT
        const val MINUTE = LetterboxdPages.MINUTE
        const val HOUR = LetterboxdPages.HOUR
        fun followingCount(platform: Platform): Int = LetterboxdPages.followingCount(platform)
        fun forgetFollowing(platform: Platform) = LetterboxdPages.forgetFollowing(platform)
        fun slim(html: String): String = LetterboxdPages.slim(html)
    }
}
