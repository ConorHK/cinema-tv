package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.TextView
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.design.AppTheme
import com.danielportelabyrne.cinema.design.Motion

/**
 * The light focus treatment for busy screens (poster rows, the programme guide), where the shared one is too costly:
 * that one starts a colour animator for every label and lifts the focused card on a shadow, and a row of twenty
 * posters repeats that at every key press. This one swaps the surface and the label colours and nothing else.
 */
object FastFocus {
    /** A pair of surfaces for one corner radius, cheap to hand out because each view gets a copy of the shared state. */
    class Surfaces(context: Context, radiusPx: Float) {
        private val restState = GradientDrawable().apply { cornerRadius = radiusPx; setColor(0) }.constantState!!
        private val litState = AppTheme.tileBackgroundFocused(context, radiusPx).constantState!!
        fun rest(): Drawable = restState.newDrawable().mutate()
        fun lit(): Drawable = litState.newDrawable().mutate()
    }

    /**
     * Lights [view] ivory when focused, with [labels] turned to ink. [rest] is the resting surface (null for none).
     * Label colours are read once, now, so bind after the labels are styled.
     */
    fun bind(view: View, surfaces: Surfaces, labels: List<TextView>, rest: Drawable? = null, onChange: ((Boolean) -> Unit)? = null) {
        val ink = view.context.getColor(R.color.ink)
        val restColours = labels.map { it.currentTextColor }
        val restSurface = rest ?: surfaces.rest()
        val litSurface = surfaces.lit()
        view.background = restSurface
        view.setOnFocusChangeListener { v, focused ->
            v.background = if (focused) litSurface else restSurface
            labels.forEachIndexed { i, t -> t.setTextColor(if (focused) ink else restColours[i]) }
            onChange?.invoke(focused)
        }
    }

    /** Keeps [view] inside its rows and page by gliding, with the same behaviour for every card: no jumps, no overshoot. */
    fun reveal(view: View, page: ScrollView?, topInset: Int, bottomInset: Int, sideMargin: Int) {
        // Laid out already: move this frame, not the next.
        if (view.isLaidOut && !view.isLayoutRequested) revealNow(view, page, topInset, bottomInset, sideMargin)
        else view.post { if (view.isAttachedToWindow) revealNow(view, page, topInset, bottomInset, sideMargin) }
    }

    private fun revealNow(view: View, page: ScrollView?, topInset: Int, bottomInset: Int, sideMargin: Int) {
        // Title details have their own ScrollView, beside the main page. Resolve actual ancestors:
        // offsetDescendantRectToMyCoords throws if the supplied page does not contain this view.
        var horizontal: HorizontalScrollView? = null
        var vertical: ScrollView? = null
        var p = view.parent
        while (p != null) {
            if (horizontal == null && p is HorizontalScrollView) horizontal = p
            if (vertical == null && p is ScrollView) vertical = p
            p = p.parent
        }
        horizontal?.let { glideSideways(view, it, sideMargin) }
        vertical?.let { glideDown(view, it, if (it === page) topInset else 0, bottomInset) }
    }

    private fun glideSideways(view: View, scroller: HorizontalScrollView, margin: Int) {
        val r = Rect(0, 0, view.width, view.height)
        scroller.offsetDescendantRectToMyCoords(view, r)
        // Mid-glide, judge from where the row will stop.
        val left = Motion.panTarget(scroller) ?: scroller.scrollX
        val right = left + scroller.width
        val target = when {
            r.left - margin < left -> r.left - margin
            r.right + margin > right -> minOf(r.left - margin, r.right + margin - scroller.width)
            else -> return
        }.coerceAtLeast(0)
        Motion.glideHorizontalTo(scroller, target)
    }

    private fun glideDown(view: View, page: ScrollView, topInset: Int, bottomInset: Int) {
        val r = Rect(0, 0, view.width, view.height)
        page.offsetDescendantRectToMyCoords(view, r)
        val top = Motion.panTarget(page) ?: page.scrollY
        val bottom = top + page.height
        val target = when {
            r.top - topInset < top -> r.top - topInset
            r.bottom + bottomInset > bottom -> r.bottom + bottomInset - page.height
            else -> return
        }.coerceAtLeast(0)
        // Moving up close to the top, go all the way: otherwise the page can stop short of its first row and look
        // stuck. Moving down, never snap, or the first steps from the top would not move at all.
        val snapped = if (target < top && target < 160 * page.resources.displayMetrics.density) 0 else target
        Motion.glideVerticalTo(page, snapped)
    }

    /** Child views of [group] that are directly attached, newest last (used to find rows by position). */
    fun childrenOf(group: ViewGroup): List<View> = (0 until group.childCount).map { group.getChildAt(it) }
}
