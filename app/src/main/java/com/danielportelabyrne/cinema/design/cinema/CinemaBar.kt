package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion

/**
 * A Cinema progress bar (`.dl .bar`): a faint cream track with a stripe-set fill. With [progress] null and
 * [indeterminate] set, a soft-edged striped segment sweeps along the track ("Finding sources", "Finding peers");
 * with neither, it is just the track, never a made-up figure.
 */
class CinemaBar(context: Context, private val trackAlpha: Float = 0.08f) : View(context) {
    var progress: Float? = null
        set(value) { field = value?.coerceIn(0f, 1f); invalidate() }
    var indeterminate = false
        set(value) { field = value; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cinema.alpha(Cinema.CREAM, trackAlpha) }
    private val fill = StripeDrawable(horizontalBands = true, radius = 0f)
    private val mask = Paint()
    private val box = RectF()

    init { isFocusable = false }

    override fun onDraw(canvas: Canvas) {
        val r = height / 2f
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(box, r, r, track)
        val p = progress
        if (p != null) {
            if (p <= 0f) return
            val w = maxOf(height.toFloat(), width * p)
            canvas.save(); clip(canvas, 0f, w, r)
            fill.setBounds(0, 0, w.toInt(), height); fill.draw(canvas)
            canvas.restore()
            return
        }
        if (!indeterminate) return
        // A segment 22% of the track wide, sweeping left to right every 1.6 s, its ends faded out.
        val seg = width * 0.22f
        val t = if (Motion.animationsOn(this)) (SystemClock.uptimeMillis() % 1600L) / 1600f else 0.3f
        val x = -seg + (width + seg) * t
        val layer = canvas.saveLayer(0f, 0f, width.toFloat(), height.toFloat(), null)
        clip(canvas, 0f, width.toFloat(), r)
        fill.setBounds(x.toInt(), 0, (x + seg).toInt(), height); fill.draw(canvas)
        mask.shader = LinearGradient(x, 0f, x + seg, 0f, intArrayOf(0, -0x1000000, -0x1000000, 0), floatArrayOf(0f, 0.3f, 0.7f, 1f), Shader.TileMode.CLAMP)
        mask.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        canvas.drawRect(x, 0f, x + seg, height.toFloat(), mask)
        canvas.restoreToCount(layer)
        if (isAttachedToWindow && isShown) postInvalidateOnAnimation()
    }

    private fun clip(canvas: Canvas, left: Float, right: Float, r: Float) {
        canvas.clipPath(android.graphics.Path().apply { addRoundRect(RectF(left, 0f, right, height.toFloat()), r, r, android.graphics.Path.Direction.CW) })
    }
}
