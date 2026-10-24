package com.danielportelabyrne.cinema.design

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.provider.Settings.Global
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.R

/**
 * Centralised visual tokens. The whole app speaks one language:
 *
 *  - resting surfaces are dark brown, with no outline,
 *  - the focused item is always lit: solid ivory with ink content
 *    (or, for round app icons, an ivory ring around the disc),
 *  - everything is either a soft rounded panel, a pill, or a circle.
 */
object AppTheme {

    private const val FOCUS_SCALE = 1.05f
    private const val FOCUS_DURATION_MS = 150L
    private val interpolator = DecelerateInterpolator()

    // --- Surfaces -----------------------------------------------------------

    /** Resting surface: dark brown, no outline. */
    fun tileBackground(context: Context, radiusPx: Float): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(ContextCompat.getColor(context, R.color.panel_bg))
        }

    /** Focused surface: solid, lit ivory. */
    fun tileBackgroundFocused(context: Context, radiusPx: Float): Drawable =
        solidRect(context, radiusPx, R.color.ivory)

    /** "Picked up for reorder": solid butter. */
    fun tileBackgroundPicked(context: Context, radiusPx: Float): Drawable =
        solidRect(context, radiusPx, R.color.butter)

    /** Round header/list controls: focused = solid ivory disc. */
    fun circleBackgroundFocused(context: Context): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.ivory))
        }

    /** Lighter brown pill behind the current nav tab. */
    fun selectedPill(context: Context, radiusPx: Float): Drawable =
        solidRect(context, radiusPx, R.color.btn_brown_on)

    /** Ring drawn around a round app icon when focused (or picked up). */
    fun iconRing(context: Context, colorRes: Int): Drawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(context.resources.getDimensionPixelSize(R.dimen.focus_ring_width), ContextCompat.getColor(context, colorRes))
        }

    // --- Design-system surfaces (2026-09-22) -----------------------------------

    fun px(context: Context, dimenRes: Int): Int = context.resources.getDimensionPixelSize(dimenRes)

    /** A rounded surface in one of the role colours; radius from the radius scale. */
    fun surface(context: Context, colorRes: Int = R.color.surface, radiusRes: Int = R.dimen.radius_card): GradientDrawable =
        solidRect(context, px(context, radiusRes).toFloat(), colorRes)

    /**
     * What lights a round icon: a soft ivory halo glowing out from the disc's edge. The view carrying it
     * needs `focus_halo` of padding around the disc.
     */
    fun iconMat(context: Context): Drawable = HaloDrawable(ContextCompat.getColor(context, R.color.focus), px(context, R.dimen.focus_halo).toFloat())

    /** The mat that lights an image card (Continue watching, paintings, posters, prints): ivory frame, ink hairline inside. */
    fun imageMat(context: Context, radiusPx: Float): Drawable {
        val ring = px(context, R.dimen.focus_ring_width)
        val outer = GradientDrawable().apply { cornerRadius = radiusPx; setStroke(ring, ContextCompat.getColor(context, R.color.focus)) }
        val inner = GradientDrawable().apply { cornerRadius = (radiusPx - ring).coerceAtLeast(0f); setStroke(ring / 2, ContextCompat.getColor(context, R.color.on_focus)) }
        return android.graphics.drawable.LayerDrawable(arrayOf(outer, inner)).apply { setLayerInset(1, ring, ring, ring, ring) }
    }

    /**
     * The focus ring for paper objects (recipe cards): an ivory ring standing [gapPx] clear of the object's
     * edge, drawn in its overlay so it can sit outside it. On cream paper an inset ring would vanish, and an
     * ink hairline reads as a heavy black border. Returned at alpha 0 for [fadeDrawable].
     */
    fun outsetRing(view: View, radiusPx: Float, gapPx: Int): Drawable {
        val ring = GradientDrawable().apply {
            cornerRadius = radiusPx + gapPx
            setStroke(px(view.context, R.dimen.focus_ring_width), ContextCompat.getColor(view.context, R.color.focus))
            alpha = 0
        }
        view.overlay.add(ring)
        view.addOnLayoutChangeListener { v, _, _, _, _, _, _, _, _ -> ring.setBounds(-gapPx, -gapPx, v.width + gapPx, v.height + gapPx) }
        return ring
    }

    /**
     * The one focus rule for image objects: an ivory mat fades in over the view's edge, plus the shared lift.
     * [extra] runs on every change (scroll into view, hints...).
     */
    fun bindImageFocus(view: View, radiusPx: Float, scale: Float = FOCUS_SCALE, extra: ((Boolean) -> Unit)? = null) {
        val mat = imageMat(view.context, radiusPx).apply { alpha = 0 }
        view.foreground = mat
        view.setOnFocusChangeListener { v, hasFocus ->
            fadeDrawable(v, mat, hasFocus)
            animateFocus(v, hasFocus, scale)
            extra?.invoke(hasFocus)
        }
    }

    /** Crossfades [drawable]'s alpha in or out with the shared focus timings. */
    fun fadeDrawable(view: View, drawable: Drawable, show: Boolean) {
        val target = if (show) 255 else 0
        if (!Motion.animationsOn(view)) { drawable.alpha = target; view.invalidate(); return }
        android.animation.ValueAnimator.ofInt(drawable.alpha, target).apply {
            duration = if (show) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
            interpolator = Motion.SETTLE
            addUpdateListener { drawable.alpha = it.animatedValue as Int; view.invalidate() }
            start()
        }
    }

    private fun solidRect(context: Context, radiusPx: Float, colorRes: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radiusPx
            setColor(ContextCompat.getColor(context, colorRes))
        }

    // --- Panel focus binding -------------------------------------------------

    /**
     * Applies the shared focus treatment to a panel-like view: swaps the
     * surface and flips every text/icon inside to ink while lit, restoring
     * the original colours afterwards.
     */
    fun bindPanelFocus(view: View, rest: Drawable, focused: Drawable, scale: Float = FOCUS_SCALE, extra: ((Boolean) -> Unit)? = null) {
        // Both surfaces live in one crossfading drawable, so a highlight melts in and out rather than switching.
        val fade = android.graphics.drawable.TransitionDrawable(arrayOf(rest, focused)).apply { isCrossFadeEnabled = true }
        view.background = fade
        view.setOnFocusChangeListener { v, hasFocus ->
            if (Motion.animationsOn(v)) {
                if (hasFocus) fade.startTransition(Motion.FOCUS_IN_MS.toInt()) else fade.reverseTransition(Motion.FOCUS_OUT_MS.toInt())
            } else {
                fade.resetTransition()
                if (hasFocus) fade.startTransition(0)
            }
            recolor(v, hasFocus)
            animateFocus(v, hasFocus, scale)
            extra?.invoke(hasFocus)
        }
    }

    private fun recolor(view: View, focused: Boolean) {
        val ink = ContextCompat.getColor(view.context, R.color.ink)
        if (view is TextView) {
            // The resting colour is remembered across focus changes: re-reading it while a tween back is still
            // running would capture a half-lit colour and leave the label stuck there.
            val saved = view.getTag(R.id.orig_text_color) as? ColorStateList
            val tweening = (view.getTag(R.id.motion_text) as? android.animation.Animator)?.isRunning == true
            if (focused) {
                val rest = if (saved != null && tweening) saved else view.textColors
                view.setTag(R.id.orig_text_color, rest)
                val alpha = Color.alpha(rest.defaultColor)
                Motion.tweenTextColor(view, Color.argb(alpha, Color.red(ink), Color.green(ink), Color.blue(ink)))
            } else if (saved != null) {
                Motion.tweenTextColor(view, saved.defaultColor)
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) recolor(view.getChildAt(i), focused)
        }
    }

    // --- Motion -------------------------------------------------------------

    /**
     * Restrained focus motion: a small scale-up, honouring the system's
     * "remove animations" accessibility setting by collapsing the duration
     * to zero rather than ignoring it.
     */
    fun animateFocus(view: View, focused: Boolean, focusScale: Float = FOCUS_SCALE) {
        val scale = if (focused) focusScale else 1f
        val duration = if (animationsDisabled(view)) 0L else if (focused) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS
        view.animate()
            .scaleX(scale)
            .scaleY(scale)
            .translationZ(if (focused) 18f else 0f)
            .setDuration(duration)
            .setInterpolator(Motion.SETTLE)
            .start()
    }

    private fun animationsDisabled(view: View): Boolean = try {
        Global.getFloat(view.context.contentResolver, Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    } catch (e: Exception) {
        false
    }
}

/**
 * A soft glow around a circle that fills the bounds minus [halo] on every side: bright right at the
 * disc's edge, fading to nothing [halo] further out. Alpha is animatable like any drawable.
 */
private class HaloDrawable(private val color: Int, private val halo: Float) : Drawable() {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var alphaValue = 255

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        val r = minOf(bounds.width(), bounds.height()) / 2f
        if (r <= 0f) return
        val disc = (r - halo).coerceAtLeast(0f) / r
        fun a(f: Float) = android.graphics.Color.argb((255 * f).toInt(), android.graphics.Color.red(color), android.graphics.Color.green(color), android.graphics.Color.blue(color))
        paint.shader = android.graphics.RadialGradient(bounds.exactCenterX(), bounds.exactCenterY(), r,
            // Full strength at the disc's edge, then a long, gentle fall-off (no visible edge anywhere).
            intArrayOf(a(1f), a(1f), a(0.82f), a(0.52f), a(0.24f), a(0.07f), a(0f)),
            floatArrayOf(0f, disc, disc + (1 - disc) * 0.15f, disc + (1 - disc) * 0.35f, disc + (1 - disc) * 0.58f, disc + (1 - disc) * 0.8f, 1f),
            android.graphics.Shader.TileMode.CLAMP)
    }

    override fun draw(canvas: android.graphics.Canvas) {
        val b = bounds
        paint.alpha = alphaValue
        canvas.drawCircle(b.exactCenterX(), b.exactCenterY(), minOf(b.width(), b.height()) / 2f, paint)
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha; invalidateSelf() }
    override fun getAlpha(): Int = alphaValue
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
