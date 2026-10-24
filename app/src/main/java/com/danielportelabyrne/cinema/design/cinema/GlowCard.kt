package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A focusable glass card (download requests, trending searches). At rest a translucent cream fill; focused, a
 * brighter fill with a soft cream bloom behind it. No outline, per the Cinema language. The bloom reaches past the
 * card's bounds, so parents must not clip children.
 */
class GlowCard(context: Context, private val radiusPx: Float) : LinearLayout(context) {
    private val radius = Cinema.pxf(context, radiusPx)
    private val rest = Cinema.glass(context, radiusPx, Cinema.GLASS)
    private val lit = Cinema.glass(context, radiusPx, 0x29FFF2DE)   // .16
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Cinema.alpha(Cinema.CREAM, 0.16f)
        if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 30), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val box = RectF()
    var onFocus: ((Boolean) -> Unit)? = null

    init {
        isFocusable = true; isClickable = true
        clipChildren = false; clipToPadding = false
        setWillNotDraw(false)
        background = rest
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        background = if (gainFocus) lit else rest
        invalidate()
        onFocus?.invoke(gainFocus)
    }

    override fun draw(canvas: Canvas) {
        if (isFocused && width > 0 && height > 0) {
            // Painted once per size (Glows), not blurred live every frame.
            val g = Glows.roundRect(width, height, radius, Cinema.pxf(context, 30), glow.color)
            canvas.drawBitmap(g.bitmap, -g.pad.toFloat(), -g.pad.toFloat(), null)
        }
        super.draw(canvas)
    }
}
