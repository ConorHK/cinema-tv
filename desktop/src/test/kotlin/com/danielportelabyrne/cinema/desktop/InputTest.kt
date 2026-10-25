package com.danielportelabyrne.cinema.desktop

import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import com.danielportelabyrne.cinema.desktop.input.KeyMap
import com.danielportelabyrne.cinema.desktop.input.NavAction
import org.junit.Assert.*
import org.junit.Test

@OptIn(InternalComposeUiApi::class)
class InputTest {
    private fun key(code: Key, char: Char = '\uFFFF', ctrl: Boolean = false, alt: Boolean = false, type: KeyEventType = KeyEventType.KeyDown) =
        KeyEvent(code, type, char.code, isCtrlPressed = ctrl, isAltPressed = alt)

    @Test fun arrowKeysNavigateInsteadOfTypingUndefinedCharacters() {
        listOf(Key.DirectionUp to NavAction.UP, Key.DirectionDown to NavAction.DOWN,
            Key.DirectionLeft to NavAction.LEFT, Key.DirectionRight to NavAction.RIGHT).forEach { (code, action) ->
            val event = key(code)
            assertNull(KeyMap.character(event))
            assertEquals(action, KeyMap.action(event))
        }
    }

    @Test fun printableLettersPunctuationAndSpacesCanBeTyped() {
        assertEquals('d', KeyMap.character(key(Key.D, 'd')))
        assertEquals('É', KeyMap.character(key(Key.E, 'É')))
        assertEquals('_', KeyMap.character(key(Key.Minus, '_')))
        assertEquals(' ', KeyMap.character(key(Key.Spacebar, ' ')))
    }

    @Test fun shortcutsControlsAndReleasesDoNotBecomeText() {
        assertNull(KeyMap.character(key(Key.F, 'f', ctrl = true)))
        assertNull(KeyMap.character(key(Key.A, 'a', alt = true)))
        assertNull(KeyMap.character(key(Key.Enter, '\n')))
        assertNull(KeyMap.character(key(Key.Backspace, '\b')))
        assertNull(KeyMap.character(key(Key.Tab, '\t')))
        assertNull(KeyMap.character(key(Key.ShiftLeft)))
        assertNull(KeyMap.character(key(Key.D, 'd', type = KeyEventType.KeyUp)))
    }
}
