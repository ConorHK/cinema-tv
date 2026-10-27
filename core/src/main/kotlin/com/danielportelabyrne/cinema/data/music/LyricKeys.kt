package com.danielportelabyrne.cinema.data.music

/**
 * Up and Down on now playing while lyrics show: holding scrolls them, a tap goes back to the line being sung, and a
 * double tap hides them. A tap waits [doubleMs] for a second tap before it counts. [direction] is -1 for up, 1 for
 * down. Pure, so it can be tested.
 */
class LyricKeys(private val doubleMs: Long = 300, private val holdMs: Long = 400) {
    sealed class Action {
        object Follow : Action()
        object Hide : Action()
        data class ScrollStart(val direction: Int) : Action()
        object ScrollEnd : Action()
    }

    private var pending = false
    private var pendingAt = 0L
    private var pressedAt = 0L
    private var held = false
    var scrolling = false
        private set

    fun down(direction: Int, repeat: Int, atMs: Long): Action? {
        if (repeat == 0) {
            held = true
            pressedAt = atMs
            if (pending && atMs - pendingAt <= doubleMs) { pending = false; return Action.Hide }
            pending = true; pendingAt = atMs
            return null
        }
        if (!scrolling && atMs - pressedAt >= holdMs) {
            scrolling = true; pending = false
            return Action.ScrollStart(direction)
        }
        return null
    }

    fun up(atMs: Long): Action? {
        held = false
        if (scrolling) { scrolling = false; return Action.ScrollEnd }
        return due(atMs)
    }

    /** The waiting tap, once no second tap can follow. Call every frame. */
    fun due(atMs: Long): Action? {
        if (!pending || held || atMs - pendingAt <= doubleMs) return null
        pending = false
        return Action.Follow
    }

    /** Forgets a waiting tap, as when the lyrics go away. */
    fun reset() { pending = false; held = false; scrolling = false }
}
