package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.Shader
import android.os.Build
import android.view.View

/**
 * The spectrum as a smooth glowing ridge over a faint reflection. Colours sweep slowly across it, and silence draws
 * nothing, so no edge stays lit on an OLED panel.
 */
class SpectrumView(context: Context, bands: Int) : View(context) {
    private val levels = FloatArray(bands)
    private val shown = FloatArray(bands)
    private val ridge = Path()
    private val sweep = Matrix()
    private var colours: LinearGradient? = null

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val reflection = Paint(Paint.ANTI_ALIAS_FLAG)

    /** [phase] 0..1 slides the colours along. */
    fun show(next: FloatArray, phase: Float) {
        System.arraycopy(next, 0, levels, 0, minOf(next.size, levels.size))
        colours?.let { sweep.setTranslate(phase * width * 2, 0f); it.setLocalMatrix(sweep) }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val base = h * BASELINE
        val c = LinearGradient(0f, 0f, w.toFloat(), 0f, PALETTE, null, Shader.TileMode.MIRROR)
        colours = c
        val fade = LinearGradient(0f, 0f, 0f, base, 0xF0FFFFFF.toInt(), 0x08FFFFFF, Shader.TileMode.CLAMP)
        // Drawn flipped, so it fades from the line toward local y = 0.
        val down = LinearGradient(0f, base, 0f, 0f, 0x40FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)
        // Two gradients in one shader need API 28 on the GPU; older TVs get plain colour with paint alpha.
        if (Build.VERSION.SDK_INT >= 28) {
            fill.shader = ComposeShader(c, fade, PorterDuff.Mode.DST_IN)
            reflection.shader = ComposeShader(c, down, PorterDuff.Mode.DST_IN)
        } else {
            fill.shader = c; fill.alpha = 110
            reflection.shader = c; reflection.alpha = 30
        }
        glow.shader = c; glow.alpha = 40; glow.strokeWidth = h * 0.035f
        edge.shader = c; edge.alpha = 235; edge.strokeWidth = h * 0.007f
    }

    override fun onDraw(canvas: Canvas) {
        val n = levels.size
        // A light blur across neighbours turns separate bands into one shape.
        for (i in 0 until n) shown[i] = (levels[maxOf(0, i - 1)] + 2 * levels[i] + levels[minOf(n - 1, i + 1)]) / 4f
        if (shown.all { it < 0.01f }) return
        val base = height * BASELINE
        val step = width / (n - 1f)
        fun y(i: Int) = base - shown[i.coerceIn(0, n - 1)] * base * 0.96f
        ridge.reset(); ridge.moveTo(0f, y(0))
        for (i in 0 until n - 1) {
            // Catmull-Rom through the band tops, as cubic Beziers.
            val x0 = i * step; val x1 = (i + 1) * step
            ridge.cubicTo(x0 + step / 3, y(i) + (y(i + 1) - y(i - 1)) / 6, x1 - step / 3, y(i + 1) - (y(i + 2) - y(i)) / 6, x1, y(i + 1))
        }
        canvas.drawPath(ridge, glow)
        canvas.drawPath(ridge, edge)
        ridge.lineTo(width.toFloat(), base); ridge.lineTo(0f, base); ridge.close()
        canvas.drawPath(ridge, fill)
        // The reflection: the same shape, flipped and squashed below the line.
        canvas.save()
        canvas.scale(1f, -0.35f, 0f, base)
        canvas.drawPath(ridge, reflection)
        canvas.restore()
    }

    companion object {
        private const val BASELINE = 0.74f
        private val PALETTE = intArrayOf(0xFFD4623A.toInt(), 0xFFEB8A56.toInt(), 0xFFEDBE56.toInt(), 0xFF5FB3A6.toInt(), 0xFFD4623A.toInt())
    }
}
