package com.danielportelabyrne.cinema.design.cinema

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * Soft shapes painted once and reused: the resting shadow under every poster and the focus bloom behind a card or a
 * button. Drawn live, each was a blur (BlurMaskFilter or an elevation shadow) the GPU recomputed every frame for every
 * card on screen; as bitmaps they cost one textured quad. Cached by size and colour; a page uses a handful of sizes.
 */
object Glows {
    /** A blurred rounded rectangle [w]×[h] (the shape), with [blur] of room around it on every side. */
    class Glow(val bitmap: Bitmap, val pad: Int)

    private val cache = HashMap<String, Glow>()

    /** A rounded rectangle [w]×[h] of [color], blurred by [blur] px. Draw it at (shapeLeft - pad, shapeTop - pad). */
    @Synchronized fun roundRect(w: Int, h: Int, radius: Float, blur: Float, color: Int): Glow {
        val key = "$w:$h:$radius:$blur:$color"
        cache[key]?.let { return it }
        val pad = Math.ceil(blur * 1.5).toInt() + 1
        val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            if (blur > 0f) maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
        }
        Canvas(bmp).drawRoundRect(RectF(pad.toFloat(), pad.toFloat(), (pad + w).toFloat(), (pad + h).toFloat()), radius, radius, paint)
        bmp.prepareToDraw()
        return Glow(bmp, pad).also { cache[key] = it }
    }

    /**
     * The resting shadow of a poster at [elevation] px: what the system's light (from above, Material's 0.19 spot and
     * 0.039 ambient alphas) casts, as one soft rounded rectangle slightly below the shape.
     */
    fun shadow(w: Int, h: Int, radius: Float, elevation: Float): Glow =
        roundRect(w, h, radius, elevation * 0.9f, android.graphics.Color.argb(62, 0, 0, 0))

    /** Vertical offset of [shadow] below its shape. */
    fun shadowOffset(elevation: Float): Float = elevation * 0.45f
}
