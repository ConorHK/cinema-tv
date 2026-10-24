package com.danielportelabyrne.cinema.design

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.widget.TextView
import com.danielportelabyrne.cinema.R

/**
 * The app's six text styles, for views built in code. `res/values/themes.xml` defines the same
 * six (`Text.Display` ... `Text.Eyebrow`) for layouts. Nothing else sets a text size.
 *
 *  - Display: serif 36, once per screen (the home greeting).
 *  - Title: serif 26, section headings and sheet/panel titles.
 *  - Heading: serif bold 22, card titles (a film, a recipe, a project).
 *  - Body: sans medium 18, labels, buttons, anything you have to read.
 *  - Caption: sans 16 at 78%, secondary metadata only.
 *  - Eyebrow: sans medium 14, tracked capitals in the accent colour; at most one per card.
 */
object Type {
    enum class Style(val sizeRes: Int, val family: String, val weight: Int, val colorRes: Int, val tracking: Float = 0f, val caps: Boolean = false) {
        DISPLAY(R.dimen.text_display, "serif", Typeface.NORMAL, R.color.text),
        TITLE(R.dimen.text_title, "serif", Typeface.NORMAL, R.color.text),
        HEADING(R.dimen.text_heading, "serif", Typeface.BOLD, R.color.text),
        BODY(R.dimen.text_body, "sans-serif-medium", Typeface.NORMAL, R.color.text),
        CAPTION(R.dimen.text_caption, "sans-serif", Typeface.NORMAL, R.color.text_muted),
        EYEBROW(R.dimen.text_eyebrow, "sans-serif-medium", Typeface.NORMAL, R.color.accent, 0.16f, true)
    }

    /** Applies [style] to [view]. [color] (a resolved colour int) overrides the style's colour, e.g. ink on paper. */
    fun apply(view: TextView, style: Style, color: Int? = null): TextView = view.apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(style.sizeRes))
        typeface = Typeface.create(style.family, style.weight)
        setTextColor(color ?: context.getColor(style.colorRes))
        letterSpacing = style.tracking
        isAllCaps = style.caps
    }

    fun text(context: Context, value: CharSequence, style: Style, color: Int? = null): TextView =
        apply(TextView(context).apply { text = value }, style, color)

    /** Body text for reading (a synopsis, a note): regular weight with a little more leading. */
    fun reading(view: TextView): TextView = view.apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        setLineSpacing(0f, 1.18f)
    }

    /** Text that sits directly on the painting gets the same soft shadow everywhere. */
    fun onPainting(view: TextView): TextView = view.apply { setShadowLayer(8f, 0f, 2f, 0x99000000.toInt()) }
}
