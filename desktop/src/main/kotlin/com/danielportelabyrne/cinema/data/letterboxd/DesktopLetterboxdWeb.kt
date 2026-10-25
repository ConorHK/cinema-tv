package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.data.exchange
import com.danielportelabyrne.cinema.platform.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.concurrent.TimeUnit

private const val TAG = "Letterboxd"

/** Plain HTTP first; pages needing JavaScript run in an isolated browser process, off the UI thread. */
class LetterboxdWeb(private val allowBrowser: Boolean = true) {
    private val browserLock = Any()
    @Volatile private var closed = false
    @Volatile private var browser: Process? = null

    fun page(path: String): String? {
        if (closed) return null
        val canonical = canonicalPath(path)
        val url = if (canonical.startsWith("http")) canonical else BASE + canonical
        plain(url)?.let { return it }
        publicFallback(canonical)?.let { plain(BASE + it)?.let { html -> return html } }
        if (!allowBrowser) return null
        return synchronized(browserLock) { if (closed) null else viaBrowser(url) }
    }

    fun json(path: String): String? = if (closed) null else plain(if (path.startsWith("http")) path else BASE + path, acceptJson = true)

    private fun plain(url: String, acceptJson: Boolean = false): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", USER_AGENT)
        c.setRequestProperty("Accept", if (acceptJson) "application/json" else "text/html")
        c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
        c.exchange {
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }.takeUnless { isChallenge(it) }
                else -> null
            }
        }
    } catch (e: Exception) { Log.w(TAG, "Page request failed: ${e.javaClass.simpleName}"); null }

    private fun viaBrowser(url: String): String? {
        // WebKit is native code: a fault in it must not take down the player and the rest of Cinema.
        val result = Files.createTempFile("cinema-letterboxd-", ".html").toFile()
        var process: Process? = null
        return try {
            process = ProcessBuilder(browserCommand(url, result))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD).start()
            browser = process
            if (closed) return null
            if (!process.waitFor(35, TimeUnit.SECONDS)) {
                Log.w(TAG, "Browser timed out")
                return null
            }
            if (process.exitValue() != 0 || result.length() !in 1..5_000_000) {
                Log.w(TAG, "Browser could not load the page")
                return null
            }
            result.readText().takeUnless { isChallenge(it) }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt(); null
        } catch (e: Exception) {
            Log.w(TAG, "Browser unavailable: ${e.javaClass.simpleName}"); null
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
            browser = null
            result.delete()
        }
    }

    fun close() { closed = true; browser?.destroyForcibly() }

    companion object {
        const val BASE = "https://letterboxd.com"
        const val USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"

        internal fun canonicalPath(path: String): String = path
            .replace(Regex("^(/[A-Za-z0-9_]+/)films/(diary|reviews)/"), "$1$2/")

        /** The public film-page header identifies a member too; RSS supplies diary entries without JavaScript. */
        internal fun publicFallback(path: String): String? {
            Regex("^/([A-Za-z0-9_]+)/$").matchEntire(path)?.let { return "/${it.groupValues[1]}/films/" }
            Regex("^/([A-Za-z0-9_]+)/diary/$").matchEntire(path)?.let { return "/${it.groupValues[1]}/rss/" }
            return null
        }

        fun isChallenge(html: String) = html.contains("<title>Just a moment") ||
            html.contains("id=\"challenge-running\"") || html.contains("id=\"challenge-form\"") ||
            html.contains("id=\"cf-chl-widget") ||
            (html.length < 20_000 && html.contains("challenges.cloudflare.com"))

        internal fun browserCommand(url: String, result: File): List<String> = listOf(
            File(System.getProperty("java.home"), "bin/" + if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java").path,
            "-Xmx192m", "-Dprism.order=sw", "-XX:-CreateCoredumpOnCrash",
            "-Djava.io.tmpdir=${System.getProperty("java.io.tmpdir")}",
            "-XX:ErrorFile=${System.getProperty("java.io.tmpdir")}/cinema-browser-error.log",
            "-cp", System.getProperty("java.class.path"),
            "com.danielportelabyrne.cinema.data.letterboxd.DesktopLetterboxdBrowserKt", url, result.path,
        )
    }
}
