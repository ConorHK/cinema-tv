package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

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

    fun typeRaw(c: Char) { text += c }
    fun backspace() { if (text.isNotEmpty()) text = text.dropLast(1) }
}

/** A native text-entry dialog. Keeping a focused editable control lets Steam's OSK send committed text by IME. */
@Composable
fun KeyboardSheet(req: KeyboardRequest, onDone: (String) -> Unit) {
    val editor = remember { FocusRequester() }
    LaunchedEffect(req) { focusSoon({ editor.tryFocus() }) }
    Box(Modifier.fillMaxSize().background(Cin.BG.copy(alpha = 0.88f)), contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(48.dp)).background(Cin.SHEET).padding(56.dp), horizontalAlignment = Alignment.Start) {
            T(req.title, ts(44, Cin.CREAM, serif = true))
            Spacer(Modifier.height(24.dp))
            // A real editable control is required for Steam's OSK to deliver committed text. The old
            // display was only a Text composable, so the OSK had no active text-input target.
            BasicTextField(
                value = req.text,
                onValueChange = { req.text = it },
                singleLine = true,
                visualTransformation = if (req.secret) PasswordVisualTransformation() else VisualTransformation.None,
                textStyle = ts(34, Cin.CREAM),
                cursorBrush = SolidColor(Cin.SAFFRON),
                modifier = Modifier.width(1004.dp).height(100.dp).clip(RoundedCornerShape(50.dp))
                    .background(Color(0xFF1C1612)).focusRequester(editor).padding(horizontal = 40.dp),
                decorationBox = { inner ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (req.text.isEmpty()) T(req.hint ?: "", ts(34, Cin.DIM, weight = 300), maxLines = 1)
                        Box { inner() }
                    }
                },
            )
            Spacer(Modifier.height(36.dp))
            CinemaButton(req.doneLabel, ButtonKind.TERRA, height = 84.dp) { onDone(req.text) }
        }
    }
}
