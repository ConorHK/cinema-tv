package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Outline
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.design.Cinema

/**
 * A Continue watching card: a 16:9 still with a saffron stripe along its foot showing how far in it is, then the title
 * and what is left. Focused, the still grows and the title turns cream, as the other cards do.
 * Parents must not clip children, as the grown still reaches past the card's bounds.
 */
class ResumeCard(context: Context) : LinearLayout(context) {
    val image = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    val title = Cinema.text(context, "", 23, Cinema.CARD_TITLE, weight = 500).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    val meta = Cinema.text(context, "", 18, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    var onFocus: ((Boolean) -> Unit)? = null

    private val stillW = Cinema.px(context, 400)
    private val stillH = Cinema.px(context, 225)
    private val stripe = Cinema.px(context, 6)
    private val radius = Cinema.pxf(context, 18)
    private val track = View(context).apply { setBackgroundColor(0x66000000) }
    private val fill = View(context).apply { setBackgroundColor(Cinema.SAFFRON); visibility = GONE }
    private val still = FrameLayout(context)

    init {
        orientation = VERTICAL
        isFocusable = true
        isClickable = true
        clipChildren = false
        clipToPadding = false
        still.clipToOutline = true
        still.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, radius) }
        }
        still.addView(image, FrameLayout.LayoutParams(-1, -1))
        still.addView(track, FrameLayout.LayoutParams(-1, stripe, Gravity.BOTTOM))
        still.addView(fill, FrameLayout.LayoutParams(0, stripe, Gravity.BOTTOM or Gravity.START))
        addView(still, LayoutParams(stillW, stillH))
        addView(title, LayoutParams(-2, -2).apply { topMargin = Cinema.px(context, 14) })
        addView(meta, LayoutParams(stillW, -2))
        onFocusChangeListener = OnFocusChangeListener { _, lit ->
            animate().scaleX(if (lit) 1.06f else 1f).scaleY(if (lit) 1.06f else 1f).setDuration(160).start()
            title.setTextColor(if (lit) Cinema.CREAM else Cinema.CARD_TITLE)
            onFocus?.invoke(lit)
        }
    }

    /** How far through the title it is, 0 to 1; zero hides the stripe. */
    fun setProgress(fraction: Float) {
        val on = fraction > 0f
        fill.visibility = if (on) VISIBLE else GONE
        if (on) fill.layoutParams = FrameLayout.LayoutParams((stillW * fraction.coerceIn(0f, 1f)).toInt().coerceAtLeast(stripe), stripe, Gravity.BOTTOM or Gravity.START)
    }

    fun setStill(bitmap: Bitmap) = image.setImageBitmap(bitmap)
}
