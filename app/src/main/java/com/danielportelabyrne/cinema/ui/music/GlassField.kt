package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.HandlerThread
import android.view.View
import com.danielportelabyrne.cinema.data.music.ArtPalette
import com.danielportelabyrne.cinema.design.cinema.GlassRenderer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pools of the cover's colours drifting behind rippled glass, swelling with the music; the treble glints on the
 * ripple crests. Worked out small on its own thread and scaled up, so it stays soft and costs the screen one bitmap
 * a frame. All of it moves, and between the pools the screen is black.
 */
class GlassField(context: Context) : View(context) {
    private val thread = HandlerThread("glass-live").apply { start() }
    private val work = Handler(thread.looper)
    @Volatile private var busy = false
    private var rendered = 0
    private var spent = 0L
    @Volatile private var palette = ArtPalette.CINEMA.copyOf(POOLS)

    // Three bitmaps: one on screen, one just replaced (maybe still being drawn), one being written.
    private val frames = Array(3) { Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888) }
    @Volatile private var front = -1
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val dst = RectF()

    // Worked out once on the glass thread.
    private var sampleX = FloatArray(0)
    private var sampleY = FloatArray(0)
    private var lens = FloatArray(0)
    private var gloss = FloatArray(0)
    private val out = IntArray(W * H)
    private val lr = FloatArray(GW * GH); private val lg = FloatArray(GW * GH); private val lb = FloatArray(GW * GH)

    init { work.post { prepare() } }

    fun setPalette(colours: IntArray) { palette = colours.copyOf(POOLS).also { p -> for (i in p.indices) if (p[i] == 0) p[i] = ArtPalette.CINEMA[i % ArtPalette.CINEMA.size] } }

    /** Draws a new frame for [groups] (bass first, 0..1) unless the last is still being made. */
    fun show(groups: FloatArray, nowMs: Long) {
        if (busy) return
        busy = true
        val g = groups.copyOf()
        work.post {
            val started = System.nanoTime()
            try { render(g, nowMs) } finally { busy = false }
            spent += System.nanoTime() - started
            if (++rendered % 300 == 0) { android.util.Log.i(TAG, "glass frame ${spent / 300 / 1000} us"); spent = 0 }
        }
    }

    fun release() { thread.quitSafely() }

    override fun onDraw(canvas: Canvas) {
        val f = front.takeIf { it >= 0 } ?: return
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(frames[f], null, dst, blit)
    }

    private fun prepare() {
        val k = W / 960f
        val s = GlassRenderer.surface(W, H)
        val bend = BEND * k
        sampleX = FloatArray(W * H); sampleY = FloatArray(W * H); lens = FloatArray(W * H); gloss = FloatArray(W * H)
        for (py in 0 until H) for (px in 0 until W) {
            val i = py * W + px
            val gx = s.gx[i]; val gy = s.gy[i]; val curve = s.curve[i]
            sampleX[i] = ((px - gx * bend) / (W - 1)).coerceIn(0f, 1f) * (GW - 1.001f)
            sampleY[i] = ((py - gy * bend) / (H - 1)).coerceIn(0f, 1f) * (GH - 1.001f)
            val focus = (-curve * CAUSTIC).coerceIn(-0.6f, 1.6f)
            val slope = sqrt(gx * gx + gy * gy)
            val edge = 1f - 0.35f * (slope / 1.5f).coerceIn(0f, 1f).let { it * it }
            lens[i] = (1f + focus) * edge
            val flat = (1f - slope).coerceIn(0f, 1f)
            gloss[i] = flat * flat * flat * flat * flat * flat * (-curve * 2f).coerceIn(0f, 1f)
        }
    }

    private fun render(groups: FloatArray, nowMs: Long) {
        if (lens.isEmpty()) return
        val t = nowMs / 1000.0
        val colours = palette
        lr.fill(AMBIENT_R); lg.fill(AMBIENT_G); lb.fill(AMBIENT_B)
        for (p in 0 until POOLS) {
            val level = groups.getOrElse(p) { 0f }.coerceIn(0f, 1.2f)
            // Each pool wanders its own slow loop, so the screen never holds still.
            val cx = 0.5 + 0.36 * sin(2 * PI * t / PERIODS_X[p] + p * 1.7)
            val cy = 0.5 + 0.32 * sin(2 * PI * t / PERIODS_Y[p] + p * 2.3)
            val radius = (0.2f + 0.04f * p) * (0.8f + 0.55f * level)
            val strength = 0.22f + 1.15f * level
            val c = colours[p]
            val r = ((c shr 16) and 0xFF) / 255f * strength; val g = ((c shr 8) and 0xFF) / 255f * strength; val b = (c and 0xFF) / 255f * strength
            val inv = 1f / (radius * radius)
            for (y in 0 until GH) {
                val dy = (y / (GH - 1f) - cy.toFloat()) * ASPECT
                for (x in 0 until GW) {
                    val dx = x / (GW - 1f) - cx.toFloat()
                    val fall = exp(-(dx * dx + dy * dy) * inv)
                    if (fall < 0.004f) continue
                    val i = y * GW + x
                    lr[i] += r * fall; lg[i] += g * fall; lb[i] += b * fall
                }
            }
        }
        val glint = GLINT * groups.getOrElse(POOLS) { 0f }.coerceIn(0f, 1.2f)
        for (i in 0 until W * H) {
            val sx = sampleX[i]; val sy = sampleY[i]
            val x0 = sx.toInt(); val y0 = sy.toInt(); val fx = sx - x0; val fy = sy - y0
            val a = y0 * GW + x0; val b = a + 1; val c = a + GW; val d = c + 1
            fun at(l: FloatArray): Float { val top = l[a] + (l[b] - l[a]) * fx; val bottom = l[c] + (l[d] - l[c]) * fx; return top + (bottom - top) * fy }
            val lift = lens[i]
            val shine = gloss[i] * glint
            var r = at(lr) * lift; var g = at(lg) * lift; var bl = at(lb) * lift
            val lum = 0.3f * r + 0.59f * g + 0.11f * bl
            r += shine * (0.4f + lum); g += shine * (0.36f + lum); bl += shine * (0.3f + lum)
            out[i] = (0xFF shl 24) or (tone(r) shl 16) or (tone(g) shl 8) or tone(bl)
        }
        val next = (front + 1).mod(3)
        frames[next].setPixels(out, 0, W, 0, 0, W, H)
        front = next
        postInvalidateOnAnimation()
    }

    /** Soft highlights: bright pools round off instead of clipping. */
    private fun tone(v: Float): Int = (v / (1f + v) * 1.35f * 255f).toInt().coerceIn(0, 255)

    companion object {
        const val POOLS = 5
        private const val TAG = "CinemaMusic"
        private const val W = 480
        private const val H = 270
        private const val GW = 64
        private const val GH = 36
        private const val ASPECT = 9f / 16f
        private const val BEND = 26f
        private const val CAUSTIC = 4f
        private const val GLINT = 0.7f
        private const val AMBIENT_R = 0.018f
        private const val AMBIENT_G = 0.012f
        private const val AMBIENT_B = 0.008f
        private val PERIODS_X = doubleArrayOf(47.0, 61.0, 37.0, 53.0, 29.0)
        private val PERIODS_Y = doubleArrayOf(59.0, 41.0, 67.0, 31.0, 43.0)
    }
}
