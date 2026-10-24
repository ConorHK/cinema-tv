package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import com.danielportelabyrne.cinema.design.Cinema
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * The Cinema ground behind every page (`.amb` and `ARCH` in the mockups): espresso, a warm glow top right, a teal one
 * bottom left, and four faint 70s arch columns on the right that fade out toward the left.
 *
 * It is painted once per size into a half-resolution bitmap and then only blitted, so scrolling the page above it
 * costs one bitmap draw a frame.
 */
class AmbientBackground(context: Context, private val arches: Boolean = true) : View(context) {
    private var cache: Bitmap? = null
    private val blit = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dst = RectF()

    init { isFocusable = false; isClickable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        cache?.recycle(); cache = null
        if (w > 0 && h > 0) cache = paint(w / 2, h / 2, arches)
    }

    override fun onDraw(canvas: Canvas) {
        val c = cache ?: run { canvas.drawColor(Cinema.BG); return }
        dst.set(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawBitmap(c, null, dst, blit)
    }

    override fun onDetachedFromWindow() { super.onDetachedFromWindow(); cache?.recycle(); cache = null }

    companion object {
        /** Paints the ground at [w] x [h]; the mockup geometry is in a 1920 x 1080 frame and scaled to fit. */
        fun paint(w: Int, h: Int, arches: Boolean): Bitmap {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Cinema.BG)
            val sx = w / 1920f; val sy = h / 1080f
            fun glow(cx: Float, cy: Float, rx: Float, ry: Float, color: Int, alpha: Float) {
                // CSS radial-gradient(rx ry at cx cy, color, transparent 70%): an ellipse, drawn as a scaled circle.
                val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
                p.shader = RadialGradient(0f, 0f, rx * sx, intArrayOf(Cinema.alpha(color, alpha), Cinema.alpha(color, 0f)), floatArrayOf(0f, 0.7f), Shader.TileMode.CLAMP)
                c.save(); c.translate(cx * sx, cy * sy); c.scale(1f, (ry * sy) / (rx * sx))
                c.drawCircle(0f, 0f, rx * sx, p); c.restore()
            }
            glow(1690f, -108f, 900f, 600f, 0xFFE27A49.toInt(), 0.16f)
            glow(0f, 1188f, 900f, 700f, 0xFF3AA597.toInt(), 0.14f)
            glow(960f, 540f, 700f, 500f, 0xFFEDBE56.toInt(), 0.04f)
            if (arches) drawArches(c, w, h, 0.2f)
            return bmp
        }

        /** The four arch columns, at [opacity], masked to fade toward the left (CSS `linear-gradient(100deg, transparent 30%, #000 80%)`). */
        fun drawArches(c: Canvas, w: Int, h: Int, opacity: Float) {
            val layer = c.saveLayer(0f, 0f, w.toFloat(), h.toFloat(), null)
            val m = Matrix().apply { setScale(w / 1920f, h / 1080f) }
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun arch(x: Float, top: Float, r: Float, color: Int, a: Float = 1f) {
                val path = Path().apply {
                    moveTo(x, 1100f); lineTo(x, top)
                    arcTo(RectF(x, top - r, x + 2 * r, top + r), 180f, 180f)
                    lineTo(x + 2 * r, 1100f); close()
                    transform(m)
                }
                p.color = Cinema.alpha(color, opacity * a)
                c.drawPath(path, p)
            }
            arch(1170f, 560f, 150f, 0xFFE0A93A.toInt())
            arch(1400f, 470f, 130f, 0xFFC4532A.toInt())
            arch(1640f, 720f, 140f, 0xFF6E2214.toInt())
            arch(1290f, 820f, 160f, 0xFF4D5B2C.toInt(), 0.9f)
            // Fade mask along a line 100 degrees from vertical, as CSS measures it.
            val rad = Math.toRadians(100.0)
            val dx = sin(rad).toFloat(); val dy = -cos(rad).toFloat()
            val len = abs(w * dx) + abs(h * dy)
            val cx = w / 2f; val cy = h / 2f
            val mask = Paint().apply {
                shader = LinearGradient(cx - dx * len / 2, cy - dy * len / 2, cx + dx * len / 2, cy + dy * len / 2,
                    intArrayOf(0, 0, -0x1000000, -0x1000000), floatArrayOf(0f, 0.3f, 0.8f, 1f), Shader.TileMode.CLAMP)
                xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            }
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), mask)
            c.restoreToCount(layer)
        }
    }
}
