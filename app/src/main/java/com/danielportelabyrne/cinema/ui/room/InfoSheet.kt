package com.danielportelabyrne.cinema.ui.room

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.AppTheme
import com.danielportelabyrne.cinema.design.Motion
import com.danielportelabyrne.cinema.design.Type
import com.danielportelabyrne.cinema.ui.scrollFocusIntoView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PAPER_TOP = 0xFFF3E7C6.toInt()
private val PAPER_BOTTOM = 0xFFE6D5A8.toInt()
private val PAPER_INK = 0xFF2B2117.toInt()
private val PAPER_INK_DIM = 0xFF6B5A46.toInt()
private val TERRACOTTA = 0xFFAF5938.toInt()
private val WALNUT_FRAME = 0xFF5A3F28.toInt()

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun Context.text(value: String, sp: Float, color: Int, style: Int = Typeface.NORMAL, family: String = "sans-serif-medium") =
    TextView(this).apply {
        text = value; textSize = sp; setTextColor(color)
        typeface = Typeface.create(family, style)
    }

private fun paper(context: Context, radiusDp: Int): GradientDrawable = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(PAPER_TOP, PAPER_BOTTOM)).apply {
    cornerRadius = context.dp(radiusDp).toFloat()
}

// ---------------------------------------------------------------------------------------------
// Project shelf
// ---------------------------------------------------------------------------------------------

/** The sideboard: projects standing on a walnut ledge. One caption below the ledge follows the focused object. */
class SheetAction(val label: String, val sublabel: String? = null, val primary: Boolean = false, val run: () -> Unit)

/**
 * A calm centred card for details and confirmations, and the app's own replacement for
 * stock Android dialogs. Optional artwork sits on the left; [actions] become pill buttons above Close.
 * Back or OK on Close dismisses it and returns focus to where it was.
 */
class InfoSheet(private val container: FrameLayout) {
    private val context = container.context
    private var previousFocus: View? = null
    private var lastButtons: List<View> = emptyList()
    private var card: View? = null
    private var flight: Flight? = null
    private var surfaceFade: android.animation.ValueAnimator? = null
    val isVisible: Boolean get() = container.visibility == View.VISIBLE
    /** True while the sheet is animating away: it no longer takes keys, and the page is already coming back. */
    var isClosing = false
        private set

    /**
     * A picture that travels from the page into the sheet (and back when it closes). [source] is the
     * picture on the page, [target] its place in the sheet, [reveal] the parts of the sheet that appear
     * around it once it is on its way.
     */
    class Flight(val source: View, val target: View, val reveal: List<View>) {
        internal val from = android.graphics.Rect()
    }

    fun show(
        kicker: String, title: String, subtitle: String?, lines: List<String>,
        image: android.graphics.Bitmap? = null, actions: List<SheetAction> = emptyList(),
        refresh: Boolean = false,
        /** Size of [image] in dp; defaults to a portrait poster. */
        imageSizeDp: Pair<Int, Int> = 150 to 225,
        /** The picture on the page that [image] shows: it flies from there into the sheet. */
        from: View? = null
    ) {
        // A refresh redraws the same sheet in place (for results that arrive late): keep the way back and the focused button.
        val focusedIndex = if (refresh) lastButtons.indexOfFirst { it.isFocused }.coerceAtLeast(0) else 0
        if (!refresh) rememberFocus()
        settle()
        container.removeAllViews()
        container.setBackgroundColor(ContextCompat.getColor(context, R.color.sheet_scrim))
        val pad = AppTheme.px(context, R.dimen.space_6)
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false; clipToPadding = false
            setPadding(pad, pad, pad, pad)
            background = AppTheme.surface(context, R.color.surface_solid)
            elevation = context.dp(24).toFloat()
        }
        var imageView: View? = null
        if (image != null) {
            card.addView(android.widget.ImageView(context).also { imageView = it }.apply {
                setImageBitmap(image); scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(v: View, o: android.graphics.Outline) { o.setRoundRect(0, 0, v.width, v.height, AppTheme.px(context, R.dimen.radius_small).toFloat()) }
                }
            }, LinearLayout.LayoutParams(context.dp(imageSizeDp.first), context.dp(imageSizeDp.second)).apply { marginEnd = AppTheme.px(context, R.dimen.space_5) })
        }
        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val measure = context.dp(440)
        card.addView(column, LinearLayout.LayoutParams(0, -2, 1f))

        val gap = AppTheme.px(context, R.dimen.space_2)
        column.addView(Type.text(context, kicker, Type.Style.EYEBROW))
        column.addView(Type.text(context, title, Type.Style.TITLE).apply { maxWidth = measure; setPadding(0, gap, 0, 0) })
        subtitle?.let {
            val accent = ContextCompat.getColor(context, R.color.accent)
            column.addView(Type.text(context, it.uppercase(Locale.UK), Type.Style.CAPTION, accent).apply {
                letterSpacing = 0.14f; textSize = 13f
                setPadding(context.dp(10), context.dp(3), context.dp(10), context.dp(4))
                background = GradientDrawable().apply { cornerRadius = context.dp(12).toFloat(); setStroke(context.dp(1), accent) }
            }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = gap + gap / 2 })
        }
        lines.forEach { column.addView(Type.reading(Type.text(context, it, Type.Style.BODY, ContextCompat.getColor(context, R.color.text_muted))).apply { maxWidth = measure; setPadding(0, gap + gap / 2, 0, 0) }) }

        var first: View? = null
        val built = mutableListOf<View>()
        val buttons = actions + SheetAction("Close") { }
        val stack = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; clipChildren = false; clipToPadding = false }
        val controlHeight = AppTheme.px(context, R.dimen.control_height)
        buttons.forEachIndexed { i, action ->
            val label = if (action.sublabel == null) android.text.SpannableString(action.label) else
                android.text.SpannableString(action.label + "\n" + action.sublabel).apply {
                    setSpan(android.text.style.RelativeSizeSpan(0.8f), action.label.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(android.text.style.StyleSpan(Typeface.NORMAL), action.label.length + 1, length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            val extraLines = action.sublabel?.count { it == '\n' }?.plus(1) ?: 0
            // A lone Close is a small centred pill; a list of choices reads left-aligned like a menu.
            val alone = buttons.size == 1
            val button = Type.text(context, label, Type.Style.BODY).apply {
                gravity = if (alone) Gravity.CENTER else Gravity.CENTER_VERTICAL; isFocusable = true; isClickable = true
                setPadding(AppTheme.px(context, R.dimen.space_4), 0, AppTheme.px(context, R.dimen.space_4), 0)
                tag = extraLines
            }
            AppTheme.bindPanelFocus(
                button,
                AppTheme.surface(context, if (action.primary) R.color.action else R.color.surface_raised, R.dimen.radius_pill),
                AppTheme.surface(context, R.color.focus, R.dimen.radius_pill), 1f
            )
            button.setOnClickListener { hide(); if (i != buttons.lastIndex) action.run() }
            stack.addView(button, LinearLayout.LayoutParams(if (alone) context.dp(200) else -1, controlHeight + context.dp(20) * extraLines).apply { topMargin = if (i == 0) 0 else gap })
            built += button
            if (first == null) first = button
        }
        column.addView(stack, LinearLayout.LayoutParams(-1, -2).apply { topMargin = AppTheme.px(context, R.dimen.space_4) })

        // Beside a picture the card hugs its words (up to a reading measure) rather than a fixed width.
        val cardWidth = if (image == null) context.dp(560) else {
            val words = (0 until column.childCount).map { column.getChildAt(it) }.filterIsInstance<TextView>()
                .maxOfOrNull { it.paint.measureText(it.text.toString()) + it.paddingLeft + it.paddingRight }?.toInt() ?: 0
            val text = words.coerceIn(context.dp(280), measure)
            pad * 2 + context.dp(imageSizeDp.first) + AppTheme.px(context, R.dimen.space_5) + text
        }
        container.addView(card, FrameLayout.LayoutParams(cardWidth, -2, Gravity.CENTER))
        container.visibility = View.VISIBLE
        this.card = card
        val f = if (from != null && imageView != null) Flight(from, imageView!!, listOf(column)) else null
        val flying = !refresh && f != null && Motion.animationsOn(container) && from!!.isShown && from.getGlobalVisibleRect(f.from)
        if (flying) { flight = f; flyIn(card, f!!) }
        else if (!refresh) Motion.enter(container, card, context.dp(28).toFloat())
        lastButtons = built
        (built.getOrNull(focusedIndex) ?: first)?.post { (built.getOrNull(focusedIndex) ?: first)?.requestFocus() }
    }

    /** Shows any view centred over a scrim, focusing [focus]. Back dismisses it like every other sheet. */
    fun showCustom(view: View, focus: View?, flight: Flight? = null) {
        rememberFocus()
        settle()
        container.removeAllViews()
        container.setBackgroundColor(ContextCompat.getColor(context, R.color.sheet_scrim))
        container.clipChildren = false
        // Measured before anything moves: the page is about to step back behind the sheet.
        val flying = flight != null && Motion.animationsOn(container) &&
            flight.source.isShown && flight.source.getGlobalVisibleRect(flight.from)
        container.addView(view, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        container.visibility = View.VISIBLE
        card = view
        lastButtons = emptyList()
        if (flying) { this.flight = flight; flyIn(view, flight!!) } else Motion.enter(container, view, context.dp(28).toFloat())
        focus?.post { focus.requestFocus() }
    }

    fun hide() {
        if (!isVisible || isClosing) return
        isClosing = true
        // Focus goes home at once, so the remote answers immediately; the sheet only has to finish leaving.
        container.descendantFocusability = android.view.ViewGroup.FOCUS_BLOCK_DESCENDANTS
        previousFocus?.takeIf { it.isAttachedToWindow }?.requestFocus()
        container.requestLayout()   // lets the page start coming back while the sheet leaves
        val f = flight
        if (f != null && f.source.isAttachedToWindow && Motion.animationsOn(container)) flyOut(f)
        else Motion.exit(container, card) { settle() }
    }

    private fun rememberFocus() {
        // Reopened while still closing: the way back is the one already remembered.
        if (!isClosing) previousFocus = container.rootView.findFocus()
    }

    /** Ends any entrance or exit in its final state and puts the container back to rest. */
    private fun settle() {
        surfaceFade?.cancel(); surfaceFade = null
        container.animate().cancel()
        card?.animate()?.cancel()
        flight?.let { f ->
            f.target.animate().cancel()
            f.source.alpha = 1f
        }
        flight = null
        if (isClosing) {
            isClosing = false
            container.visibility = View.GONE
            container.removeAllViews()
            card = null
        }
        container.alpha = 1f
        container.descendantFocusability = android.view.ViewGroup.FOCUS_AFTER_DESCENDANTS
        (container.background as? ColorDrawable)?.alpha = 255
    }

    /** The picture lifts off the page and settles into the sheet; the sheet's surface and words gather around it. */
    private fun flyIn(card: View, f: Flight) {
        f.source.alpha = 0f
        val scrim = container.background as? ColorDrawable
        val surface = card.background
        scrim?.alpha = 0; surface?.alpha = 0
        f.reveal.forEach { it.alpha = 0f; it.translationY = context.dp(10).toFloat() }
        f.target.visibility = View.INVISIBLE
        card.post {
            if (flight !== f || isClosing) return@post
            placeAt(f.target, f.from)
            f.target.visibility = View.VISIBLE
            f.target.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                .setDuration(Motion.FLIGHT_MS).setInterpolator(Motion.SWOOSH).start()
            fadeSurfaces(scrim, surface, 255, Motion.SHEET_IN_MS / 2)
            f.reveal.forEach {
                it.animate().alpha(1f).translationY(0f).setStartDelay(Motion.FLIGHT_MS / 3)
                    .setDuration(Motion.SHEET_IN_MS).setInterpolator(Motion.SETTLE).start()
            }
        }
    }

    /** The reverse: the sheet dissolves from around the picture, which drops back into its place on the page. */
    private fun flyOut(f: Flight) {
        val scrim = container.background as? ColorDrawable
        val surface = card?.background
        f.reveal.forEach { it.animate().alpha(0f).setStartDelay(0).setDuration(Motion.SHEET_OUT_MS / 2).setInterpolator(Motion.SETTLE).start() }
        fadeSurfaces(scrim, surface, 0, Motion.SHEET_OUT_MS)
        // Back to where the picture was taken from: the page comes forward to exactly that position.
        val o = offsetsTo(f.target, f.from)
        f.target.animate().translationX(o[0]).translationY(o[1]).scaleX(o[2]).scaleY(o[3])
            .setDuration(Motion.FLIGHT_MS - 60).setInterpolator(Motion.SWOOSH)
            .withEndAction { if (flight === f) settle() }.start()
    }

    private fun fadeSurfaces(scrim: ColorDrawable?, surface: android.graphics.drawable.Drawable?, to: Int, ms: Long) {
        surfaceFade?.cancel()
        surfaceFade = android.animation.ValueAnimator.ofInt(scrim?.alpha ?: surface?.alpha ?: 255, to).apply {
            duration = ms; interpolator = Motion.SETTLE
            addUpdateListener { val a = it.animatedValue as Int; scrim?.alpha = a; surface?.alpha = a }
            start()
        }
    }

    /** Puts [view] (pivot at its top-left) exactly over [rect] in screen space. */
    private fun placeAt(view: View, rect: android.graphics.Rect) {
        val o = offsetsTo(view, rect)
        view.translationX = o[0]; view.translationY = o[1]; view.scaleX = o[2]; view.scaleY = o[3]
    }

    /** Translation and scale that put [view]'s untransformed layout box over [rect]; sets the pivot to top-left. */
    private fun offsetsTo(view: View, rect: android.graphics.Rect): FloatArray {
        val saved = floatArrayOf(view.translationX, view.translationY, view.scaleX, view.scaleY)
        view.translationX = 0f; view.translationY = 0f; view.scaleX = 1f; view.scaleY = 1f
        val at = IntArray(2); view.getLocationOnScreen(at)
        view.translationX = saved[0]; view.translationY = saved[1]; view.scaleX = saved[2]; view.scaleY = saved[3]
        view.pivotX = 0f; view.pivotY = 0f
        val w = view.width.coerceAtLeast(1); val h = view.height.coerceAtLeast(1)
        return floatArrayOf((rect.left - at[0]).toFloat(), (rect.top - at[1]).toFloat(), rect.width() / w.toFloat(), rect.height() / h.toFloat())
    }
}

// ---------------------------------------------------------------------------------------------
// Tonight card
// ---------------------------------------------------------------------------------------------

/** "Tonight?": a framed paper print with three lines to choose from -- Watch, Make, Eat. */
internal fun trapEdges(row: LinearLayout) {
    val count = row.childCount
    for (i in 0 until count) {
        row.getChildAt(i).setOnKeyListener { _, keyCode, event ->
            event.action == android.view.KeyEvent.ACTION_DOWN &&
                ((keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && i == 0) || (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && i == count - 1))
        }
    }
}
