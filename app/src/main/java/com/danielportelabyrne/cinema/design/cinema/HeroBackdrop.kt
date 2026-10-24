package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.view.View
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A backdrop picture for a featured title or a title page: centre-cropped into the view (biased by [focusX]: 0.5
 * centre, 1 right), with its left part faded to nothing ([fadeTo], the share of the width the fade spans, `.mk`).
 *
 * The crop and the fade are a shader (the picture, masked by a gradient) drawn by the GPU, so a new picture costs no
 * CPU painting and no full-screen bitmap. A new picture cross-fades over the one before; nothing is blanked first.
 */
class HeroBackdrop(context: Context, private val focusX: Float = 0.5f, private val fadeTo: Float = 0.38f) : View(context) {
    private class Layer(val bitmap: Bitmap) { val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG) }
    private var current: Layer? = null
    private var previous: Layer? = null
    private var mix = 1f
    private var fade: android.animation.ValueAnimator? = null

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    /**
     * Shows [bitmap]: cross-fading from the current picture when there is one on screen, at once when the view is not
     * yet on screen (a page being built from pictures already in memory). Null clears it.
     */
    fun show(bitmap: Bitmap?) {
        if (bitmap != null && bitmap === current?.bitmap) return
        fade?.cancel(); fade = null
        if (bitmap == null) { current = null; previous = null; mix = 1f; invalidate(); return }
        val next = Layer(bitmap).also { shade(it) }
        val animate = isAttachedToWindow && isShown && width > 0 && com.danielportelabyrne.cinema.design.Motion.animationsOn(this)
        previous = if (animate) current else null
        current = next
        if (!animate) { mix = 1f; invalidate(); return }
        mix = 0f
        fade = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            addUpdateListener { mix = it.animatedValue as Float; invalidate() }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { previous = null; mix = 1f; invalidate() }
            })
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { current?.let { shade(it) }; previous?.let { shade(it) } }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); fade?.cancel(); fade = null; previous = null; mix = 1f }

    /** The crop (a local matrix on the picture) and the left fade (the picture masked by a gradient), for this size. */
    private fun shade(layer: Layer) {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        val src = layer.bitmap
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val picture = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply {
                setScale(scale, scale)
                postTranslate((w - src.width * scale) * focusX, (h - src.height * scale) * 0.5f)
            })
        }
        layer.paint.shader = if (fadeTo > 0f)
            ComposeShader(picture, LinearGradient(0f, 0f, w * fadeTo, 0f, 0, -0x1000000, Shader.TileMode.CLAMP), PorterDuff.Mode.DST_IN)
        else picture
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        previous?.let { it.paint.alpha = 255; canvas.drawRect(0f, 0f, w, h, it.paint) }
        current?.let { it.paint.alpha = (mix * 255).toInt(); canvas.drawRect(0f, 0f, w, h, it.paint) }
    }
}

/**
 * Two fades of the Cinema ground (one across, one down) in one pass (`bdgrad` in the mockups). Each fade is a list of
 * (position, alpha) stops over [Cinema.BG].
 */
class Scrim(private val across: List<Pair<Float, Float>>, private val down: List<Pair<Float, Float>>) : Drawable() {
    private val paint = Paint().apply { isDither = true }
    override fun onBoundsChange(b: android.graphics.Rect) {
        fun grad(stops: List<Pair<Float, Float>>, x1: Float, y1: Float) = LinearGradient(b.left.toFloat(), b.top.toFloat(), x1, y1,
            stops.map { Cinema.alpha(Cinema.BG, it.second) }.toIntArray(), stops.map { it.first }.toFloatArray(), Shader.TileMode.CLAMP)
        paint.shader = ComposeShader(grad(across, b.right.toFloat(), b.top.toFloat()), grad(down, b.left.toFloat(), b.bottom.toFloat()), PorterDuff.Mode.SRC_OVER)
    }
    override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

    companion object {
        /** The featured title's fades (`bdgrad(.98)`). */
        fun hero() = Scrim(listOf(0f to 0.98f, 0.34f to 0.85f, 0.66f to 0.25f, 1f to 0.1f), listOf(0f to 0.55f, 0.22f to 0f, 0.5f to 0f, 1f to 0.96f))
        /** A title page's fades (`detail`). */
        fun detail() = Scrim(listOf(0f to 0.96f, 0.38f to 0.8f, 0.72f to 0.3f, 1f to 0.15f), listOf(0f to 0.35f, 0.25f to 0f, 0.48f to 0f, 0.86f to 0.96f, 1f to 0.96f))
    }
}
