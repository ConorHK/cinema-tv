package com.danielportelabyrne.cinema.data.stream

/**
 * Domain model for smart stream selection. Everything in this package except the `*Android*` /
 * `*Store` / `*Estimator` / `*Sources` / `*Repository` classes is plain Kotlin so it can be unit tested.
 * Unknown values stay UNKNOWN (or null); nothing is guessed at parse time.
 */
enum class Resolution(val label: String, val height: Int) {
    P2160("4K", 2160), P1080("1080p", 1080), P720("720p", 720), SD("SD", 480), UNKNOWN("", 0)
}

enum class SourceType(val label: String) {
    REMUX("REMUX"), BLURAY("BluRay"), WEB_DL("WEB-DL"), WEBRIP("WEBRip"), HDTV("HDTV"), CAM("CAM"), UNKNOWN("")
}

enum class VideoCodec(val label: String) { AV1("AV1"), HEVC("HEVC"), H264("H.264"), UNKNOWN("") }

enum class DynamicRange(val label: String) {
    DOLBY_VISION("Dolby Vision"), HDR10_PLUS("HDR10+"), HDR10("HDR10"), SDR("SDR"), UNKNOWN("")
}

enum class AudioFormat(val label: String) {
    ATMOS("Atmos"), TRUEHD("TrueHD"), DTS_HD("DTS-HD"), DTS("DTS"), EAC3("DD+"), AC3("AC3"), AAC("AAC"), UNKNOWN("")
}

data class StreamMetadata(
    val resolution: Resolution = Resolution.UNKNOWN,
    val source: SourceType = SourceType.UNKNOWN,
    val codec: VideoCodec = VideoCodec.UNKNOWN,
    val range: DynamicRange = DynamicRange.UNKNOWN,
    /** True when a Dolby Vision file also advertises an HDR10 base layer (so it degrades gracefully). */
    val hdrFallback: Boolean = false,
    val audio: AudioFormat = AudioFormat.UNKNOWN,
    val sizeBytes: Long? = null,
    /** Explicit bitrate if the source gave one; otherwise derived from size and runtime. */
    val bitrateBps: Long? = null,
    val filename: String? = null,
    /** Torrent health: how many peers are sharing it. Null when the source does not say. */
    val seeders: Int? = null
) {
    /** For the PLAY button: resolution and dynamic range only ("4K \u00b7 Dolby Vision", "1080p"). */
    fun shortLabel(): String = listOf(resolution.label, range.label.takeIf { range != DynamicRange.SDR })
        .filterNotNull().filter { it.isNotBlank() }.joinToString(" \u00b7 ").ifBlank { "Best available" }

    /** "4K · Dolby Vision · WEB-DL": only what is known, and no "SDR" noise. */
    fun qualityLabel(): String = listOf(resolution.label, range.label.takeIf { range != DynamicRange.SDR }, source.label)
        .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Unknown quality" }
}

data class StreamCandidate(
    val id: String,
    val provider: String,
    val displayName: String,
    /** A direct http(s) link, when the source supplies one. Torrent-only sources have none. */
    val url: String?,
    val infoHash: String?,
    val meta: StreamMetadata,
    val notWebReady: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    /** True when [url] points at Stremio's local streaming engine (a torrent result). */
    val viaEngine: Boolean = false,
    val trackers: List<String> = emptyList(),
    val fileIdx: Int? = null
) {
    /** Torrent results Stremio can play itself, in its own player (with its subtitles), through a magnet link. */
    val stremioHandoff: Boolean get() = url == null && infoHash != null && !isPack

    /**
     * A torrent holding several files where the film is not the first (Torrentio reports its position as a
     * file index). A magnet link cannot say which file to open, so Stremio would open the wrong film.
     */
    val isPack: Boolean get() = fileIdx != null && fileIdx > 0

    /** magnet: link for [stremioHandoff] results. */
    fun magnetUri(title: String): String =
        "magnet:?xt=urn:btih:$infoHash&dn=" + java.net.URLEncoder.encode(title, "UTF-8") +
            trackers.take(10).joinToString("") { "&tr=" + java.net.URLEncoder.encode(it, "UTF-8") } +
            (fileIdx?.let { "&so=$it" } ?: "")

    /** Can the app's own player attempt this? Torrent hashes and "not web ready" streams cannot. */
    val directPlayable: Boolean get() = url != null && (url.startsWith("http://") || url.startsWith("https://")) && !notWebReady
}

/** What this TV can really decode and show. */
data class DeviceCapabilities(
    val maxHeight: Int,
    val codecs: Set<VideoCodec>,
    val dolbyVision: Boolean,
    val hdr10: Boolean,
    val hdr10Plus: Boolean,
    val audio: Set<AudioFormat>
) {
    fun supportsRange(range: DynamicRange): Boolean = when (range) {
        DynamicRange.DOLBY_VISION -> dolbyVision
        DynamicRange.HDR10_PLUS -> hdr10Plus
        DynamicRange.HDR10 -> hdr10
        else -> true
    }
}

/** A bandwidth estimate in bits per second, with how it was obtained. */
data class NetworkEstimate(val bps: Long, val ageMs: Long, val basis: String) {
    fun safeBps(fraction: Double = RankingWeights.SAFE_FRACTION): Long = (bps * fraction).toLong()
    val mbps: Double get() = bps / 1_000_000.0
}

/** One observed playback session; the only thing we persist about viewing. */
data class PlaybackRecord(
    val contentId: String,
    val provider: String,
    val resolution: Resolution,
    val codec: VideoCodec,
    val source: SourceType,
    val estimatedBitrateBps: Long?,
    val startupTimeMs: Long?,
    val observedThroughputBps: Long?,
    val bufferEventCount: Int,
    val totalBufferDurationMs: Long,
    val playDurationMs: Long,
    val completedNormally: Boolean,
    val failed: Boolean,
    val timestampMs: Long
)

/** Rolling, recency-weighted statistics for one provider. */
data class ProviderStats(
    val averageStartupMs: Long?,
    val averageObservedBps: Long?,
    /** Fraction of sessions that buffered mid-play. */
    val bufferRate: Double,
    val failureRate: Double,
    val sessions: Int
) {
    val reliability: Double get() = (1.0 - (failureRate * 0.7 + bufferRate * 0.3)).coerceIn(0.0, 1.0)

    companion object { val NONE = ProviderStats(null, null, 0.0, 0.0, 0) }
}

enum class Verdict { RECOMMENDED, EXCELLENT, GOOD, MAY_BUFFER, INCOMPATIBLE }

data class RankedStream(
    val candidate: StreamCandidate,
    val score: Int,
    val requiredBps: Long,
    val requiredKnown: Boolean,
    val verdict: Verdict,
    val reason: String?,
    val notes: List<String>
) {
    /** PLAY can do something with it: play it here (direct link) or hand it to Stremio's player (torrent). */
    val playable: Boolean get() = candidate.directPlayable || candidate.stremioHandoff
}
