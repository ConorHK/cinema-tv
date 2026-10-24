package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.search.Qwerty
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion

/**
 * The in-app D-pad keyboard (`22-search`): a number row, three staggered letter rows, and Clear / Space / Delete.
 * Keys are glass caps; the focused one turns cream, grows to 1.12 and blooms. Laid out from the mockup's key grid
 * (90 x 84 keys, 98 apart). [onKey] gets every press; the text itself is kept by the caller.
 */
class QwertyKeyboard(context: Context, private val onKey: (Qwerty.Key) -> Unit) : FrameLayout(context) {
    /** Every key, for focusing the first one. */
    val keys = ArrayList<KeyCap>()

    init {
        clipChildren = false; clipToPadding = false
        Qwerty.ROWS.forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                val key = KeyCap(context, ch.toString(), null, wide = false).apply { setOnClickListener { onKey(Qwerty.Key.Char(ch)) } }
                add(key, Cinema.px(context, c * 98 + Qwerty.ROW_OFFSETS[r] * 98), Cinema.px(context, r * 98), Cinema.px(context, 90))
            }
        }
        val y = Cinema.px(context, 4 * 98)
        add(KeyCap(context, "Clear", R.drawable.ic_cin_x, wide = true).apply { setOnClickListener { onKey(Qwerty.Key.Clear) } }, 0, y, Cinema.px(context, 190))
        add(KeyCap(context, "Space", null, wide = true).apply { setOnClickListener { onKey(Qwerty.Key.Space) } }, Cinema.px(context, 202), y, Cinema.px(context, 580))
        add(KeyCap(context, "Delete", R.drawable.ic_cin_bksp, wide = true).apply { setOnClickListener { onKey(Qwerty.Key.Delete) } }, Cinema.px(context, 794), y, Cinema.px(context, 186))
    }

    private fun add(key: KeyCap, x: Int, y: Int, w: Int) {
        keys += key
        addView(key, LayoutParams(w, Cinema.px(context, 84)).apply { leftMargin = x; topMargin = y })
    }

    /** One key cap. */
    class KeyCap(context: Context, label: String, iconRes: Int?, private val wide: Boolean) : LinearLayout(context) {
        private val text: TextView = if (wide) Cinema.text(context, label, 20, Cinema.MUTED, tracking = 0.05f) else Cinema.text(context, label, 30, 0xFFE6DAC8.toInt())
        private val icon = iconRes?.let { Cinema.icon(context, it, Cinema.MUTED) }
        private val radius = Cinema.pxf(context, 18)
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Cinema.alpha(Cinema.CREAM, 0.22f)
            if (android.os.Build.VERSION.SDK_INT >= 28) maskFilter = android.graphics.BlurMaskFilter(Cinema.pxf(context, 18), android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
        private val box = RectF()

        init {
            orientation = HORIZONTAL; gravity = Gravity.CENTER
            isFocusable = true; isClickable = true; contentDescription = label
            clipChildren = false; setWillNotDraw(false)
            icon?.let { addView(it, LayoutParams(Cinema.px(context, 26), Cinema.px(context, 26)).apply { marginEnd = Cinema.px(context, 8) }) }
            addView(text)
            restyle(false)
        }

        private fun restyle(lit: Boolean) {
            background = Cinema.glass(context, 18f, if (lit) Cinema.CREAM else Cinema.GLASS)
            val c = if (lit) Cinema.INK else if (wide) Cinema.MUTED else 0xFFE6DAC8.toInt()
            text.setTextColor(c); icon?.setColorFilter(c)
            Cinema.weight(text, if (wide) 20 else 30, serif = false, weight = if (lit) 600 else 400)
        }

        override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            restyle(gainFocus)
            val s = if (gainFocus) 1.12f else 1f
            if (Motion.animationsOn(this)) animate().scaleX(s).scaleY(s).translationZ(if (gainFocus) Cinema.pxf(context, 12) else 0f).setDuration(if (gainFocus) Motion.FOCUS_IN_MS else Motion.FOCUS_OUT_MS).setInterpolator(Motion.SETTLE).start()
            else { scaleX = s; scaleY = s }
            invalidate()
        }

        override fun draw(canvas: Canvas) {
            if (isFocused && android.os.Build.VERSION.SDK_INT >= 28) { box.set(0f, 0f, width.toFloat(), height.toFloat()); canvas.drawRoundRect(box, radius, radius, glow) }
            super.draw(canvas)
        }
    }
}
