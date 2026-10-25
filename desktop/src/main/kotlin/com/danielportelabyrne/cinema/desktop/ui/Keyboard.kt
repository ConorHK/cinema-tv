package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.desktop.input.NavAction

/** Something to type: a server address, a username, a password. */
class KeyboardRequest(
    val title: String,
    initial: String = "",
    val secret: Boolean = false,
    val hint: String? = null,
    val doneLabel: String = "Done",
    val onDone: (String) -> Unit,
) {
    var text by mutableStateOf(initial)
    var shift by mutableStateOf(false)

    fun type(s: String) { text += if (shift) s.uppercase() else s; shift = false }
    fun typeRaw(c: Char) { text += c }
    fun backspace() { if (text.isNotEmpty()) text = text.dropLast(1) }

    fun key(k: String) = when (k) {
        Keys.CLEAR -> text = ""
        Keys.SPACE -> typeRaw(' ')
        Keys.DELETE -> backspace()
        Keys.SHIFT -> shift = !shift
        else -> type(k)
    }
}

/** What the pad's extra buttons do on the keyboard: X deletes, Y adds a space, Start is Done, LB/RB switch case. */
fun keyboardShortcut(action: NavAction, req: KeyboardRequest, done: () -> Unit): Boolean = when (action) {
    NavAction.ALT -> { req.backspace(); true }
    NavAction.SEARCH -> { req.typeRaw(' '); true }
    NavAction.MENU -> { done(); true }
    NavAction.PREV, NavAction.NEXT -> { req.shift = !req.shift; true }
    else -> false
}

/** The keyboard sheet: what has been typed in a dark pill, then the TV's QWERTY keys with a symbols row. */
@Composable
fun KeyboardSheet(req: KeyboardRequest, onDone: (String) -> Unit) {
    val first = remember { FocusRequester() }
    LaunchedEffect(req) { focusSoon({ first.tryFocus() }) }
    Box(Modifier.fillMaxSize().background(Cin.BG.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(48.dp)).background(Cin.SHEET).padding(56.dp), horizontalAlignment = Alignment.Start) {
            T(req.title, ts(44, Cin.CREAM, serif = true))
            Spacer(Modifier.height(24.dp))
            Row(
                Modifier.width(1004.dp).height(100.dp).clip(RoundedCornerShape(50.dp)).background(Color(0xFF1C1612)).padding(horizontal = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shown = if (req.secret) "•".repeat(req.text.length) else req.text
                if (shown.isEmpty()) T(req.hint ?: "", ts(34, Cin.DIM, weight = 300), maxLines = 1)
                else T(shown, ts(34, Cin.CREAM), maxLines = 1)
                Spacer(Modifier.width(6.dp)); Box(Modifier.size(3.dp, 42.dp).background(Cin.SAFFRON))
            }
            Spacer(Modifier.height(36.dp))
            QwertyKeys(onKey = { req.key(it) }, first = first, symbols = true, shift = req.shift, doneLabel = req.doneLabel) { onDone(req.text) }
            Spacer(Modifier.height(30.dp))
            KeyHints(listOf("X" to "Delete", "Y" to "Space", "LB" to "Capitals", "≡" to req.doneLabel, "B" to "Cancel"), Modifier.fillMaxWidth())
        }
    }
}
