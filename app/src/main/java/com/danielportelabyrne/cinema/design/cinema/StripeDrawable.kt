package com.danielportelabyrne.cinema.design.cinema

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.Drawable
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The stripe set as even bands inside a rounded rectangle. [horizontalBands] stacks the bands top to bottom (the
 * eyebrow mark, hero indicator, progress bars); otherwise they sit side by side (the row-title marker, the rail bar).
 * [fraction] fills only the leading part, for a progress bar; the track is drawn by the caller.
 */
class StripeDrawable(
    private val horizontalBands: Boolean,
    private val radius: Float,
    private val colors: IntArray = Cinema.STRIPES,
    fraction: Float = 1f
) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val box = RectF()

    var fraction: Float = fraction
        set(value) { field = value.coerceIn(0f, 1f); rebuild(bounds); invalidateSelf() }

    override fun onBoundsChange(bounds: Rect) = rebuild(bounds)

    private fun rebuild(b: Rect) {
        val w = b.width() * fraction
        box.set(b.left.toFloat(), b.top.toFloat(), b.left + maxOf(w, if (fraction > 0f) b.height().toFloat() else 0f).coerceAtMost(b.width().toFloat()), b.bottom.toFloat())
        clip.reset()
        clip.addRoundRect(box, radius, radius, Path.Direction.CW)
    }

    override fun draw(canvas: Canvas) {
        if (box.width() <= 0f) return
        canvas.save()
        canvas.clipPath(clip)
        val n = colors.size
        for (i in 0 until n) {
            paint.color = colors[i]
            if (horizontalBands) {
                val h = box.height() / n
                canvas.drawRect(box.left, box.top + i * h, box.right, box.top + (i + 1) * h + 0.5f, paint)
            } else {
                val w = box.width() / n
                canvas.drawRect(box.left + i * w, box.top, box.left + (i + 1) * w + 0.5f, box.bottom, paint)
            }
        }
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = PixelFormat.TRANSLUCENT
}
