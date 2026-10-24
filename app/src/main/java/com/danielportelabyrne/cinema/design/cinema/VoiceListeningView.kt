package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.design.Motion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/**
 * Voice search, listening (`23-search-voice-listening`): the stripe-ring microphone, a waveform of stripe-coloured
 * bars that follows the loudness the recogniser reports, the words heard so far as a Fraunces quote, and a hint.
 */
class VoiceListeningView(context: Context) : LinearLayout(context) {
    private val wave = Waveform(context)
    private val quote: TextView = Cinema.text(context, "", 50, Cinema.CREAM, serif = true, tracking = -0.02f).apply { gravity = Gravity.CENTER; maxLines = 2 }
    private val hint: TextView = Cinema.text(context, "", 21, Cinema.MUTED).apply { gravity = Gravity.CENTER }

    init {
        orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
        addView(RingMic(context), LayoutParams(Cinema.px(context, 440), Cinema.px(context, 440)))
        addView(wave, LayoutParams(Cinema.px(context, 44 * 20), Cinema.px(context, 90)).apply { topMargin = Cinema.px(context, 6) })
        addView(quote, LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 14) })
        addView(hint, LayoutParams(-1, -2).apply { topMargin = Cinema.px(context, 14) })
    }

    /** What has been heard ("" while nothing yet), how loud (0..1), and the hint line. */
    fun update(partial: String, level: Float, hintText: String) {
        quote.text = if (partial.isEmpty()) "Listening…" else "“$partial”"
        hint.text = hintText
        wave.level = level
    }

    /** Five stripe rings, saffron outermost, around a cream disc with the microphone. */
    private class RingMic(context: Context) : FrameLayout(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            setWillNotDraw(false)
            val disc = FrameLayout(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Cinema.CREAM) }
                elevation = Cinema.pxf(context, 20)
                addView(Cinema.icon(context, R.drawable.ic_cin_mic, 0xFF1B0D06.toInt()), LayoutParams(Cinema.px(context, 84), Cinema.px(context, 84), Gravity.CENTER))
            }
            addView(disc, LayoutParams(Cinema.px(context, 190), Cinema.px(context, 190), Gravity.CENTER))
        }
        override fun onDraw(canvas: Canvas) {
            val c = width / 2f
            for (i in 4 downTo 0) {   // RC[i] at diameter 190 + i*62 (`.mic` rings in the mockup)
                paint.color = Cinema.RINGS[i]
                canvas.drawCircle(c, c, Cinema.pxf(context, (190 + i * 62) / 2f), paint)
            }
        }
    }

    /** 44 rounded bars in the stripe colours, tallest in the middle, breathing with the loudness. */
    private class Waveform(context: Context) : View(context) {
        var level = 0f
        private var shown = 0f
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val bar = RectF()
        override fun onDraw(canvas: Canvas) {
            shown += (level - shown) * 0.25f
            val n = 44
            val w = Cinema.pxf(context, 12); val gap = Cinema.pxf(context, 8)
            val maxH = Cinema.pxf(context, 86); val minH = Cinema.pxf(context, 14)
            val t = if (Motion.animationsOn(this)) SystemClock.uptimeMillis() / 1000f else 0f
            val cy = height / 2f
            for (i in 0 until n) {
                val shape = (0.25f + 0.75f * abs(sin(i * 0.55f + t * 3f) * cos(i * 0.17f + 1f))) * exp(-((i - n / 2f) / (n / 2.6f)).let { it * it } * 0.5f)
                val h = max(minH, maxH * shape * (0.3f + 0.7f * shown))
                paint.color = Cinema.RINGS[4 - (i % 5)]
                val x = i * (w + gap)
                bar.set(x, cy - h / 2, x + w, cy + h / 2)
                canvas.drawRoundRect(bar, w / 2, w / 2, paint)
            }
            if (isAttachedToWindow && isShown) postInvalidateOnAnimation()
        }
    }
}
