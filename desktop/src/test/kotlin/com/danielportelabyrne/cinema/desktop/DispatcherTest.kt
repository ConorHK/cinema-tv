package com.danielportelabyrne.cinema.desktop

import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import com.danielportelabyrne.cinema.desktop.ui.Dispatcher
import com.danielportelabyrne.cinema.desktop.ui.FocusHub
import com.danielportelabyrne.cinema.desktop.ui.KeyboardRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

@OptIn(InternalComposeUiApi::class)
class DispatcherTest {
    private fun model(): CinemaModel {
        val home = Files.createTempDirectory("cinema-input-test").toFile()
        return CinemaModel(DesktopPlatform(home, home.resolve("cache")), CoroutineScope(Dispatchers.Unconfined), Dispatchers.Unconfined)
    }
    private fun event(key: Key, char: Char = '\uFFFF') = KeyEvent(key, KeyEventType.KeyDown, char.code)

    @Test fun followPageReceivesTextWhileArrowsStillMoveFocus() {
        val model = model().apply { page = Page.LbFollow() }
        var text = ""
        model.typingTarget = { c -> text = if (c == null) text.dropLast(1) else text + c }
        val dispatcher = Dispatcher(model, FocusHub())
        var moved: FocusDirection? = null
        dispatcher.focus = object : FocusManager {
            override fun clearFocus(force: Boolean) {}
            override fun moveFocus(focusDirection: FocusDirection): Boolean { moved = focusDirection; return true }
        }
        assertTrue(dispatcher.key(event(Key.D, 'd')))
        assertTrue(dispatcher.key(event(Key.DirectionRight)))
        assertEquals("d", text)
        assertEquals(FocusDirection.Right, moved)
        assertTrue(dispatcher.key(event(Key.Backspace, '\b')))
        assertEquals("", text)
    }

    @Test fun nativeTextEntryReceivesKeyboardTextWithoutTypingBehindIt() {
        val model = model().apply { page = Page.LbFollow() }
        var underlying = ""
        model.typingTarget = { underlying += it }
        val request = KeyboardRequest("Name") {}
        model.keyboard = request
        val dispatcher = Dispatcher(model, FocusHub())
        dispatcher.key(event(Key.A, 'a'))
        dispatcher.key(event(Key.Spacebar, ' '))
        dispatcher.key(event(Key.Backspace, '\b'))
        assertEquals("a", request.text)
        assertEquals("", underlying)
    }

    @Test fun quitConfirmationDoesNotEditThePageBehindIt() {
        val model = model().apply { page = Page.LbFollow(); confirmQuit = true }
        var changed = false
        model.typingTarget = { changed = true }
        val dispatcher = Dispatcher(model, FocusHub())
        assertFalse(dispatcher.key(event(Key.A, 'a')))
        assertFalse(changed)
    }
}
