package com.danielportelabyrne.cinema.data.music

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Turns mono audio into [bands] levels from 0 to 1, spaced like hearing (log frequency, 40 Hz up). Levels rise fast
 * and fall slowly, so the bars read as music rather than flicker. Not thread safe: feed and read from one thread.
 */
class Spectrum(val bands: Int = 32, private val size: Int = 2048) {
    init { require(size > 0 && size and (size - 1) == 0) { "size must be a power of two" } }

    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / (size - 1))).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val ring = FloatArray(size)
    private var filled = 0
    private var at = 0
    private val current = FloatArray(bands)

    /** The latest levels; the array is reused. */
    val levels: FloatArray get() = current

    /** Adds [count] mono samples (-1..1) and updates [levels] from the newest [size] of them. */
    fun feed(samples: FloatArray, count: Int = samples.size, sampleRate: Int) {
        for (i in 0 until count) { ring[at] = samples[i]; at = (at + 1) % size }
        filled = minOf(size, filled + count)
        if (filled < size) return
        for (i in 0 until size) { re[i] = ring[(at + i) % size] * window[i]; im[i] = 0f }
        fft(re, im)
        val top = minOf(16_000.0, sampleRate / 2.0)
        for (b in 0 until bands) {
            val lo = bin(edge(b, top), sampleRate)
            val hi = maxOf(lo + 1, bin(edge(b + 1, top), sampleRate))
            var peak = 0f
            for (k in lo until minOf(hi, size / 2)) peak = maxOf(peak, hypot(re[k], im[k]))
            val target = loudness(peak)
            current[b] = if (target > current[b]) current[b] + (target - current[b]) * RISE else current[b] * FALL
        }
    }

    /** Lets the bars sink to rest (paused, between tracks). */
    fun decay() { for (b in 0 until bands) current[b] *= FALL }

    fun reset() { filled = 0; at = 0; current.fill(0f) }

    private fun edge(b: Int, top: Double): Double = LOW * (top / LOW).pow(b.toDouble() / bands)
    private fun bin(hz: Double, sampleRate: Int): Int = (hz * size / sampleRate).toInt()

    /** Peak magnitude as 0..1 over the last [RANGE_DB] decibels below full scale. */
    private fun loudness(peak: Float): Float {
        if (peak <= 0f) return 0f
        val db = 20 * log10(peak / (size / 4f))
        return ((db + RANGE_DB) / RANGE_DB).coerceIn(0f, 1f)
    }

    companion object {
        private const val LOW = 40.0
        private const val RANGE_DB = 60f
        private const val RISE = 0.6f
        private const val FALL = 0.88f

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
