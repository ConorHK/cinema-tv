package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Field
import com.danielportelabyrne.cinema.desktop.SetupRow
import com.danielportelabyrne.cinema.desktop.Step

/**
 * First run and Settings, laid out like the TV's SetupFlow (`page`): a column 200px in, centred on the screen, with a
 * terracotta step eyebrow, an 84px serif title (128 for the welcome), a muted body, brown pill fields, chips, rows
 * with buttons, a status line, and the terracotta main button with a glass second one.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SetupScreen(model: CinemaModel) {
    val s = model.setup
    val first = remember { FocusRequester() }
    LaunchedEffect(s.token, model.keyboard == null, s.quickConnectOpen, model.sheet == null) {
        if (model.keyboard == null && !s.quickConnectOpen && model.sheet == null) focusSoon({ first.tryFocus() })
    }
    Box(Modifier.fillMaxSize().background(Cin.BG)) {
        Column(
            Modifier.align(Alignment.CenterStart).verticalScroll(rememberScrollState()).padding(start = 200.dp, end = 200.dp, top = 110.dp, bottom = 110.dp)
        ) {
            var firstUsed = false
            fun req(): FocusRequester? = if (!firstUsed) { firstUsed = true; first } else null
            s.eyebrow()?.let { T(it, ts(22, Cin.TERRA, weight = 600, tracking = 0.2f)) }
            T(s.title(), titleStyle(if (s.step == Step.WELCOME) 128 else 84), modifier = Modifier.padding(top = 10.dp))
            s.body?.let { T(it, ts(30, Cin.MUTED, lineHeight = 1.4f), modifier = Modifier.padding(top = 22.dp, bottom = 14.dp).widthIn(max = 1500.dp)) }
            if (s.chips.isNotEmpty() && s.step != Step.SEERR) FlowRow(Modifier.padding(top = 18.dp, bottom = 18.dp).widthIn(max = 1500.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                s.chips.forEach { (label, run) -> CinemaButton(label, small = true, height = 64.dp) { run() } }
            }
            s.fields.forEachIndexed { i, f ->
                FieldPill(f, if (s.chips.isEmpty() || s.step == Step.SEERR || i > 0) req() else null) { s.edit(f) }
                s.notes[i]?.let { T(it, ts(22, Cin.DIM), modifier = Modifier.padding(top = 6.dp).widthIn(max = 1500.dp)) }
            }
            if (s.chips.isNotEmpty() && s.step == Step.SEERR) Row(Modifier.padding(top = 18.dp)) {
                s.chips.forEach { (label, run) -> CinemaButton(label, small = true, height = 64.dp) { run() } }
            }
            s.rows.forEach { row -> RowCard(row, if (s.fields.isEmpty()) req() else null) }
            if (s.status.isNotEmpty()) T(s.status, ts(24, Cin.STATUS), modifier = Modifier.padding(top = 18.dp).widthIn(max = 1500.dp))
            if (s.primary != null || s.secondary != null) Row(Modifier.padding(top = 40.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                s.primary?.let { (label, icon) -> CinemaButton(label, ButtonKind.TERRA, icon, height = 84.dp, requester = req()) { s.pressPrimary() } }
                s.secondary?.let { label -> CinemaButton(label, height = 84.dp, requester = req()) { s.pressSecondary() } }
            }
        }
        if (s.quickConnectOpen) QuickConnectSheet(model)
    }
}

/** A field: a brown pill that warms when focused; choosing it opens the keyboard. */
@Composable
private fun FieldPill(f: Field, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.padding(top = 20.dp).size(1100.dp, 104.dp).clip(RoundedCornerShape(52.dp))
            .background(if (focused) Cin.FIELD_FOCUS else Cin.FIELD)
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(requester) { onClick() }
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val shown = if (f.secret) "•".repeat(f.value.length) else f.value
        T(shown.ifEmpty { f.hint }, ts(30, if (shown.isEmpty()) Cin.DIM else Cin.CREAM), maxLines = 1)
    }
}

/** One line of the Settings overview (or a found client): a name, a detail line, and buttons on the right. */
@Composable
private fun RowCard(row: SetupRow, requester: FocusRequester?) {
    Row(
        Modifier.padding(top = 16.dp).width(1500.dp).clip(RoundedCornerShape(30.dp)).background(Cin.GLASS).padding(start = 36.dp, top = 22.dp, end = 28.dp, bottom = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            T(row.name, ts(30, Cin.CREAM, weight = 600))
            T(row.detail, ts(23, Cin.MUTED), maxLines = 2, modifier = Modifier.padding(top = 4.dp))
        }
        row.buttons.forEachIndexed { i, b ->
            Spacer(Modifier.width(16.dp))
            CinemaButton(b.label, when { b.danger -> ButtonKind.DANGER; b.accent -> ButtonKind.TERRA; else -> ButtonKind.GLASS }, small = true, height = 64.dp,
                requester = if (i == 0) requester else null) { b.run() }
        }
    }
}

/** "Approve this device": the Quick Connect code, large, while the app waits for it to be approved elsewhere. */
@Composable
private fun QuickConnectSheet(model: CinemaModel) {
    val s = model.setup
    val cancel = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusSoon({ cancel.tryFocus() }) }
    Box(Modifier.fillMaxSize().background(Cin.BG.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(1120.dp).clip(RoundedCornerShape(60.dp)).background(Cin.SHEET).padding(horizontal = 70.dp, vertical = 64.dp)) {
            T("CONNECT JELLYFIN", ts(22, Cin.SAFFRON, weight = 600, tracking = 0.14f))
            T("Approve this device", ts(56, Cin.CREAM, serif = true), modifier = Modifier.padding(top = 8.dp))
            T("On a phone or computer, open Jellyfin and sign in. Then open the user menu, choose Quick Connect and type this code.",
                ts(30, Cin.KEY_TEXT, weight = 500, lineHeight = 1.15f), modifier = Modifier.padding(top = 18.dp))
            val code = s.quickConnectCode
            T(code?.chunked(3)?.joinToString(" ") ?: "· · ·", ts(112, Cin.CREAM, weight = 300, tracking = 0.1f), modifier = Modifier.padding(top = 30.dp))
            T(if (code == null) "Asking Jellyfin for a code…" else "Waiting for approval…", ts(28, Cin.SAFFRON, weight = 500), modifier = Modifier.padding(top = 26.dp))
            Spacer(Modifier.height(30.dp))
            CinemaButton("Cancel", ButtonKind.PRIMARY, height = 104.dp, modifier = Modifier.widthIn(min = 300.dp), requester = cancel) { s.cancelQuickConnect() }
        }
    }
}
