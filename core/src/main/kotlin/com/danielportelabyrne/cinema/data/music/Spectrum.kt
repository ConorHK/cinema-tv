package com.danielportelabyrne.cinema.data.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns a window of mono audio into [bands] levels from 0 to 1, spaced like hearing (log frequency). Treble is
 * tilted up to match how music is mixed, and an automatic gain keeps quiet tracks lively. Levels rise fast and
 * fall smoothly. Not thread safe.
 */
class Spectrum(val bands: Int = 64, val size: Int = 4096) {
    init { require(size > 0 && size and (size - 1) == 0) { "size must be a power of two" } }

    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / (size - 1))).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val power = FloatArray(size / 2)
    private val db = FloatArray(bands)
    private val current = FloatArray(bands)
    private var reference = FLOOR_DB

    /** The latest levels; the array is reused. */
    val levels: FloatArray get() = current

    /** Analyses the newest [size] samples of [samples] and moves [levels] toward them over [dtMs]. */
    fun update(samples: FloatArray, sampleRate: Int, dtMs: Long) {
        val n = minOf(size, samples.size)
        val from = samples.size - n
        for (i in 0 until size) { re[i] = if (i < size - n) 0f else samples[from + i - (size - n)] * window[i]; im[i] = 0f }
        fft(re, im)
        for (k in 0 until size / 2) power[k] = re[k] * re[k] + im[k] * im[k]
        val binHz = sampleRate.toDouble() / size
        val top = minOf(HIGH, sampleRate / 2.0 * 0.95)
        var loudest = -200f
        for (b in 0 until bands) {
            val lo = edge(b, top) / binHz
            val hi = edge(b + 1, top) / binHz
            val p = bandPower(lo, hi)
            val centre = edge(b, top) * (top / LOW).pow(0.5 / bands)
            val level = (10 * log10(p.toDouble().coerceAtLeast(1e-12) / NORM) + TILT_DB * ln(centre / 1000.0) / ln(2.0)).toFloat()
            db[b] = level
            if (level > loudest) loudest = level
        }
        // The loudest band sets the top; it falls slowly so loud passages stay loud.
        reference = maxOf(loudest, reference - GAIN_FALL_DB_PER_S * dtMs / 1000f, FLOOR_DB)
        val fall = FALL.toDouble().pow(dtMs / 16.0).toFloat()
        for (b in 0 until bands) {
            val target = ((db[b] - (reference - RANGE_DB)) / RANGE_DB).coerceIn(0f, 1f)
            current[b] = if (target > current[b]) current[b] + (target - current[b]) * RISE else maxOf(target, current[b] * fall)
        }
    }

    /** Lets the levels sink to rest (paused, between tracks). */
    fun decay(dtMs: Long) {
        val fall = FALL.toDouble().pow(dtMs / 16.0).toFloat()
        for (b in 0 until bands) current[b] *= fall
    }

    fun reset() { current.fill(0f); reference = FLOOR_DB }

    private fun edge(b: Int, top: Double): Double = LOW * (top / LOW).pow(b.toDouble() / bands)

    /** Mean power between fractional bins [lo] and [hi]; a band narrower than a bin reads between its neighbours. */
    private fun bandPower(lo: Double, hi: Double): Float {
        val last = size / 2 - 1
        if (hi - lo < 1.0) {
            val at = ((lo + hi) / 2).coerceIn(0.0, last.toDouble())
            val k = at.toInt().coerceAtMost(last - 1)
            val f = (at - k).toFloat()
            return power[k] * (1 - f) + power[k + 1] * f
        }
        val a = kotlin.math.ceil(lo).toInt().coerceIn(0, last)
        val z = kotlin.math.floor(hi).toInt().coerceIn(a, last)
        var sum = 0f
        for (k in a..z) sum += power[k]
        return sum / (z - a + 1)
    }

    private val NORM = (size / 4f) * (size / 4f)

    companion object {
        private const val LOW = 35.0
        private const val HIGH = 16_000.0
        private const val TILT_DB = 3.0
        private const val RANGE_DB = 42f
        private const val FLOOR_DB = -45f
        private const val GAIN_FALL_DB_PER_S = 4f
        private const val RISE = 0.55f
        private const val FALL = 0.9f

        /** In-place radix-2 FFT. */
        fun fft(re: FloatArray, im: FloatArray) {
            val n = re.size
            var j = 0
            for (i in 1 until n) {
                var bit = n shr 1
                while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
                j = j xor bit
                if (i < j) { re[i] = re[j].also { re[j] = re[i] }; im[i] = im[j].also { im[j] = im[i] } }
            }
            var len = 2
            while (len <= n) {
                val angle = -2 * PI / len
                val wr = cos(angle).toFloat(); val wi = sin(angle).toFloat()
                var start = 0
                while (start < n) {
                    var cr = 1f; var ci = 0f
                    for (k in 0 until len / 2) {
                        val a = start + k; val b = a + len / 2
                        val tr = re[b] * cr - im[b] * ci; val ti = re[b] * ci + im[b] * cr
                        re[b] = re[a] - tr; im[b] = im[a] - ti
                        re[a] += tr; im[a] += ti
                        val nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr
                    }
                    start += len
                }
                len = len shl 1
            }
        }
    }
}

/**
 * Mono samples tagged with the media time they play at, so the picture can follow the speaker rather than the
 * decoder, which runs a buffer ahead. Thread safe: one thread writes, another reads.
 */
class AudioHistory(private val capacity: Int = 1 shl 17) {
    private val ring = FloatArray(capacity)
    private var written = 0L
    private var endUs = Long.MIN_VALUE
    private var rate = 0

    /** Adds [count] samples whose first plays at [startUs]. */
    @Synchronized fun add(samples: FloatArray, count: Int, startUs: Long, sampleRate: Int) {
        if (sampleRate != rate) { rate = sampleRate; written = 0 }
        for (i in 0 until count) ring[((written + i) % capacity).toInt()] = samples[i]
        written += count
        endUs = startUs + count * 1_000_000L / sampleRate
    }

    @Synchronized fun clear() { written = 0; endUs = Long.MIN_VALUE }

    val sampleRate: Int @Synchronized get() = rate

    /** Fills [out] with the samples ending at [playingUs]; false when there are none for that time. */
    @Synchronized fun window(playingUs: Long, out: FloatArray): Boolean {
        if (written == 0L || rate == 0 || endUs == Long.MIN_VALUE) return false
        val behind = (endUs - playingUs) * rate / 1_000_000L
        if (behind < 0 || behind + out.size > minOf(written, capacity.toLong())) return false
        val last = written - behind
        for (i in out.indices) out[i] = ring[(((last - out.size + i) % capacity + capacity) % capacity).toInt()]
        return true
    }
}
