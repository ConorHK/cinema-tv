package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * What A (or Enter) does right now: the focused element's action. Elements register themselves as they gain focus,
 * so the pad never needs to know what is on screen. [alt] is the X button's action on the same element, if any.
 */
class FocusHub {
    private var select: (() -> Unit)? = null
    private var alt: (() -> Unit)? = null
    private var owner: Any? = null

    /** True while some element has focus. */
    val hasFocus: Boolean get() = owner != null

    fun focused(owner: Any, select: () -> Unit, alt: (() -> Unit)?) { this.owner = owner; this.select = select; this.alt = alt }
    fun blurred(owner: Any) { if (this.owner === owner) { this.owner = null; select = null; alt = null } }

    /** True when something took the press. */
    fun select(): Boolean = select?.let { it(); true } ?: false
    fun alt(): Boolean = alt?.let { it(); true } ?: false
}

val LocalFocusHub = staticCompositionLocalOf { FocusHub() }

/**
 * Makes an element reachable with the D-pad and actionable with A, a mouse click or a tap on the Deck's screen.
 * [onFocus] runs as it gains focus (to change the backdrop, remember where the viewer was...).
 */
fun Modifier.tvFocusable(
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onAlt: (() -> Unit)? = null,
    onSelect: () -> Unit,
): Modifier = composed {
    val hub = LocalFocusHub.current
    val select by rememberUpdatedState(onSelect)
    val alt by rememberUpdatedState(onAlt)
    val focused by rememberUpdatedState(onFocus)
    val token = remember { Any() }
    val interactions = remember { MutableInteractionSource() }
    (if (requester != null) Modifier.focusRequester(requester) else Modifier)
        .onFocusChanged {
            if (it.isFocused) { hub.focused(token, { select() }, alt?.let { a -> { a() } }); focused() } else hub.blurred(token)
        }
        // clickable is itself the focus target (one per element), and takes mouse clicks and taps.
        .clickable(interactionSource = interactions, indication = null) { select() }
}

/** Focus state for drawing: true while this element has focus. Pair with [tvFocusable] on the same element. */
@Composable
fun rememberFocused(): Pair<Boolean, (Boolean) -> Unit> {
    var focused by remember { mutableStateOf(false) }
    return focused to { v: Boolean -> focused = v }
}

/** Asks for focus, quietly doing nothing when the element is not on screen (yet). */
fun FocusRequester.tryFocus(): Boolean = try { requestFocus(androidx.compose.ui.focus.FocusDirection.Enter) } catch (_: IllegalStateException) { false }

/**
 * Focuses the first of [tries] that succeeds, retrying for a few frames: rows and grids only build their cards while
 * laying out, so on a screen's first frame the card to focus may not exist yet.
 */
suspend fun focusSoon(vararg tries: () -> Boolean) {
    repeat(40) {
        if (tries.any { it() }) return
        androidx.compose.runtime.withFrameNanos { }
    }
}

/**
 * Glides a page so row [index] sits near the top, as the TV does when a row takes focus. It waits a moment first: the
 * focus change scrolls the page just enough to show the card, and that scroll would otherwise cancel this one.
 */
suspend fun androidx.compose.foundation.lazy.LazyListState.glideTo(index: Int, offset: Int = 10) {
    kotlinx.coroutines.delay(90)
    animateScrollToItem(index, offset)
}
