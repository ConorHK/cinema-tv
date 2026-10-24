package com.danielportelabyrne.cinema.design.cinema

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * The paused picture seen through silky rippled glass with the picture itself as the light behind it: its colours come
 * through, bent about by each ripple; light gathers under the crests (each acts as a small lens) and thins in the
 * hollows; steep sides dim a little; the bright parts of the picture bloom out through the glass; and the crests carry
 * a faint gloss of their own. There is no lamp in front: all the light is the picture's.
 *
 * Pure arithmetic on ARGB pixel arrays, so it runs anywhere (and in tests). The glass surface is made once per size and
 * kept; [render] then takes a fraction of a second for a 960 x 540 picture.
 */
object GlassRenderer {
    // The surface, in pixels at 960 wide: blob size (wider than tall, like rolled glass), how far the blobs are pushed
    // about, how steep-sided they are, how smooth (no grain finer than the blobs), and how strongly the slopes are felt.
    private const val CELL_X = 30f
    private const val CELL_Y = 20f
    private const val WARP = 1.5f
    private const val SHARP = 2.6f
    private const val SMOOTH = 3.5f
    private const val BUMP = 15f

    // The light: how far the picture is bent, how soft it is behind the glass, how much the crests gather light, how much
    // steep sides dim, colour and warmth, and how far and strongly the bright parts bloom.
    private const val BEND = 17f
    private const val SOFT = 2
    private const val CAUSTIC = 4f
    private const val EDGE = 0.3f
    private const val SATURATION = 1.25f
    private const val WARMTH = 0.35f
    private const val GAIN = 1.05f
    private const val BLOOM_RADIUS = 16
    private const val BLOOM = 0.55f
    private const val SHEEN = 0.25f

    /** Per pixel: the slopes across and down, and the curvature (negative on crests). */
    class Surface(val width: Int, val height: Int, val gx: FloatArray, val gy: FloatArray, val curve: FloatArray)

    @Volatile private var cached: Surface? = null

    fun surface(w: Int, h: Int): Surface = cached?.takeIf { it.width == w && it.height == h } ?: makeSurface(w, h).also { cached = it }

    /**
     * Value noise in two octaves, its coordinates pushed about by a broader noise, its levels pushed towards the ends so
     * the ripples have rounded tops and steeper sides, then smoothed; then its slopes and curvature. Sizes scale with
     * [w], so the glass looks the same at any resolution.
     */
    fun makeSurface(w: Int, h: Int): Surface {
        val k = w / 960f
        val cx = CELL_X * k; val cy = CELL_Y * k
        val height = FloatArray(w * h)
        for (py in 0 until h) for (px in 0 until w) {
            var x = px / cx; var y = py / cy
            x += (noise(x * 0.45f, y * 0.45f, 11) - 0.5f) * WARP
            y += (noise(x * 0.45f, y * 0.45f, 12) - 0.5f) * WARP
            val v = (noise(x, y, 1) + 0.4f * noise(x * 2.03f, y * 2.03f, 2)) / 1.4f
            height[py * w + px] = 0.5f + 0.5f * tanh((v - 0.5f) * SHARP)
        }
        gaussian(height, w, h, SMOOTH * k)
        val gx = FloatArray(w * h); val gy = FloatArray(w * h); val curve = FloatArray(w * h)
        val bump = BUMP / k
        for (py in 0 until h) for (px in 0 until w) {
            val i = py * w + px
            val l = height[py * w + (px - 1).coerceAtLeast(0)]; val r = height[py * w + (px + 1).coerceAtMost(w - 1)]
            val u = height[(py - 1).coerceAtLeast(0) * w + px]; val d = height[(py + 1).coerceAtMost(h - 1) * w + px]
            gx[i] = (r - l) * 0.5f * bump
            gy[i] = (d - u) * 0.5f * bump
            curve[i] = (l + r + u + d - 4 * height[i]) * bump
        }
        return Surface(w, h, gx, gy, curve)
    }

    /** [pixels] (ARGB, [w] x [h], already cropped to the screen's shape) seen through the glass. A new array. */
    fun render(pixels: IntArray, w: Int, h: Int): IntArray {
        val k = w / 960f
        val s = surface(w, h)
        val soft = pixels.copyOf().also { p -> repeat(3) { boxBlur(p, w, h, (SOFT * k).toInt().coerceAtLeast(1)) } }
        val glow = pixels.copyOf().also { p -> repeat(3) { boxBlur(p, w, h, (BLOOM_RADIUS * k).toInt().coerceAtLeast(1)) } }
        val out = IntArray(w * h)
        val bend = BEND * k
        for (py in 0 until h) for (px in 0 until w) {
            val i = py * w + px
            val gx = s.gx[i]; val gy = s.gy[i]; val curve = s.curve[i]
            // The picture, bent through the ripple (bilinear, so the bending stays smooth).
            val sx = (px - gx * bend).coerceIn(0f, w - 1.001f); val sy = (py - gy * bend).coerceIn(0f, h - 1.001f)
            val x0 = sx.toInt(); val y0 = sy.toInt(); val fx = sx - x0; val fy = sy - y0
            val c00 = soft[y0 * w + x0]; val c10 = soft[y0 * w + x0 + 1]; val c01 = soft[(y0 + 1) * w + x0]; val c11 = soft[(y0 + 1) * w + x0 + 1]
            fun ch(shift: Int): Float {
                val a = ((c00 shr shift) and 0xFF) + (((c10 shr shift) and 0xFF) - ((c00 shr shift) and 0xFF)) * fx
                val b = ((c01 shr shift) and 0xFF) + (((c11 shr shift) and 0xFF) - ((c01 shr shift) and 0xFF)) * fx
                return (a + (b - a) * fy) / 255f
            }
            val r = ch(16); val g = ch(8); val b = ch(0)

            // Light gathered under the crests, thinned in the hollows; steep sides a little dimmer.
            val focus = (-curve * CAUSTIC).coerceIn(-0.6f, 1.5f)
            val slope = sqrt(gx * gx + gy * gy)
            val edge = 1f - EDGE * (slope / 1.5f).coerceIn(0f, 1f).let { it * it }
            val lift = (1f + focus) * edge * GAIN

            // Its own colour, a little richer and warmer, as if a warm lamp were behind it.
            val lum = 0.299f * r + 0.587f * g + 0.114f * b
            var cr = (lum + (r - lum) * SATURATION) * (1f - WARMTH + 1.12f * WARMTH) * lift
            var cg = (lum + (g - lum) * SATURATION) * (1f - WARMTH + 0.92f * WARMTH) * lift
            var cb = (lum + (b - lum) * SATURATION) * (1f - WARMTH + 0.72f * WARMTH) * lift

            // The bright parts of the picture bloom out through the glass (a screen blend).
            val gc = glow[i]
            val gr = ((gc shr 16) and 0xFF) / 255f; val gg = ((gc shr 8) and 0xFF) / 255f; val gb = (gc and 0xFF) / 255f
            val bloom = ((0.299f * gr + 0.587f * gg + 0.114f * gb) * 1.3f - 0.25f).coerceIn(0f, 1f) * BLOOM
            cr = 1f - (1f - cr.coerceIn(0f, 1f)) * (1f - gr * bloom)
            cg = 1f - (1f - cg.coerceIn(0f, 1f)) * (1f - gg * bloom)
            cb = 1f - (1f - cb.coerceIn(0f, 1f)) * (1f - gb * bloom)

            // The glass's own faint gloss on the crests.
            val flat = (1f - slope).coerceIn(0f, 1f)
            val sheen = flat * flat * flat * flat * flat * flat * (-curve * 2f).coerceIn(0f, 1f) * SHEEN
            out[i] = (0xFF shl 24) or (byte(cr + sheen) shl 16) or (byte(cg + sheen) shl 8) or byte(cb + sheen)
        }
        return out
    }

    private fun byte(v: Float) = (v.coerceIn(0f, 1f) * 255f + 0.5f).toInt()

    private fun hash(ix: Int, iy: Int, seed: Int): Float {
        var h = ix * 374761393 + iy * 668265263 + seed * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private fun noise(x: Float, y: Float, seed: Int): Float {
        val fx0 = floor(x); val fy0 = floor(y)
        val ix = fx0.toInt(); val iy = fy0.toInt()
        val fx = x - fx0; val fy = y - fy0
        val ux = fx * fx * (3 - 2 * fx); val uy = fy * fy * (3 - 2 * fy)
        val a = hash(ix, iy, seed); val b = hash(ix + 1, iy, seed)
        val c = hash(ix, iy + 1, seed); val d = hash(ix + 1, iy + 1, seed)
        return (a + (b - a) * ux) * (1 - uy) + (c + (d - c) * ux) * uy
    }

    /** A gaussian blur of spread [sigma], in place, across then down. */
    private fun gaussian(v: FloatArray, w: Int, h: Int, sigma: Float) {
        val r = (sigma * 3).toInt() + 1
        val kernel = FloatArray(2 * r + 1) { exp(-((it - r) * (it - r)) / (2 * sigma * sigma)) }
        val sum = kernel.sum(); for (i in kernel.indices) kernel[i] /= sum
        val tmp = FloatArray(v.size)
        for (y in 0 until h) for (x in 0 until w) {
            var acc = 0f
            for (j in -r..r) acc += v[y * w + (x + j).coerceIn(0, w - 1)] * kernel[j + r]
            tmp[y * w + x] = acc
        }
        for (y in 0 until h) for (x in 0 until w) {
            var acc = 0f
            for (j in -r..r) acc += tmp[(y + j).coerceIn(0, h - 1) * w + x] * kernel[j + r]
            v[y * w + x] = acc
        }
    }

    /** One pass of a box blur of radius [r], in place, across then down. */
    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int) {
        val tmp = IntArray(px.size)
        fun pass(src: IntArray, dst: IntArray, horizontal: Boolean) {
            val outer = if (horizontal) h else w; val inner = if (horizontal) w else h
            val n = 2 * r + 1
            for (o in 0 until outer) {
                fun at(j: Int) = src[if (horizontal) o * w + j.coerceIn(0, inner - 1) else j.coerceIn(0, inner - 1) * w + o]
                var sr = 0; var sg = 0; var sb = 0
                for (j in -r..r) { val c = at(j); sr += (c shr 16) and 0xFF; sg += (c shr 8) and 0xFF; sb += c and 0xFF }
                for (i in 0 until inner) {
                    dst[if (horizontal) o * w + i else i * w + o] = (0xFF shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
                    val add = at(i + r + 1); val sub = at(i - r)
                    sr += ((add shr 16) and 0xFF) - ((sub shr 16) and 0xFF)
                    sg += ((add shr 8) and 0xFF) - ((sub shr 8) and 0xFF)
                    sb += (add and 0xFF) - (sub and 0xFF)
                }
            }
        }
        pass(px, tmp, true); pass(tmp, px, false)
    }
}
