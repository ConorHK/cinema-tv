package com.danielportelabyrne.cinema.desktop.input

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint

/**
 * Everything the app can be asked to do, whatever it came from: a gamepad (the Steam Deck's own controls through Steam
 * Input, or any pad SDL knows), the keyboard, or Steam's "desktop" layout, which turns the Deck's controls into keys.
 *
 * On an Xbox-style pad: D-pad or left stick = [UP]/[DOWN]/[LEFT]/[RIGHT], A = [SELECT], B = [BACK], X = [ALT],
 * Y = [SEARCH], LB/RB = [PREV]/[NEXT], Start (≡) = [MENU], View (⧉) = [INFO].
 */
enum class NavAction { UP, DOWN, LEFT, RIGHT, SELECT, BACK, ALT, SEARCH, PREV, NEXT, MENU, INFO;

    val isMove: Boolean get() = this == UP || this == DOWN || this == LEFT || this == RIGHT
}

/** Where an action came from, so the same press arriving twice (pad and keyboard at once) is only acted on once. */
enum class NavSource { KEYBOARD, GAMEPAD }

object KeyMap {
    /** AWT uses U+FFFF for arrows and other keys without text. Never insert it into a search or URL. */
    fun character(event: KeyEvent): Char? {
        if (event.type != KeyEventType.KeyDown || event.isCtrlPressed || event.isAltPressed || action(event) != null) return null
        val code = event.utf16CodePoint
        if (code !in 32 until 0xFFFF || !Character.isDefined(code)) return null
        return code.toChar().takeUnless { it.isISOControl() || it.isSurrogate() }
    }

    /**
     * The keyboard's share of [NavAction]. Arrows, Enter and Escape are what Steam's desktop layout sends for the Deck's
     * D-pad, A and B; the rest are for a real keyboard. Letters are left alone so text can still be typed where it is
     * wanted (the on-screen keyboard listens for them).
     */
    fun action(event: KeyEvent): NavAction? {
        if (event.type != KeyEventType.KeyDown) return null
        return when (event.key) {
            Key.DirectionUp -> NavAction.UP
            Key.DirectionDown -> NavAction.DOWN
            Key.DirectionLeft -> NavAction.LEFT
            Key.DirectionRight -> NavAction.RIGHT
            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> NavAction.SELECT
            Key.Escape, Key.Back -> NavAction.BACK
            Key.PageUp -> NavAction.PREV
            Key.PageDown -> NavAction.NEXT
            Key.F1, Key.Menu -> NavAction.MENU
            Key.F2 -> NavAction.INFO
            Key.F3 -> NavAction.SEARCH
            Key.F -> if (event.isCtrlPressed) NavAction.SEARCH else null
            Key.Tab -> NavAction.NEXT
            else -> null
        }
    }
}

/**
 * Drops a repeat of the same action from the other source within [windowMs]: in Steam's desktop mode one press of the
 * Deck's D-pad can arrive both as an arrow key and as a gamepad button.
 */
class NavDeduper(private val windowMs: Long = 80, private val now: () -> Long = System::currentTimeMillis) {
    private var last: NavAction? = null
    private var lastSource: NavSource? = null
    private var lastAt = 0L

    @Synchronized
    fun accept(action: NavAction, source: NavSource): Boolean {
        val t = now()
        val duplicate = action == last && source != lastSource && t - lastAt < windowMs
        if (!duplicate) { last = action; lastSource = source; lastAt = t }
        return !duplicate
    }
}
