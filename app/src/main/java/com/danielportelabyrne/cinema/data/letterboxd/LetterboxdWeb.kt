package com.danielportelabyrne.cinema.data.letterboxd

import com.danielportelabyrne.cinema.data.exchange
import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val TAG = "Letterboxd"

/**
 * Reads public Letterboxd pages. Letterboxd has no open API, and some of its pages (profiles, likes, diaries, reviews)
 * sit behind a browser check that a plain request never passes. So: a plain request first, with the same identity and
 * cookies as the app's own WebView; when that meets the check, the page is loaded in a hidden WebView, which passes it
 * like any browser, and its HTML is read back. The check's cookie then lets plain requests through for a while.
 *
 * Blocking: call off the main thread. Null when the page cannot be had.
 */
class LetterboxdWeb(private val activity: Activity, private val allowBrowser: Boolean = true) {
    private val main = Handler(Looper.getMainLooper())
    private val userAgent: String by lazy { WebSettings.getDefaultUserAgent(activity) }
    private val browserLock = Any()
    @Volatile private var webView: WebView? = null

    /** [path] is a site path ("/someone/likes/films/") or a full Letterboxd address. */
    fun page(path: String): String? {
        val url = if (path.startsWith("http")) path else BASE + path
        plain(url)?.let { return it }
        if (!allowBrowser) return null
        return synchronized(browserLock) { plain(url) ?: viaBrowser(url) }
    }

    /** A small JSON answer (poster addresses and the like), which the check does not guard. */
    fun json(path: String): String? = plain(if (path.startsWith("http")) path else BASE + path, acceptJson = true)

    private fun plain(url: String, acceptJson: Boolean = false): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 15_000; c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", userAgent)
        c.setRequestProperty("Accept", if (acceptJson) "application/json" else "text/html")
        c.setRequestProperty("Accept-Language", "en-GB,en;q=0.9")
        CookieManager.getInstance().getCookie(BASE)?.let { c.setRequestProperty("Cookie", it) }
        c.exchange {
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().use { it.readText() }.takeUnless { isChallenge(it) }
                else -> null
            }
        }
    } catch (e: Exception) { Log.w(TAG, "plain ${url.substringAfter(BASE)}: ${e.javaClass.simpleName}"); null }

    @SuppressLint("SetJavaScriptEnabled")
    private fun viaBrowser(url: String): String? {
        val done = CountDownLatch(1)
        var html: String? = null
        val started = System.currentTimeMillis()
        main.post {
            if (activity.isDestroyed) { done.countDown(); return@post }
            val view = webView ?: WebView(activity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.loadsImagesAutomatically = false
                settings.blockNetworkImage = true
                settings.userAgentString = userAgent
                CookieManager.getInstance().setAcceptCookie(true)
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                isFocusable = false; isFocusableInTouchMode = false; alpha = 0.01f
                // A WebView only runs its scripts properly while attached; a 1-pixel one in a corner is never seen.
                (activity.window.decorView as ViewGroup).addView(this, ViewGroup.LayoutParams(1, 1))
            }.also { webView = it }
            fun poll(attempt: Int) {
                // Ready once the browser check has gone: no "Just a moment" title, none of its widgets, and something on the
                // page (a whole page, or a small fragment such as the ratings chart).
                view.evaluateJavascript("(function(){var t=document.title||'';var c=document.querySelector('#challenge-running,#challenge-form,.cf-turnstile,#cf-wrapper,#challenge-stage');" +
                    "return (t.indexOf('Just a moment')<0 && !c && document.body && document.body.innerHTML.length>40)?'1':'0'})()") { raw ->
                    val ready = raw?.trim('"') == "1"
                    if (ready) {
                        view.evaluateJavascript("document.documentElement.outerHTML") { out ->
                            html = decodeJsString(out); done.countDown()
                        }
                    } else if (attempt < 50) main.postDelayed({ poll(attempt + 1) }, 400)
                    else done.countDown()
                }
            }
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(v: WebView, u: String) { poll(0) }
            }
            view.loadUrl(url)
        }
        done.await(25, TimeUnit.SECONDS)
        main.post { CookieManager.getInstance().flush(); webView?.stopLoading() }
        Log.i(TAG, "browser ${url.substringAfter(BASE)}: ${if (html != null) "${html!!.length} chars" else "failed"} in ${System.currentTimeMillis() - started} ms")
        return html?.takeUnless { isChallenge(it) }
    }

    fun close() { main.post { webView?.let { (it.parent as? ViewGroup)?.removeView(it); it.destroy() }; webView = null } }

    companion object {
        const val BASE = "https://letterboxd.com"

        fun isChallenge(html: String) = html.length < 20_000 && (html.contains("Just a moment") || html.contains("challenges.cloudflare.com"))

        /** evaluateJavascript hands back a JSON string literal; this turns it into the text it holds. */
        fun decodeJsString(raw: String?): String? {
            if (raw == null || raw == "null" || raw.length < 2) return null
            return try { org.json.JSONArray("[$raw]").getString(0) } catch (_: Exception) { null }
        }
    }
}
