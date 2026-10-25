package com.danielportelabyrne.cinema.data.stream

import kotlin.math.exp
import kotlin.math.ln

/** Every tunable number lives here, so the algorithm can be adjusted without hunting for magic values. */
object RankingWeights {
    /** Share of the estimated connection we allow a stream to need. */
    const val SAFE_FRACTION = 0.65

    /** Instantaneous bitrate can far exceed the file's average, especially for remuxes. */
    val PEAK_FACTOR = mapOf(
        SourceType.REMUX to 1.5, SourceType.BLURAY to 1.4, SourceType.WEB_DL to 1.25,
        SourceType.WEBRIP to 1.25, SourceType.HDTV to 1.2
    )
    const val DEFAULT_PEAK_FACTOR = 1.3

    val RESOLUTION = mapOf(Resolution.P2160 to 400, Resolution.P1080 to 250, Resolution.P720 to 100, Resolution.SD to 20, Resolution.UNKNOWN to 0)
    val SOURCE = mapOf(SourceType.REMUX to 100, SourceType.BLURAY to 80, SourceType.WEB_DL to 70, SourceType.WEBRIP to 40, SourceType.HDTV to 20, SourceType.CAM to -500, SourceType.UNKNOWN to 0)
    val RANGE = mapOf(DynamicRange.DOLBY_VISION to 40, DynamicRange.HDR10_PLUS to 35, DynamicRange.HDR10 to 30)
    const val CODEC_MODERN = 20        // AV1 / HEVC, only when the TV can decode it
    val AUDIO = mapOf(AudioFormat.ATMOS to 30, AudioFormat.TRUEHD to 25, AudioFormat.DTS_HD to 22, AudioFormat.DTS to 12, AudioFormat.EAC3 to 15, AudioFormat.AC3 to 8, AudioFormat.AAC to 5)
    const val AUDIO_UNSUPPORTED = 5

    // Reliability, from playback history.
    const val KNOWN_FAST_SOURCE = 100
    const val VERY_FAST_STARTUP = 50
    const val GOOD_HISTORY = 100
    const val SLOW_STARTUP = -100
    const val PREVIOUS_BUFFERING = -200
    const val REPEATED_FAILURES = -300
    const val VERY_FAST_STARTUP_MS = 2_500L
    const val SLOW_STARTUP_MS = 8_000L

    // Torrent health. Below NO_SEEDERS a torrent will not start; the middle band is neutral.
    const val FEW_SEEDERS_PENALTY = -150      // 1-2 seeders
    const val LOW_SEEDERS_PENALTY = -50       // 3-7
    const val GOOD_SEEDERS_BONUS = 30         // 30-99
    const val GREAT_SEEDERS_BONUS = 60        // 100+

    const val UNKNOWN_BITRATE_PENALTY = -40
    const val COMFORTABLE_HEADROOM_BONUS = 20
    /** Dolby Vision on a TV without it can show wrong colours (profile 5). */
    const val UNSUPPORTED_DV_PENALTY = -150

    /** Typical average bitrates (bits/s) used only when neither bitrate nor size is known. */
    fun typicalBps(resolution: Resolution, source: SourceType): Long = when (resolution) {
        Resolution.P2160 -> when (source) { SourceType.REMUX -> 70_000_000L; SourceType.BLURAY -> 50_000_000L; else -> 22_000_000L }
        Resolution.P1080 -> when (source) { SourceType.REMUX -> 30_000_000L; SourceType.BLURAY -> 15_000_000L; else -> 8_000_000L }
        Resolution.P720 -> 4_000_000L
        Resolution.SD -> 2_000_000L
        Resolution.UNKNOWN -> 10_000_000L
    }
}

object BitrateEstimator {
    /** fileSizeBits / runtimeSeconds. Null unless both are known. */
    fun averageBps(sizeBytes: Long?, runtimeSec: Long?): Long? {
        if (sizeBytes == null || runtimeSec == null || sizeBytes <= 0 || runtimeSec <= 0) return null
        return sizeBytes * 8 / runtimeSec
    }

    /** The bitrate the connection must sustain: average x peak headroom. Second value: was it derived from real data? */
    fun requiredBps(meta: StreamMetadata, runtimeSec: Long?): Pair<Long, Boolean> {
        val average = meta.bitrateBps ?: averageBps(meta.sizeBytes, runtimeSec)
        val known = average != null
        val base = average ?: RankingWeights.typicalBps(meta.resolution, meta.source)
        val peak = RankingWeights.PEAK_FACTOR[meta.source] ?: RankingWeights.DEFAULT_PEAK_FACTOR
        return (base * peak).toLong() to known
    }
}

/** Recency-weighted provider statistics: an old bad night fades instead of biasing results forever. */
object ProviderStatsCalculator {
    private const val HALF_LIFE_DAYS = 30.0

    fun compute(records: List<PlaybackRecord>, nowMs: Long): ProviderStats {
        if (records.isEmpty()) return ProviderStats.NONE
        val k = ln(2.0) / (HALF_LIFE_DAYS * 86_400_000.0)
        fun weight(r: PlaybackRecord) = exp(-k * (nowMs - r.timestampMs).coerceAtLeast(0))
        val total = records.sumOf { weight(it) }
        fun avg(values: List<Pair<Double, Long>>): Long? {
            val w = values.sumOf { it.first }
            return if (w <= 0) null else (values.sumOf { it.first * it.second } / w).toLong()
        }
        val startup = avg(records.filter { it.startupTimeMs != null }.map { weight(it) to it.startupTimeMs!! })
        val observed = avg(records.filter { it.observedThroughputBps != null && it.observedThroughputBps > 0 }.map { weight(it) to it.observedThroughputBps!! })
        val buffered = records.filter { !it.failed && it.bufferEventCount > 0 && it.totalBufferDurationMs > 2_000 }.sumOf { weight(it) } / total
        val failed = records.filter { it.failed }.sumOf { weight(it) } / total
        return ProviderStats(startup, observed, buffered, failed, records.size)
    }
}

/** Turns candidates into a ranked list. Pure and deterministic, so it is fully unit tested. */
object StreamRanker {

    fun rank(
        candidates: List<StreamCandidate>,
        device: DeviceCapabilities,
        network: NetworkEstimate,
        providerStats: Map<String, ProviderStats>,
        runtimeSec: Long?
    ): List<RankedStream> {
        val safe = network.safeBps()
        val scored = candidates.map { score(it, device, safe, providerStats[it.provider] ?: ProviderStats.NONE, runtimeSec) }

        val usable = scored.filter { it.verdict != Verdict.INCOMPATIBLE && it.verdict != Verdict.MAY_BUFFER }
            .sortedByDescending { it.score }
        val top = usable.firstOrNull { it.playable } ?: usable.firstOrNull()
        val ordered = usable.map { if (it === top) it.copy(verdict = Verdict.RECOMMENDED) else it }
        val risky = scored.filter { it.verdict == Verdict.MAY_BUFFER }.sortedBy { it.requiredBps }
        val incompatible = scored.filter { it.verdict == Verdict.INCOMPATIBLE }.sortedByDescending { it.score }
        return ordered + risky + incompatible
    }

    /**
     * The stream to auto-play: the best playable one that fits; if every playable stream is too heavy,
     * the lightest of those; otherwise null (the caller falls back to Stremio).
     */
    fun pickBest(ranked: List<RankedStream>): RankedStream? =
        ranked.firstOrNull { it.playable && it.verdict != Verdict.MAY_BUFFER && it.verdict != Verdict.INCOMPATIBLE }
            ?: ranked.filter { it.playable && it.verdict == Verdict.MAY_BUFFER }.minByOrNull { it.requiredBps }

    private fun score(c: StreamCandidate, device: DeviceCapabilities, safeBps: Long, stats: ProviderStats, runtimeSec: Long?): RankedStream {
        val m = c.meta
        val notes = mutableListOf<String>()
        val (required, known) = BitrateEstimator.requiredBps(m, runtimeSec)

        // ---- hard incompatibilities: this TV cannot show it ----
        if (m.resolution.height > device.maxHeight && m.resolution != Resolution.UNKNOWN) {
            return RankedStream(c, 0, required, known, Verdict.INCOMPATIBLE, "TV shows up to ${device.maxHeight}p", notes)
        }
        if (m.codec != VideoCodec.UNKNOWN && m.codec != VideoCodec.H264 && m.codec !in device.codecs) {
            return RankedStream(c, 0, required, known, Verdict.INCOMPATIBLE, "${m.codec.label} not supported by this TV", notes)
        }
        if (m.source == SourceType.CAM) {
            return RankedStream(c, 0, required, known, Verdict.INCOMPATIBLE, "Camera recording", notes)
        }

        var score = 0
        score += RankingWeights.RESOLUTION[m.resolution] ?: 0
        score += RankingWeights.SOURCE[m.source] ?: 0

        // ---- dynamic range: only credit what the TV can display ----
        when {
            m.range == DynamicRange.UNKNOWN || m.range == DynamicRange.SDR -> {}
            device.supportsRange(m.range) -> score += RankingWeights.RANGE[m.range] ?: 0
            m.range == DynamicRange.DOLBY_VISION && m.hdrFallback && device.hdr10 -> { score += RankingWeights.RANGE[DynamicRange.HDR10] ?: 0; notes += "Plays as HDR10" }
            m.range == DynamicRange.DOLBY_VISION -> { score += RankingWeights.UNSUPPORTED_DV_PENALTY; notes += "Dolby Vision unsupported" }
            else -> notes += "${m.range.label} unsupported, no credit"
        }

        if ((m.codec == VideoCodec.HEVC || m.codec == VideoCodec.AV1) && m.codec in device.codecs) score += RankingWeights.CODEC_MODERN

        if (m.audio != AudioFormat.UNKNOWN) {
            score += if (m.audio in device.audio || m.audio == AudioFormat.AAC) RankingWeights.AUDIO[m.audio] ?: 0 else RankingWeights.AUDIO_UNSUPPORTED
        }

        if (!known) { score += RankingWeights.UNKNOWN_BITRATE_PENALTY; notes += "Bitrate estimated" }

        // ---- torrent health (direct links have no seeders) ----
        if (c.stremioHandoff) m.seeders?.let { n ->
            when {
                n <= 0 -> return RankedStream(c, 0, required, known, Verdict.INCOMPATIBLE, "No one is sharing this torrent", notes)
                n < 3 -> { score += RankingWeights.FEW_SEEDERS_PENALTY; notes += "Only $n seeders" }
                n < 8 -> { score += RankingWeights.LOW_SEEDERS_PENALTY; notes += "Few seeders ($n)" }
                n >= 100 -> { score += RankingWeights.GREAT_SEEDERS_BONUS; notes += "$n seeders" }
                n >= 30 -> { score += RankingWeights.GOOD_SEEDERS_BONUS; notes += "$n seeders" }
            }
        }

        // ---- reliability from history ----
        if (stats.sessions >= 2 && stats.reliability >= 0.9 && (stats.averageObservedBps ?: 0) >= required * 3 / 2) { score += RankingWeights.KNOWN_FAST_SOURCE; notes += "Known fast source" }
        if (stats.sessions >= 2 && (stats.averageStartupMs ?: Long.MAX_VALUE) < RankingWeights.VERY_FAST_STARTUP_MS) score += RankingWeights.VERY_FAST_STARTUP
        if (stats.sessions >= 3 && stats.failureRate == 0.0 && stats.bufferRate < 0.1) score += RankingWeights.GOOD_HISTORY
        if (stats.sessions >= 2 && (stats.averageStartupMs ?: 0) > RankingWeights.SLOW_STARTUP_MS) { score += RankingWeights.SLOW_STARTUP; notes += "Slow to start" }
        if (stats.bufferRate >= 0.25) { score += (RankingWeights.PREVIOUS_BUFFERING * (stats.bufferRate / 0.5).coerceAtMost(1.0)).toInt(); notes += "Has buffered before" }
        if (stats.failureRate >= 0.34) { score += (RankingWeights.REPEATED_FAILURES * stats.failureRate.coerceAtMost(1.0)).toInt(); notes += "Failed before" }

        // ---- can the connection sustain it? ----
        if (required > safeBps) {
            return RankedStream(c, score, required, known, Verdict.MAY_BUFFER, "Needs about ${required / 1_000_000} Mbps, connection allows ${safeBps / 1_000_000}", notes)
        }
        val comfortable = required < safeBps / 2
        if (comfortable) score += RankingWeights.COMFORTABLE_HEADROOM_BONUS
        return RankedStream(c, score, required, known, if (comfortable) Verdict.EXCELLENT else Verdict.GOOD, null, notes)
    }
}
