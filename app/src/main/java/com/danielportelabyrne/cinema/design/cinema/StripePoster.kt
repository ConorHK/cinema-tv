package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A title page's poster with the stacked stripe shadow (`10px 10px 0 #f6bf33, 20px … #f08a25, 30px … #e2562b,
 * 40px … #c2281d`): four solid offset copies of its shape fanning down and right, the poster on top. The bands are
 * drawn past the view's bounds, so its parents must not clip children.
 */
class StripePoster(context: Context, radiusPx: Float = 22f, private val stepPx: Float = 10f) : FrameLayout(context) {
    val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val radius = Cinema.pxf(context, radiusPx)
    private val step = Cinema.pxf(context, stepPx)
    private val band = Paint(Paint.ANTI_ALIAS_FLAG)
    private val box = RectF()
    private val bands = intArrayOf(Cinema.STRIPE_SAFFRON, Cinema.STRIPE_ORANGE, Cinema.STRIPE_TERRA, Cinema.STRIPE_RED)
    private val face = FrameLayout(context).apply {
        setBackgroundColor(Cinema.PLACEHOLDER)
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) } }
        clipToOutline = true
    }

    init {
        clipChildren = false; clipToPadding = false
        setWillNotDraw(false)
        face.addView(image, LayoutParams(-1, -1))
        addView(face, LayoutParams(-1, -1))
    }

    override fun dispatchDraw(canvas: Canvas) {
        // Furthest band first, so saffron (the nearest) ends up just under the poster.
        for (i in bands.indices.reversed()) {
            val d = step * (i + 1)
            band.color = bands[i]
            box.set(d, d, width + d, height + d)
            canvas.drawRoundRect(box, radius, radius, band)
        }
        super.dispatchDraw(canvas)
    }
}
