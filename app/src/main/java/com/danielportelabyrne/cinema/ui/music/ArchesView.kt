package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.danielportelabyrne.cinema.design.Cinema
import kotlin.math.PI
import kotlin.math.sin

/**
 * The Cinema arches as the music: one 70s arch of nested colour bands per run of frequencies, bass on the left. Each
 * rises on a spring and breathes slowly when the music is quiet. The columns fade out toward the floor and no top
 * rests in one place, so nothing is lit in a fixed spot.
 */
class ArchesView(context: Context) : View(context) {
    private val levels = FloatArray(COUNT)
    private var timeMs = 0L

    /** Turns the colours round by one band per step; set per track. */
    var shift = 0
        set(v) { field = v; invalidate() }

    private val rings = Array(PALETTE.size) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val glows = Array(PALETTE.size) { Paint(Paint.ANTI_ALIAS_FLAG) }
    private val core = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; color = Cinema.CREAM }
    private val arc = RectF()
    private var glowRadius = 1f

    fun show(next: FloatArray, nowMs: Long) {
        System.arraycopy(next, 0, levels, 0, minOf(next.size, COUNT))
        timeMs = nowMs
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val fade = floatArrayOf(0f, 0.42f, 0.9f)
        PALETTE.forEachIndexed { i, c ->
            rings[i].shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Cinema.alpha(c, 0.92f), Cinema.alpha(c, 0.8f), Cinema.alpha(c, 0f)), fade, Shader.TileMode.CLAMP)
        }
        core.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(Cinema.BG, Cinema.BG, Cinema.alpha(Cinema.BG, 0f)), fade, Shader.TileMode.CLAMP)
        glowRadius = w / COUNT * 1.1f
        PALETTE.forEachIndexed { i, c ->
            glows[i].shader = RadialGradient(0f, 0f, glowRadius, intArrayOf(Cinema.alpha(c, 0.32f), Cinema.alpha(c, 0f)), null, Shader.TileMode.CLAMP)
        }
        rim.strokeWidth = w / 380f
    }

    override fun onDraw(canvas: Canvas) {
        val h = height.toFloat()
        val slot = width / COUNT.toFloat()
        val archW = slot * 0.84f
        val radius = archW / 2
        val tops = FloatArray(COUNT)
        for (i in 0 until COUNT) {
            val breathe = 0.035f * sin(2 * PI * timeMs / (9_000.0 + i * 1_700.0) + i * 1.3).toFloat()
            val lift = (BASE + breathe + (TOP - BASE) * levels[i]).coerceIn(0.08f, 0.96f)
            tops[i] = h - h * lift
        }
        // Light spills above each arch first, so the arches sit in front of it.
        for (i in 0 until COUNT) {
            val cx = i * slot + slot / 2
            val p = glows[colour(i, 0)]
            p.alpha = (255 * (0.2f + 0.8f * levels[i].coerceIn(0f, 1f))).toInt()
            canvas.save(); canvas.translate(cx, tops[i] + radius)
            canvas.drawCircle(0f, 0f, glowRadius, p)
            canvas.restore()
        }
        for (i in 0 until COUNT) {
            val left = i * slot + (slot - archW) / 2
            val top = tops[i]
            for (j in 0 until BANDS) {
                val inset = j * archW * 0.12f
                // The arch runs below the floor, so only its top is rounded.
                val r = radius - inset
                canvas.drawRoundRect(left + inset, top + inset, left + archW - inset, h + archW, r, r, rings[colour(i, j)])
            }
            val inset = BANDS * archW * 0.12f
            canvas.drawRoundRect(left + inset, top + inset, left + archW - inset, h + archW, radius - inset, radius - inset, core)
            rim.alpha = (255 * 0.35f * levels[i].coerceIn(0f, 1f)).toInt()
            if (rim.alpha > 2) {
                arc.set(left, top, left + archW, top + archW)
                canvas.drawArc(arc, 200f, 140f, false, rim)
            }
        }
    }

    private fun colour(column: Int, band: Int) = (column + band * 2 + shift) % PALETTE.size

    companion object {
        const val COUNT = 6
        private const val BANDS = 3
        private const val BASE = 0.2f
        private const val TOP = 0.9f
        private val PALETTE = intArrayOf(
            0xFFE0A93A.toInt(), 0xFFEB8A56.toInt(), 0xFFC4532A.toInt(), 0xFF9A3420.toInt(), 0xFF6F7F3C.toInt(), 0xFF3AA597.toInt()
        )
    }
}
