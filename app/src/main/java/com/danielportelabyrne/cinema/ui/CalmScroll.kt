package com.danielportelabyrne.cinema.ui

import android.view.View
import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.widget.HorizontalScrollView
import android.widget.ScrollView

/**
 * Scroll views that never jump. Android's own "scroll the focused child into view" is switched off, and
 * [scrollFocusIntoView] pans them with the app's slow, soft curve instead. Their soft edges are drawn
 * by the home backdrop's edge veil (see HomeBackdrop.attachVeil), not by offscreen layers.
 */
class CalmScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    /** Called with the scroll position on every scroll frame and after every layout. */
    var onScrolled: ((Int) -> Unit)? = null

    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        onScrolled?.invoke(t)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        onScrolled?.invoke(scrollY)
    }
}

class CalmHorizontalScrollView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : HorizontalScrollView(context, attrs) {
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int = 0

    /**
     * When set, Left from the row's first item may leave the row for something that is in no row at all (the Cinema
     * page's side rail). It still never jumps into a neighbouring row.
     */
    var leftLeavesToRail = false

    /**
     * Left and right stay in this row: at its end the focus stops there rather than jumping to a neighbouring row
     * that happens to be nearby on screen.
     */
    override fun focusSearch(focused: View?, direction: Int): View? {
        val found = super.focusSearch(focused, direction)
        if (focused == null || (direction != View.FOCUS_LEFT && direction != View.FOCUS_RIGHT)) return found
        var p: android.view.ViewParent? = found?.parent
        while (p != null) { if (p === this) return found; p = p.parent }
        if (leftLeavesToRail && direction == View.FOCUS_LEFT && found != null && !insideAnyRow(found)) return found
        return focused
    }

    private fun insideAnyRow(view: View): Boolean {
        var p: android.view.ViewParent? = view.parent
        while (p != null) { if (p is HorizontalScrollView) return true; p = p.parent }
        return false
    }
}
