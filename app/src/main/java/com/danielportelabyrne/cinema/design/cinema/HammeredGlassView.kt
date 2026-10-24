package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import com.danielportelabyrne.cinema.design.Motion
import java.util.concurrent.Executors

/**
 * The paused picture slowly becoming silky rippled glass with the picture glowing through it ([GlassRenderer]).
 * [start] takes the paused frame, works the glass out in the background, then dissolves the frame into it over
 * [MORPH_MS] while the glass settles into place; [stop] fades it all away.
 *
 * The glass is a finished picture rather than a live effect, so once it has formed it costs nothing to show, and it
 * works the same on every Android version.
 */
class HammeredGlassView(context: Context) : FrameLayout(context) {
    /** How far the glass has formed, 0 to 1, as it changes (the paused screen eases its dimming back with it). */
    var onAmount: (Float) -> Unit = {}

    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    // The paused frame as the player shows it (bars and all), so the change starts exactly from the picture on screen.
    private val still = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val glass = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY }

    init {
        visibility = GONE
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        addView(still, LayoutParams(-1, -1))
        addView(glass, LayoutParams(-1, -1))
    }

    /** Begins turning [frame] (the paused picture) into glass. */
    fun start(frame: Bitmap) {
        stopNow()
        val token = ++generation
        worker.execute {
            if (token != generation) return@execute
            val result = try {
                val picture = fill(frame, WIDTH, HEIGHT)
                val px = IntArray(WIDTH * HEIGHT).also { picture.getPixels(it, 0, WIDTH, 0, 0, WIDTH, HEIGHT) }
                val started = SystemClock.uptimeMillis()
                val out = GlassRenderer.render(px, WIDTH, HEIGHT)
                com.danielportelabyrne.cinema.ui.Perf.took("glass.render", SystemClock.uptimeMillis() - started)
                frame to Bitmap.createBitmap(out, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            } catch (e: Throwable) { Log.w(TAG, "Glass failed: $e"); null }
            main.post { if (token == generation && result != null) reveal(result.first, result.second) }
        }
    }

    private fun reveal(picture: Bitmap, finished: Bitmap) {
        still.setImageBitmap(picture)
        glass.setImageBitmap(finished)
        visibility = VISIBLE
        alpha = 1f
        val moving = Motion.animationsOn(this)
        // The frame comes up exactly over the paused video, then the glass forms over it, settling down into place.
        glass.alpha = 0f
        glass.translationY = if (moving) -resources.displayMetrics.heightPixels * 0.02f else 0f
        glass.scaleX = if (moving) 1.04f else 1f; glass.scaleY = glass.scaleX
        glass.animate().alpha(1f).translationY(0f).scaleX(1f).scaleY(1f)
            .setDuration(if (moving) MORPH_MS else 0L).setInterpolator(EASE)
            .setUpdateListener { onAmount(glass.alpha) }
            .withEndAction { onAmount(1f); still.setImageDrawable(null) }
            .start()
    }

    /** Fades the glass away (playback resumed, or the paused screen closed). */
    fun stop() {
        generation++
        if (visibility != VISIBLE) return
        glass.animate().cancel()
        animate().cancel()
        animate().alpha(0f).setDuration(220L).withEndAction { stopNow() }.start()
    }

    private fun stopNow() {
        glass.animate().cancel(); animate().cancel()
        visibility = GONE; alpha = 1f
        still.setImageDrawable(null); glass.setImageDrawable(null)
    }

    override fun onDetachedFromWindow() { generation++; stopNow(); super.onDetachedFromWindow() }

    companion object {
        private const val TAG = "HammeredGlass"
        /** How long the picture takes to turn fully to glass. */
        const val MORPH_MS = 3200L
        /** The glass is worked out at half the screen's size; it is soft enough that this cannot be seen. */
        private const val WIDTH = 960
        private const val HEIGHT = 540
        private val EASE = PathInterpolator(0.45f, 0f, 0.25f, 1f)
        private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "glass").apply { priority = Thread.MIN_PRIORITY + 1 } }

        /** Makes the glass surface ahead of the first pause, so that pause is no slower than the rest. */
        fun warmUp() { worker.execute { runCatching { GlassRenderer.surface(WIDTH, HEIGHT) } } }

        /** [src] scaled and cropped to fill [w] x [h] (a wide film's frame has no bars to keep: the whole window is glass). */
        private fun fill(src: Bitmap, w: Int, h: Int): Bitmap {
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val scale = maxOf(w / src.width.toFloat(), h / src.height.toFloat())
            val sw = (w / scale).toInt().coerceAtMost(src.width); val sh = (h / scale).toInt().coerceAtMost(src.height)
            val from = Rect((src.width - sw) / 2, (src.height - sh) / 2, (src.width + sw) / 2, (src.height + sh) / 2)
            Canvas(out).drawBitmap(src, from, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            return out
        }
    }
}
