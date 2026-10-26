package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.view.View

/**
 * Bars mirrored about the middle, in dim warm colours. A silent band draws nothing, so no line or edge sits still
 * on the panel.
 */
class SpectrumView(context: Context, bands: Int) : View(context) {
    private val levels = FloatArray(bands)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bar = RectF()

    fun show(next: FloatArray) {
        System.arraycopy(next, 0, levels, 0, minOf(next.size, levels.size))
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        paint.shader = LinearGradient(0f, 0f, 0f, h / 2f, intArrayOf(LOUD, QUIET), null, Shader.TileMode.MIRROR)
    }

    override fun onDraw(canvas: Canvas) {
        val n = levels.size
        val slot = width / n.toFloat()
        val gap = slot * 0.28f
        val mid = height / 2f
        for (i in 0 until n) {
            val half = levels[i] * mid
            if (half < 1f) continue
            bar.set(i * slot + gap / 2, mid - half, (i + 1) * slot - gap / 2, mid + half)
            canvas.drawRoundRect(bar, slot / 3, slot / 3, paint)
        }
    }

    companion object {
        private const val LOUD = 0x8CD98A4E.toInt()
        private const val QUIET = 0x33A0502E
    }
}
