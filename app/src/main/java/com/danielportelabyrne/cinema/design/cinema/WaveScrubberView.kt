package com.danielportelabyrne.cinema.design.cinema

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import com.danielportelabyrne.cinema.data.player.PlayerMath
import com.danielportelabyrne.cinema.design.Cinema

/**
 * The wavy scrubber (`waveScrub` in the mockups): a sine across the bar, amplitude [amplitudePx] and wavelength
 * [wavelengthPx] (mockup pixels). Watched: three hard-stepped bands, maroon (full width), #E2562B (2/3) and #F6BF33
 * (1/3), round caps, no glow. Unwatched: one solid #4A423B band at 70% width. Chapter markers are small solid dots
 * on the wave (no rings, per the no-outline rule); the playhead is an off-white bulb with only a soft radial glow.
 *
 * The wave [Path] is built once per size (sampled every 4 mockup px). Moving the playhead only clips that one path
 * differently and invalidates, so scrubbing never rebuilds geometry.
 */
class WaveScrubberView(context: Context, private val amplitudePx: Float = 13f, private val wavelengthPx: Float = 150f, private val thicknessPx: Float = 30f) : View(context) {
    /** Playback position, 0..1. */
    var progress = 0f
        set(value) { val v = value.coerceIn(0f, 1f); if (v != field) { field = v; invalidate() } }
    /** Chapter starts as fractions of the film (the first chapter, at 0, is not marked). */
    var chapters: List<Float> = emptyList()
        set(value) { field = value; invalidate() }

    private val path = Path()
    private val inset = Cinema.pxf(context, 46)   // room for the playhead's glow at either end
    private val amp = Cinema.pxf(context, amplitudePx)
    private val wave = Cinema.pxf(context, wavelengthPx)
    private val thick = Cinema.pxf(context, thicknessPx)
    private fun band(color: Int, width: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; style = Paint.Style.STROKE; strokeWidth = width; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val unwatched = band(0xFF4A423B.toInt(), thick * 0.7f)
    private val outer = band(Cinema.MAROON, thick)
    private val middle = band(Cinema.STRIPE_TERRA, thick * 0.667f)
    private val inner = band(Cinema.STRIPE_SAFFRON, thick * 0.333f)
    private val markerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B0D06.toInt() }
    private val markerAhead = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cinema.alpha(Cinema.CREAM, 0.55f) }
    private val bulb = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFF7EFE0.toInt() }
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowRadius = Cinema.pxf(context, 46)

    init { isFocusable = false; importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

    private val x0 get() = inset
    private val x1 get() = width - inset

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        path.reset()
        val base = h / 2f
        PlayerMath.waveSamples(x0, x1, Cinema.pxf(context, 4)).forEachIndexed { i, x ->
            val y = PlayerMath.waveY(x, x0, base, amp, wave)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        glow.shader = RadialGradient(0f, 0f, glowRadius, intArrayOf(Cinema.alpha(Cinema.CREAM, 0.55f), Cinema.alpha(Cinema.CREAM, 0.18f), Cinema.alpha(Cinema.CREAM, 0f)), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
    }

    /** The playhead's x, for placing labels and previews above it (in this view's coordinates). */
    fun playheadX(): Float = PlayerMath.playheadX(progress, x0, x1)
    fun xFor(fraction: Float): Float = PlayerMath.playheadX(fraction, x0, x1)

    override fun onDraw(canvas: Canvas) {
        if (width == 0) return
        val px = playheadX()
        val base = height / 2f
        // Unwatched, to the right of the playhead.
        canvas.save(); canvas.clipRect(px, 0f, width.toFloat(), height.toFloat()); canvas.drawPath(path, unwatched); canvas.restore()
        // Watched, three bands; the clip leaves a flat cut at the playhead, which the bulb then covers.
        canvas.save(); canvas.clipRect(0f, 0f, px, height.toFloat())
        canvas.drawPath(path, outer); canvas.drawPath(path, middle); canvas.drawPath(path, inner)
        canvas.restore()
        // Chapter markers as solid dots (no rings: Cinema draws no outlines): dark on the watched bands, soft cream ahead.
        val r = Cinema.pxf(context, 6)
        chapters.filter { it > 0f && it < 1f }.forEach { f ->
            val x = xFor(f); val y = PlayerMath.waveY(x, x0, base, amp, wave)
            canvas.drawCircle(x, y, r, if (x < px) markerFill else markerAhead)
        }
        // The playhead: a soft bulb glow, then the solid dot.
        val py = PlayerMath.waveY(px, x0, base, amp, wave)
        canvas.save(); canvas.translate(px, py); canvas.drawCircle(0f, 0f, glowRadius, glow); canvas.restore()
        canvas.drawCircle(px, py, Cinema.pxf(context, if (isFocused) 22 else 17), bulb)
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: android.graphics.Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()   // the bulb grows a little while the scrubber has focus
    }
}

/** The paused screen's bitrate line: one path stroked in the five stripe colours, widest first, so it reads as a ribbon. */
class BitrateSparkline(context: Context) : View(context) {
    private var values: List<Long> = emptyList()
    private val path = Path()
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    fun set(samples: List<Long>) { values = samples; rebuild(); invalidate() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = rebuild()

    private fun rebuild() {
        path.reset()
        if (values.size < 2 || width == 0) return
        val pad = Cinema.pxf(context, 6)
        val max = values.max().toFloat(); val min = values.min().toFloat()
        val span = (max - min).coerceAtLeast(max * 0.1f).coerceAtLeast(1f)
        values.forEachIndexed { i, v ->
            val x = pad + (width - 2 * pad) * i / (values.size - 1)
            val y = pad + (height - 2 * pad) * (1f - (v - min) / span)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (path.isEmpty) return
        Cinema.RINGS.forEachIndexed { i, c ->
            stroke.color = c; stroke.strokeWidth = Cinema.pxf(context, 10f * (5 - i) / 5 + 1)
            canvas.drawPath(path, stroke)
        }
    }
}
