package com.danielportelabyrne.cinema.ui

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import com.danielportelabyrne.cinema.design.Motion

/**
 * Requests focus onto [target] now, and re-checks shortly after.
 *
 * A freshly-built subtree (a rebuilt shelf row, a just-shown overlay)
 * races an immediate requestFocus() against the platform's own
 * window/layout-transition focus handling: the immediate call frequently
 * wins, but not always -- if it loses, whatever was focused *before* the
 * change simply stays focused (overlapping views don't steal focus from
 * each other automatically), which silently leaves a background view
 * focused underneath a new overlay. The delayed re-check catches that.
 */
fun requestFocusRobust(root: ViewGroup, target: View) {
    target.post { target.requestFocus() }
    target.postDelayed({ val f = root.findFocus(); if (f == null || f === root) target.requestFocus() }, 300)
}

/**
 * Keeps the focused item in view by *panning*, never jumping.
 *
 * Vertically, the page only moves when the item leaves a comfort zone, and then it glides so the item
 * settles about a quarter of the way down the screen, which reads as calm, deliberate scrolling rather than
 * an edge-chasing nudge. Horizontally, rails glide just far enough to keep the item and a little of its
 * neighbours in view. Other (plain) scroll views keep Android's own behaviour.
 */
fun scrollFocusIntoView(view: View, hasFocus: Boolean) {
    if (!hasFocus) return
    view.post {
        // Plain scroll views (drawer, edit list) still rely on Android to reveal the item.
        view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
        val density = view.resources.displayMetrics.density
        var parent: android.view.ViewParent? = view.parent
        while (parent != null) {
            when (parent) {
                is CalmScrollView -> panVertically(parent, view, density)
                is CalmHorizontalScrollView -> panHorizontally(parent, view, density)
            }
            parent = parent.parent
        }
    }
}

private fun panVertically(sv: CalmScrollView, view: View, density: Float) {
    val bounds = Rect(0, 0, view.width, view.height)
    sv.offsetDescendantRectToMyCoords(view, bounds)
    val h = sv.height
    // 1) Anything that fits on the first screen keeps the page at the top: the greeting, film card and
    //    Your apps never slide away while you move between them.
    if (bounds.bottom < (h * 0.92f)) { Motion.scrollVerticalTo(sv, 0); return }
    // 2) Inside the comfort zone: stay put, so moving sideways along a row never moves the page.
    val comfortTop = sv.scrollY + (h * 0.05f).toInt()
    val comfortBottom = sv.scrollY + (h * 0.88f).toInt()
    if (bounds.top >= comfortTop && bounds.bottom <= comfortBottom) return
    // 3) Otherwise glide so the row settles about a third of the way down the screen, with the row above still peeking in.
    Motion.scrollVerticalTo(sv, bounds.top - (h * 0.34f).toInt())
}

/**
 * Rails keep their grid: when one has to move, it snaps so a whole card starts exactly at the page margin
 * (never a sliver of card cut at the left edge). Going right, it moves the least whole-card distance that
 * shows the focused card; going left, the focused card comes to the margin.
 */
private fun panHorizontally(sv: CalmHorizontalScrollView, view: View, @Suppress("UNUSED_PARAMETER") density: Float) {
    val bounds = Rect(0, 0, view.width, view.height)
    sv.offsetDescendantRectToMyCoords(view, bounds)
    val start = sv.paddingLeft
    val end = sv.width - sv.paddingRight
    if (bounds.left >= sv.scrollX + start && bounds.right <= sv.scrollX + end) return
    val target = if (bounds.left < sv.scrollX + start) bounds.left - start else {
        val need = bounds.right - end
        val row = sv.getChildAt(0) as? android.view.ViewGroup
        val stops = (0 until (row?.childCount ?: 0)).map { row!!.left + row.getChildAt(it).left - start }
        stops.filter { it >= need }.minOrNull() ?: need
    }
    Motion.scrollHorizontalTo(sv, target)
}
