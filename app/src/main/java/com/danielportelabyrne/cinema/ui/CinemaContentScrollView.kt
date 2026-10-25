package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.FocusFinder
import android.view.View
import android.widget.ScrollView
import com.danielportelabyrne.cinema.design.Motion

/**
 * The Cinema page's focus rules. Left at the edge of a row returns to the active rail item, rather than the nearest
 * unrelated one; Up and Down stay on the page (they never jump sideways into the rail), and at the page's ends the
 * key is simply absorbed.
 */
class CinemaContentScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ScrollView(context, attrs, defStyleAttr) {
    var navigationTarget: (() -> View?)? = null

    // Android jumps to a newly focused child; glide there instead.
    override fun computeScrollDeltaToGetChildRectOnScreen(rect: Rect?): Int {
        val delta = super.computeScrollDeltaToGetChildRectOnScreen(rect)
        if (delta != 0) Motion.glideVerticalTo(this, scrollY + delta)
        return 0
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        if (direction == View.FOCUS_LEFT) {
            val inside = FocusFinder.getInstance().findNextFocus(this, focused, direction)
            if (inside != null && focused != null) {
                val from = Rect().also { focused.getDrawingRect(it); offsetDescendantRectToMyCoords(focused, it) }
                val to = Rect().also { inside.getDrawingRect(it); offsetDescendantRectToMyCoords(inside, it) }
                // A hero button above the row is not a left neighbour of its first poster.
                if (from.top < to.bottom && to.top < from.bottom) return inside
            }
            navigationTarget?.invoke()?.takeIf { it.isShown && it.isFocusable }?.let { return it }
        }
        if (direction == View.FOCUS_UP || direction == View.FOCUS_DOWN) {
            val next = FocusFinder.getInstance().findNextFocus(this, focused, direction)
            // Up from the topmost item: nothing above can take focus (a page header, the Downloads counts), so show the
            // top of the page instead of leaving it scrolled out of reach.
            if (next == null && direction == View.FOCUS_UP && scrollY > 0) Motion.glideVerticalTo(this, 0)
            return next ?: focused
        }
        return super.focusSearch(focused, direction)
    }
}
