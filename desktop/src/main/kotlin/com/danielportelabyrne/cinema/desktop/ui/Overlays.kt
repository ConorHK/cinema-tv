package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.stream.LoadingStep
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Sheet
import com.danielportelabyrne.cinema.desktop.StreamLoading

/**
 * The TV's InfoSheet: a calm centred card for details and confirmations. An eyebrow, the title, a subtitle chip, the
 * lines, then the actions as pills one above the other (each with its second line), and Close last.
 */
@Composable
fun InfoSheet(model: CinemaModel, sheet: Sheet) {
    val first = remember(sheet) { FocusRequester() }
    LaunchedEffect(sheet) { focusSoon({ first.tryFocus() }) }
    Box(Modifier.fillMaxSize().background(Color(0x8C1C150F)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(min = 820.dp, max = 1100.dp).clip(RoundedCornerShape(40.dp)).background(Cin.SHEET).padding(horizontal = 70.dp, vertical = 60.dp)) {
            T(sheet.kicker.uppercase(), ts(20, Cin.SAFFRON, weight = 600, tracking = 0.18f))
            T(sheet.title, ts(52, Cin.CREAM, serif = true), maxLines = 2, modifier = Modifier.padding(top = 10.dp))
            sheet.subtitle?.let {
                Box(Modifier.padding(top = 18.dp).clip(RoundedCornerShape(16.dp)).background(Cin.SAFFRON.copy(alpha = 0.14f)).padding(horizontal = 18.dp, vertical = 7.dp)) {
                    T(it.uppercase(), ts(17, Cin.SAFFRON, weight = 600, tracking = 0.12f), maxLines = 2)
                }
            }
            sheet.lines.forEach { T(it, ts(26, Cin.MUTED, lineHeight = 1.45f), modifier = Modifier.padding(top = 18.dp)) }
            Spacer(Modifier.height(34.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val all = sheet.actions + com.danielportelabyrne.cinema.desktop.SheetAction("Close") { }
                all.forEachIndexed { i, a ->
                    SheetButton(a.label, a.sublabel, a.primary, if (i == 0) first else null) { model.sheet = null; a.run() }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, sub: String?, primary: Boolean, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when { focused -> Cin.CREAM; primary -> Cin.TERRA.copy(alpha = 0.85f); else -> Cin.GLASS_STRONG }
    val fg = if (focused) Cin.INK else if (primary) Color(0xFF1D0D05) else Cin.CREAM
    Column(
        Modifier.widthIn(min = 520.dp).bloom(Cin.CREAM.copy(alpha = 0.3f), 40.dp, 40.dp, 4.dp, enabled = focused)
            .clip(RoundedCornerShape(40.dp)).background(bg)
            .onFocusChanged { focused = it.isFocused }.tvFocusable(requester) { onClick() }
            .padding(horizontal = 38.dp, vertical = if (sub == null) 22.dp else 14.dp),
    ) {
        T(label, ts(25, fg, weight = 600), maxLines = 1)
        sub?.let { T(it, ts(19, fg.copy(alpha = 0.75f)), maxLines = 1) }
    }
}

/** A short message along the bottom of the screen, for a few seconds (the TV's toasts). */
@Composable
fun ToastLayer(text: String?) {
    if (text == null) return
    Box(Modifier.fillMaxSize().padding(bottom = 70.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.widthIn(max = 1300.dp).clip(RoundedCornerShape(40.dp)).background(Color(0xF22A1F17)).padding(horizontal = 40.dp, vertical = 22.dp)) {
            T(text, ts(24, Cin.CREAM, weight = 500), maxLines = 3)
        }
    }
}

/**
 * A Cinema progress bar (`.dl .bar`): a faint cream track with a stripe-set fill. With no [progress] and [sweeping], a
 * soft-edged striped segment sweeps along the track; with neither, it is just the track, never a made-up figure.
 */
@Composable
fun CinemaBar(progress: Float?, sweeping: Boolean, modifier: Modifier = Modifier, height: Dp = 14.dp, trackAlpha: Float = 0.08f) {
    val still = LocalInspectionMode.current
    val t = if (sweeping && progress == null && !still) rememberInfiniteTransition().animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart)).value else 0.3f
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(Cin.CREAM.copy(alpha = trackAlpha))) {
        when {
            progress != null -> if (progress > 0f) Canvas(Modifier.fillMaxSize().clip(RoundedCornerShape(50))) {
                val w = maxOf(size.height, size.width * progress.coerceIn(0f, 1f))
                drawRect(stripeBrush(true, size.height), size = androidx.compose.ui.geometry.Size(w, size.height))
            }
            sweeping -> Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                val seg = size.width * 0.22f
                val x = -seg + (size.width + seg) * t
                drawRect(stripeBrush(true, size.height), topLeft = Offset(x, 0f), size = androidx.compose.ui.geometry.Size(seg, size.height))
                drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.3f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent, startX = x, endX = x + seg), blendMode = BlendMode.DstIn)
            }
        }
    }
}

/**
 * The stream loading screen (`30-stream-loading-overlay`): the film's backdrop on the right, "Now loading", the title,
 * the status line, a sweeping striped bar, the three-step trail, and "B to cancel".
 */
@Composable
fun StreamLoadingScreen(s: StreamLoading) {
    Box(Modifier.fillMaxSize().background(Cin.BG)) {
        BackdropLayer(s.backdrop, 1060, detail = true)
        Ribbon(listOf(300f to -60f, 300f to 150f, 380f to 230f, 560f to 230f, 640f to 310f, 640f to 560f), 100f, Modifier.fillMaxSize().drawWithContent {
            drawContent(); drawRect(Brush.verticalGradient(0f to Color.Black, 0.22f to Color.Black, 0.5f to Color.Transparent), blendMode = BlendMode.DstIn)
        }.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.95f })
        Column(Modifier.align(Alignment.BottomStart).padding(start = 112.dp, bottom = 120.dp).width(900.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(Cin.SAFFRON.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(Cin.SAFFRON))
                }
                Spacer(Modifier.width(8.dp))
                T("NOW LOADING", ts(17, Cin.SAFFRON, weight = 600, tracking = 0.24f))
            }
            T(s.title, titleStyle(if (s.title.length > 22) 96 else 132), maxLines = 2, modifier = Modifier.padding(top = 26.dp))
            T(s.message, ts(28, Cin.READING, weight = 300), maxLines = 2, modifier = Modifier.padding(top = 28.dp))
            CinemaBar(null, sweeping = true, modifier = Modifier.padding(top = 40.dp).width(780.dp), height = 16.dp, trackAlpha = 0.1f)
            Row(Modifier.padding(top = 34.dp), verticalAlignment = Alignment.CenterVertically) {
                LoadingStep.entries.forEachIndexed { i, step ->
                    val lit = step.ordinal <= s.step.ordinal
                    if (i > 0) Spacer(Modifier.width(44.dp))
                    Box(Modifier.size(10.dp).clip(CircleShape).background(if (lit) Cin.SAFFRON else Cin.DIM.copy(alpha = 0.5f)))
                    Spacer(Modifier.width(12.dp))
                    T(step.label, ts(21, if (step == s.step) Cin.SAFFRON else if (lit) Cin.MUTED else Cin.DIM))
                }
            }
        }
        KeyHints(listOf("B" to "to cancel"), Modifier.align(Alignment.BottomEnd).padding(end = 96.dp, bottom = 110.dp))
    }
}

/** The stripe-set ribbon: five bands following [points] (a polyline in mockup pixels), outer maroon to inner saffron. */
@Composable
fun Ribbon(points: List<Pair<Float, Float>>, width: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val sx = size.width / 1920f; val sy = size.height / 1080f
        val path = androidx.compose.ui.graphics.Path().apply {
            points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x * sx, y * sy) else lineTo(x * sx, y * sy) }
        }
        val colours = listOf(Cin.STRIPE_MAROON, Cin.STRIPE_RED, Cin.STRIPE_TERRA, Cin.STRIPE_ORANGE, Cin.STRIPE_SAFFRON)
        colours.forEachIndexed { i, c ->
            drawPath(path, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = width * sx * (colours.size - i) / colours.size,
                join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}
