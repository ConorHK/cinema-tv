package com.danielportelabyrne.cinema.design

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.danielportelabyrne.cinema.R

/**
 * One heading for every home section: a Title-style serif name with an optional Caption on the same
 * baseline, sitting on the page scrim (no box of its own) at the page margin. The section above is
 * separated by `section_gap`; the content below starts `heading_gap` under it.
 */
object SectionHeader {

    fun title(context: Context, text: String): TextView = Type.onPainting(Type.text(context, text, Type.Style.TITLE))

    fun subtitle(context: Context, text: String): TextView = Type.onPainting(Type.text(context, text, Type.Style.CAPTION))

    /** [topGap] defaults to the section rhythm; pass 0 for the first heading under something else. */
    fun build(context: Context, title: String, subtitle: String? = null, topGap: Boolean = true): View {
        val r = context.resources
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(title(context, title))
            if (subtitle != null) addView(subtitle(context, subtitle), LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = r.getDimensionPixelSize(R.dimen.space_3)
                bottomMargin = r.getDimensionPixelSize(R.dimen.space_1)
            })
            setPadding(0, if (topGap) r.getDimensionPixelSize(R.dimen.section_gap) else 0, 0, r.getDimensionPixelSize(R.dimen.heading_gap))
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            setTag(R.id.section_heading, true)
        }
    }
}
