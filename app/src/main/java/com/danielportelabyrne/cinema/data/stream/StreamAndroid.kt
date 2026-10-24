package com.danielportelabyrne.cinema.data.stream

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.view.Display
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.audio.AudioCapabilities
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

private const val TAG = "SmartStream"

// ---------------------------------------------------------------------------------------------
// Device
// ---------------------------------------------------------------------------------------------

/** Asks the real hardware what it can decode and display. */
object DeviceProfile {
    @Volatile private var cached: DeviceCapabilities? = null

    @Suppress("DEPRECATION")
    fun detect(context: Context): DeviceCapabilities = cached ?: synchronized(this) {
        cached ?: build(context).also { cached = it }
    }

    @Suppress("DEPRECATION")
    private fun build(context: Context): DeviceCapabilities {
        val display = (context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager).getDisplay(Display.DEFAULT_DISPLAY)
        // Physical panel size: a TV can run apps at a 1080p layout on a 4K panel.
        val maxHeight = display?.supportedModes?.maxOfOrNull { max(it.physicalHeight, it.physicalWidth * 9 / 16) } ?: 1080
        val hdr = try { display?.hdrCapabilities?.supportedHdrTypes?.toSet() } catch (_: Exception) { null }.orEmpty()

        val decoders = try {
            MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos.filter { !it.isEncoder }
        } catch (_: Exception) { emptyList() }
        fun decodes(mime: String, hardwareOnly: Boolean) = decoders.any { info ->
            info.supportedTypes.any { it.equals(mime, true) } &&
                (!hardwareOnly || android.os.Build.VERSION.SDK_INT < 29 || info.isHardwareAccelerated)
        }
        val codecs = buildSet {
            add(VideoCodec.H264)
            if (decodes("video/hevc", true)) add(VideoCodec.HEVC)
            if (decodes("video/av01", true)) add(VideoCodec.AV1)
        }
        val dv = HDR_DV in hdr && decodes("video/dolby-vision", false)
        val hdr10 = HDR_HDR10 in hdr && VideoCodec.HEVC in codecs
        val hdr10Plus = HDR_HDR10_PLUS in hdr && VideoCodec.HEVC in codecs

        val audio = buildSet {
            add(AudioFormat.AAC); add(AudioFormat.AC3); add(AudioFormat.EAC3)
            try {
                val caps = AudioCapabilities.getCapabilities(context, AudioAttributes.DEFAULT, null)
                if (caps.supportsEncoding(C.ENCODING_E_AC3_JOC)) add(AudioFormat.ATMOS)
                if (caps.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)) { add(AudioFormat.TRUEHD) }
                if (caps.supportsEncoding(C.ENCODING_DTS_HD)) add(AudioFormat.DTS_HD)
                if (caps.supportsEncoding(C.ENCODING_DTS)) add(AudioFormat.DTS)
            } catch (e: Exception) { Log.w(TAG, "Audio capability probe failed: ${e.message}") }
        }
        return DeviceCapabilities(maxHeight, codecs, dv, hdr10, hdr10Plus, audio).also { Log.i(TAG, "Device: $it") }
    }

    private const val HDR_DV = 1
    private const val HDR_HDR10 = 2
    private const val HDR_HDR10_PLUS = 4
}

// ---------------------------------------------------------------------------------------------
// Network
// ---------------------------------------------------------------------------------------------

/**
 * A rolling bandwidth estimate. It is fed by real observations (player throughput, tiny ranged
 * downloads of a candidate) and blended toward Android's own link estimate when it has gone stale.
 */
class NetworkEstimator(private val context: Context) {
    private val prefs = context.getSharedPreferences("network_estimate", Context.MODE_PRIVATE)

    fun current(now: Long = System.currentTimeMillis()): NetworkEstimate {
        val measured = prefs.getLong("bps", 0L)
        val at = prefs.getLong("at", 0L)
        val prior = linkPrior()
        if (measured <= 0L) return NetworkEstimate(prior, Long.MAX_VALUE, "Android link estimate")
        val age = now - at
        // Trust observations for a couple of days, then drift back toward the prior.
        val freshness = (1.0 - age / (3 * 86_400_000.0)).coerceIn(0.0, 1.0)
        return NetworkEstimate((measured * freshness + prior * (1 - freshness)).toLong(), age, "measured")
    }

    /** Folds a new observation into the rolling average. Observations under ~1 Mbps are ignored as noise. */
    fun record(bps: Long, now: Long = System.currentTimeMillis()) {
        if (bps < 1_000_000L) return
        val old = prefs.getLong("bps", 0L)
        val next = if (old <= 0L) bps else (old * 0.6 + bps * 0.4).toLong()
        prefs.edit().putLong("bps", next).putLong("at", now).apply()
    }

    fun isStale(maxAgeMs: Long = 15 * 60_000L, now: Long = System.currentTimeMillis()) = now - prefs.getLong("at", 0L) > maxAgeMs

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /** [alive] is false when the link is dead (expired, 4xx/5xx, unreachable); [bps] is the observed speed when measured. */
    data class Probe(val alive: Boolean, val bps: Long?)

    /**
     * Checks a stream link before we recommend it. With [measure] it also downloads about 2 MB to gauge
     * speed; without, it fetches a single byte, which is enough to catch expired or dead links.
     */
    fun probe(url: String, headers: Map<String, String> = emptyMap(), measure: Boolean): Probe {
        if (!measure) return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8000; c.readTimeout = 8000
            c.setRequestProperty("Range", "bytes=0-0")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            try { Probe(c.responseCode in 200..299, null) } finally { c.disconnect() }
        } catch (e: Exception) { Log.w(TAG, "Probe failed: ${e.javaClass.simpleName}"); Probe(false, null) }
        return sampleProbe(url, headers)
    }

    private fun sampleProbe(url: String, headers: Map<String, String>): Probe {
        val bps = sample(url, headers)
        return Probe(alive = lastSampleAlive, bps = bps)
    }

    @Volatile private var lastSampleAlive = true

    /** Downloads about 2 MB of [url] and returns the observed speed. Also proves the link is alive. */
    fun sample(url: String, headers: Map<String, String> = emptyMap()): Long? = try {
        lastSampleAlive = true
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("Range", "bytes=0-${SAMPLE_BYTES - 1}")
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        val start = System.nanoTime()
        var total = 0L
        try {
            if (c.responseCode !in 200..299) { lastSampleAlive = false; throw IllegalStateException("HTTP ${c.responseCode}") }
            c.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (total < SAMPLE_BYTES) { val n = input.read(buffer); if (n < 0) break; total += n }
            }
        } finally { c.disconnect() }
        val seconds = (System.nanoTime() - start) / 1e9
        if (total < 256 * 1024 || seconds <= 0) null else (total * 8 / seconds).toLong()
    } catch (e: Exception) { Log.w(TAG, "Throughput sample failed: ${e.message}"); if (e !is IllegalStateException) lastSampleAlive = false; null }

    private fun linkPrior(): Long {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return DEFAULT_PRIOR_BPS)
        val kbps = caps?.linkDownstreamBandwidthKbps ?: 0
        // Android's figure is a link rate, not what you get: be sceptical, and never claim more than 100 Mbps unmeasured.
        return if (kbps <= 0) DEFAULT_PRIOR_BPS else (kbps * 1000L / 2).coerceIn(10_000_000L, 100_000_000L)
    }

    private companion object {
        const val SAMPLE_BYTES = 2_000_000L
        const val DEFAULT_PRIOR_BPS = 25_000_000L
    }
}

// ---------------------------------------------------------------------------------------------
// History
// ---------------------------------------------------------------------------------------------

/** The only thing persisted about viewing: one small record per playback session, capped. */
class PlaybackStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback_history", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<PlaybackRecord> = try {
        val a = JSONArray(prefs.getString("records", "[]"))
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            PlaybackRecord(
                o.getString("id"), o.getString("provider"),
                enumOr(o.optString("res"), Resolution.UNKNOWN), enumOr(o.optString("codec"), VideoCodec.UNKNOWN), enumOr(o.optString("src"), SourceType.UNKNOWN),
                o.optLong("bitrate", -1).takeIf { it > 0 }, o.optLong("startup", -1).takeIf { it >= 0 }, o.optLong("observed", -1).takeIf { it > 0 },
                o.optInt("bufN"), o.optLong("bufMs"), o.optLong("playMs"), o.optBoolean("done"), o.optBoolean("failed"), o.optLong("t")
            )
        }
    } catch (_: Exception) { emptyList() }

    @Synchronized
    fun add(r: PlaybackRecord) {
        val list = (all() + r).takeLast(MAX_RECORDS)
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("id", it.contentId).put("provider", it.provider).put("res", it.resolution.name).put("codec", it.codec.name)
                .put("src", it.source.name).put("bitrate", it.estimatedBitrateBps ?: -1).put("startup", it.startupTimeMs ?: -1)
                .put("observed", it.observedThroughputBps ?: -1).put("bufN", it.bufferEventCount).put("bufMs", it.totalBufferDurationMs)
                .put("playMs", it.playDurationMs).put("done", it.completedNormally).put("failed", it.failed).put("t", it.timestampMs))
        }
        prefs.edit().putString("records", a.toString()).apply()
    }

    fun statsByProvider(now: Long = System.currentTimeMillis()): Map<String, ProviderStats> =
        all().groupBy { it.provider }.mapValues { ProviderStatsCalculator.compute(it.value, now) }

    private inline fun <reified T : Enum<T>> enumOr(name: String, default: T): T = enumValues<T>().firstOrNull { it.name == name } ?: default

    private companion object { const val MAX_RECORDS = 200 }
}

// ---------------------------------------------------------------------------------------------
// Sources: Stremio-compatible addons
// ---------------------------------------------------------------------------------------------

