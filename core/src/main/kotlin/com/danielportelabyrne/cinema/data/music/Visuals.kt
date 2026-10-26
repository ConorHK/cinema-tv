package com.danielportelabyrne.cinema.data.music

import kotlin.math.abs
import kotlin.math.sqrt

/** Which picture plays with the music. */
enum class MusicVisual(val label: String) {
    ARCHES("Arches"), GLASS("Glass");

    fun next(): MusicVisual = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(name: String?): MusicVisual = entries.firstOrNull { it.name == name } ?: ARCHES
    }
}

/**
 * Damped springs, one per value: each chases its target, settles with a small give, and never jitters. Stepped in
 * small fixed slices, so it behaves the same at any frame rate.
 */
class Springs(count: Int, private val stiffness: Float = 90f, private val damping: Float = 13f) {
    val values = FloatArray(count)
    private val speed = FloatArray(count)

    fun step(targets: FloatArray, dtMs: Long) {
        var left = dtMs.coerceIn(0L, 100L) / 1000f
        while (left > 0f) {
            val dt = minOf(left, SLICE)
            for (i in values.indices) {
                speed[i] += (stiffness * (targets[i] - values[i]) - damping * speed[i]) * dt
                values[i] = (values[i] + speed[i] * dt).coerceIn(0f, 1.25f)
            }
            left -= dt
        }
    }

    fun reset() { values.fill(0f); speed.fill(0f) }

    private companion object { const val SLICE = 0.004f }
}

object Bands {
    /** [levels] in [out.size] runs, bass first: half the run's mean, half its loudest, so a lone peak still shows. */
    fun group(levels: FloatArray, out: FloatArray) {
        val n = out.size
        for (g in 0 until n) {
            val from = g * levels.size / n
            val to = maxOf(from + 1, (g + 1) * levels.size / n)
            var sum = 0f; var top = 0f
            for (i in from until to) { sum += levels[i]; top = maxOf(top, levels[i]) }
            out[g] = 0.5f * sum / (to - from) + 0.5f * top
        }
    }
}

/** The colours an album cover is made of, kept rich and dark enough to glow on black. */
object ArtPalette {
    /** Cinema's own colours, for covers with too little colour of their own. */
    val CINEMA = intArrayOf(0xFFE27A49.toInt(), 0xFFEDBE56.toInt(), 0xFF3AA597.toInt(), 0xFFC4532A.toInt(), 0xFF8C4A8F.toInt())

    /** Up to [count] colours from ARGB [pixels], strongest first, distinct in hue, topped up from [CINEMA]. */
    fun pick(pixels: IntArray, count: Int): IntArray {
        val buckets = 18
        val weight = FloatArray(buckets)
        val r = FloatArray(buckets); val g = FloatArray(buckets); val b = FloatArray(buckets)
        for (p in pixels) {
            val (h, s, v) = hsv(p)
            val w = s * s * v
            if (s < 0.2f || v < 0.15f) continue
            val k = ((h / 360f) * buckets).toInt().coerceIn(0, buckets - 1)
            weight[k] += w
            r[k] += ((p shr 16) and 0xFF) * w; g[k] += ((p shr 8) and 0xFF) * w; b[k] += (p and 0xFF) * w
        }
        val total = weight.sum()
        val chosen = ArrayList<Int>()
        val order = (0 until buckets).sortedByDescending { weight[it] }
        for (k in order) {
            if (chosen.size == count || total <= 0f || weight[k] < total * 0.04f) break
            // Neighbouring hues read as one colour: keep the stronger.
            if (chosen.any { abs(it - k).let { d -> minOf(d, buckets - d) } <= 1 }) continue
            chosen += k
        }
        val out = chosen.map { k -> glowable(rgb(r[k] / weight[k], g[k] / weight[k], b[k] / weight[k])) }.toMutableList()
        var i = 0
        while (out.size < count) out += CINEMA[i++ % CINEMA.size]
        return out.toIntArray()
    }

    /** Saturation lifted and brightness set mid-high, so the colour glows rather than glares. */
    fun glowable(c: Int): Int {
        val (h, s, _) = hsv(c)
        return fromHsv(h, maxOf(s, 0.55f).coerceAtMost(0.9f), 0.85f)
    }

    fun hsv(c: Int): Triple<Float, Float, Float> {
        val r = ((c shr 16) and 0xFF) / 255f; val g = ((c shr 8) and 0xFF) / 255f; val b = (c and 0xFF) / 255f
        val max = maxOf(r, g, b); val min = minOf(r, g, b); val d = max - min
        val h = when {
            d == 0f -> 0f
            max == r -> 60f * (((g - b) / d) % 6f)
            max == g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }.let { if (it < 0f) it + 360f else it }
        return Triple(h, if (max == 0f) 0f else d / max, max)
    }

    fun fromHsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - abs((h / 60f) % 2f - 1))
        val m = v - c
        val (r, g, b) = when ((h / 60f).toInt() % 6) {
            0 -> Triple(c, x, 0f); 1 -> Triple(x, c, 0f); 2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c); 4 -> Triple(x, 0f, c); else -> Triple(c, 0f, x)
        }
        return rgb((r + m) * 255f, (g + m) * 255f, (b + m) * 255f)
    }

    private fun rgb(r: Float, g: Float, b: Float): Int =
        (0xFF shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)

    /** How far apart two colours are, roughly as the eye sees it. */
    fun distance(a: Int, b: Int): Float {
        val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
        val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return sqrt((2 * dr * dr + 4 * dg * dg + 3 * db * db).toFloat())
    }
}
