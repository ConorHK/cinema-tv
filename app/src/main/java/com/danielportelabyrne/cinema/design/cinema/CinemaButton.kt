package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The Cinema pill button (`.btn`): 76 mockup pixels tall, fully rounded, icon then label.
 *
 * Focus is cream: the focused button fills cream with dark text and blooms a soft glow behind it, and every other
 * button rests as glass. The one exception is [Kind.TERRA] ("Queue download"), which keeps its terracotta gradient so
 * the page's call to action reads even when focus is elsewhere; focused, it takes a ring in its own warmer shade.
 * The glow and ring are drawn outside the view's bounds, so its parents must not clip children.
 */
/** [small] is `.btn.sm`: 60 tall (the caller sets the height), 21 px text, tighter padding. */
class CinemaButton(context: Context, kind: Kind, label: CharSequence, iconRes: Int? = null, private val small: Boolean = false) : LinearLayout(context) {
    enum class Kind { PRIMARY, TERRA, GLASS, DANGER }

    private companion object { const val RING = 0xFFF5A97E.toInt() }

    val label: TextView = Cinema.text(context, label, if (small) 21 else 25, Cinema.CREAM, weight = 600).apply { maxLines = 1 }
    private val icon: ImageView? = iconRes?.let { Cinema.icon(context, it, Cinema.CREAM) }
    // Focus glow: a soft blurred bloom behind the cream-lit buttons.
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 22), android.graphics.BlurMaskFilter.Blur.NORMAL)
    }
    private val haloBox = RectF()
    private val haloWidth = Cinema.pxf(context, 4)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    var kind: Kind = kind
        set(value) { field = value; restyle() }

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER
        isFocusable = true; isClickable = true
        setWillNotDraw(false)
        if (small) setPadding(Cinema.px(context, if (iconRes != null) 22 else 28), 0, Cinema.px(context, 28), 0)
        else setPadding(Cinema.px(context, if (iconRes != null) 30 else 38), 0, Cinema.px(context, 38), 0)
        val iconSize = Cinema.px(context, if (small) 24 else 30)
        icon?.let { addView(it, LayoutParams(iconSize, iconSize).apply { marginEnd = Cinema.px(context, if (small) 10 else 14) }) }
        addView(this.label)
        contentDescription = label
        restyle()
    }

    fun setText(value: CharSequence) { label.text = value; contentDescription = value }

    fun setIcon(res: Int) { icon?.setImageResource(res) }

    fun showIcon(show: Boolean) { icon?.visibility = if (show) VISIBLE else GONE }

    override fun setEnabled(enabled: Boolean) { super.setEnabled(enabled); alpha = if (enabled) 1f else 0.6f }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        restyle()
    }

    private fun restyle() {
        val rMock = if (small) 30f else 38f
        val r = Cinema.pxf(context, rMock)
        val lit = isFocused
        val (fill, text) = when {
            kind == Kind.TERRA -> GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFFEB8A56.toInt(), 0xFFD4623A.toInt())).apply { cornerRadius = r } to 0xFF1D0D05.toInt()
            lit -> GradientDrawable().apply { cornerRadius = r; setColor(Cinema.CREAM) } to if (kind == Kind.DANGER) 0xFF8A2A12.toInt() else Cinema.INK
            else -> Cinema.glass(context, rMock, if (kind == Kind.PRIMARY) Cinema.GLASS_STRONG else Cinema.GLASS) to if (kind == Kind.DANGER) Cinema.DANGER_TEXT else Cinema.CREAM
        }
        background = fill
        label.setTextColor(text)
        icon?.setColorFilter(text)
        halo.color = if (kind == Kind.TERRA) Cinema.alpha(Cinema.TERRA, 0.55f) else Cinema.alpha(Cinema.CREAM, 0.32f)
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        if (isFocused && kind == Kind.TERRA) { super.draw(canvas); drawRing(canvas); return }
        if (isFocused && width > 0 && height > 0) {
            // Painted once per size and colour (Glows), not blurred live every frame.
            val hw = haloWidth.toInt()
            val g = Glows.roundRect(width + 2 * hw, height + 2 * hw, height / 2f + hw, Cinema.pxf(context, 22), halo.color)
            canvas.drawBitmap(g.bitmap, (-hw - g.pad).toFloat(), (-hw - g.pad).toFloat(), null)
        }
        super.draw(canvas)
    }

    /** 4 px of light terracotta, then 5 px of terracotta at 30% beyond it. */
    private fun drawRing(canvas: Canvas) {
        val r = height / 2f
        val o2 = Cinema.pxf(context, 4) + Cinema.pxf(context, 5) / 2f
        ring.color = Cinema.alpha(Cinema.TERRA, 0.3f); ring.strokeWidth = Cinema.pxf(context, 5)
        canvas.drawRoundRect(-o2, -o2, width + o2, height + o2, r + o2, r + o2, ring)
        val o1 = Cinema.pxf(context, 2)
        ring.color = RING; ring.strokeWidth = Cinema.pxf(context, 4)
        canvas.drawRoundRect(-o1, -o1, width + o1, height + o1, r + o1, r + o1, ring)
    }
}
