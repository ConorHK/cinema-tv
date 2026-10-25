package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.loadXmlImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.xml.sax.InputSource

// --- Text ------------------------------------------------------------------------------------------

@Composable
fun T(text: String, style: TextStyle, modifier: Modifier = Modifier, maxLines: Int = Int.MAX_VALUE, color: Color? = null) =
    BasicText(text, modifier, style = if (color != null) style.copy(color = color) else style, maxLines = maxLines, overflow = TextOverflow.Ellipsis)

// --- Icons (the TV's own vector drawables, app/src/main/res/drawable/ic_cin_*.xml) -----------------

object Icons {
    private val cache = HashMap<String, ImageVector?>()
    fun get(name: String): ImageVector? = cache.getOrPut(name) {
        Icons::class.java.getResourceAsStream("/icons/$name.xml")?.use {
            try { loadXmlImageVector(InputSource(it), Density(1f)) } catch (_: Exception) { null }
        }
    }
}

/** One of the TV's icons ("search", "play", "film"...), tinted, [size] mockup pixels square. */
@Composable
fun Icon(name: String, size: Dp, tint: Color?, modifier: Modifier = Modifier) {
    val vector = Icons.get("ic_cin_$name") ?: Icons.get(name) ?: return
    Image(rememberVectorPainter(vector), null, modifier.size(size), colorFilter = tint?.let { ColorFilter.tint(it) })
}

/** The Cinema mark: five rings, maroon to saffron, with a dark play triangle (cinema_mark.xml). */
@Composable
fun Logo(size: Dp, modifier: Modifier = Modifier) {
    val vector = Icons.get("cinema_mark") ?: return
    Image(rememberVectorPainter(vector), "Cinema", modifier.size(size))
}

// --- The stripe set ----------------------------------------------------------------------------------

/** The five stripe colours as even bands: [horizontalBands] stacks them top to bottom (an eyebrow mark). */
fun stripeBrush(horizontalBands: Boolean, length: Float): Brush {
    val stops = Cin.STRIPES.flatMapIndexed { i, c -> listOf(i / 5f to c, (i + 1) / 5f to c) }.toTypedArray()
    return if (horizontalBands) Brush.verticalGradient(*stops, startY = 0f, endY = length) else Brush.horizontalGradient(*stops, startX = 0f, endX = length)
}

@Composable
fun Stripes(width: Dp, height: Dp, horizontalBands: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width, height).clip(RoundedCornerShape(50))) {
        drawRect(stripeBrush(horizontalBands, if (horizontalBands) size.height else size.width))
    }
}

// --- Glow --------------------------------------------------------------------------------------------

/**
 * A soft blurred bloom behind a rounded shape (the TV's Glows): [spread] grows it past the element's edge, [blur] softens it.
 * No outlines in the Cinema language; focus is light, not a ring.
 */
fun Modifier.bloom(color: Color, radius: Dp, blur: Dp, spread: Dp = 0.dp, offsetY: Dp = 0.dp, enabled: Boolean = true): Modifier =
    if (!enabled || color.alpha == 0f) this else drawBehind {
        drawIntoCanvas { canvas ->
            val paint = org.jetbrains.skia.Paint().apply {
                this.color = color.toArgb()
                isAntiAlias = true
                maskFilter = org.jetbrains.skia.MaskFilter.makeBlur(org.jetbrains.skia.FilterBlurMode.NORMAL, blur.toPx() / 2f)
            }
            val s = spread.toPx(); val r = radius.toPx() + s
            canvas.nativeCanvas.drawRRect(org.jetbrains.skia.RRect.makeXYWH(-s, -s + offsetY.toPx(), size.width + 2 * s, size.height + 2 * s, r), paint)
        }
    }

// --- Eyebrow, meta, headings ---------------------------------------------------------------------------

/** `.eyebrow`: a small stripe pill, then tracked saffron capitals with faint dots between the parts. */
@Composable
fun Eyebrow(parts: List<String>, modifier: Modifier = Modifier, sizePx: Int = 17, color: Color = Cin.SAFFRON, mark: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (mark) { Stripes(40.dp, 10.dp, horizontalBands = true); Spacer(Modifier.width(14.dp)) }
        parts.forEachIndexed { i, p ->
            if (i > 0) { Spacer(Modifier.width(14.dp)); Box(Modifier.size(5.dp).alpha(0.6f).clip(CircleShape).background(color)); Spacer(Modifier.width(14.dp)) }
            T(p.uppercase(), ts(sizePx, color, weight = 600, tracking = 0.24f), maxLines = 1)
        }
    }
}

/** `.meta`: certificate box, then the facts with faint dots between, then the star score. Only what is known. */
@Composable
fun Meta(certificate: String?, items: List<String>, rating: String? = null, sizePx: Int = 23, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        var first = true
        fun sepNeeded() = !first
        certificate?.takeIf { it.isNotBlank() }?.let { c ->
            Box(Modifier.clip(RoundedCornerShape(6.dp)).background(Cin.CREAM.copy(alpha = 0.16f)).padding(horizontal = 9.dp, vertical = 1.dp)) {
                T(c, ts(sizePx * 19f / 23f, Cin.CREAM, weight = 600, tracking = 0.03f), maxLines = 1)
            }
            first = false
        }
        items.filter { it.isNotBlank() }.forEach {
            if (sepNeeded()) Dot()
            T(it, ts(sizePx, Cin.MUTED), maxLines = 1); first = false
        }
        rating?.let {
            if (sepNeeded()) Dot()
            Icon("star", 22.dp, Cin.SAFFRON); Spacer(Modifier.width(8.dp))
            T(it, ts(sizePx, Cin.CREAM, weight = 500))
        }
    }
}

@Composable
private fun Dot() {
    Spacer(Modifier.width(16.dp)); Box(Modifier.size(4.dp).clip(CircleShape).background(Cin.DIM)); Spacer(Modifier.width(16.dp))
}

/** `.rowhead`: vertical stripe marker, Fraunces 38px heading, then a quiet count. */
@Composable
fun RowHeading(text: String, count: String? = null, modifier: Modifier = Modifier, sizePx: Int = 38) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Stripes(14.dp, 30.dp, horizontalBands = false)
        Spacer(Modifier.width(18.dp))
        T(text, ts(sizePx, Cin.CREAM, serif = true, tracking = -0.01f), maxLines = 1)
        count?.let { Spacer(Modifier.width(18.dp)); T(it, ts(20, Cin.DIM, weight = 500, tracking = 0.04f), modifier = Modifier.padding(top = 8.dp)) }
    }
}

// --- Buttons -------------------------------------------------------------------------------------------

enum class ButtonKind { PRIMARY, TERRA, GLASS, DANGER }

/**
 * The Cinema pill (`.btn`), 76 tall (60 when [small]). Focused, it fills cream with dark text and a soft glow blooms
 * behind it; at rest it is glass. [ButtonKind.TERRA] keeps its terracotta gradient either way, so the page's call to
 * action reads even when focus is elsewhere.
 */
@Composable
fun CinemaButton(
    text: String,
    kind: ButtonKind = ButtonKind.GLASS,
    icon: String? = null,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    height: Dp = if (small) 60.dp else 76.dp,
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(50)
    val textColor = when {
        kind == ButtonKind.TERRA -> Color(0xFF1D0D05)
        focused -> if (kind == ButtonKind.DANGER) Color(0xFF8A2A12) else Cin.INK
        kind == ButtonKind.DANGER -> Cin.DANGER_TEXT
        else -> Cin.CREAM
    }
    val bg by animateColorAsState(if (focused) Cin.CREAM else if (kind == ButtonKind.PRIMARY) Cin.GLASS_STRONG else Cin.GLASS, tween(120))
    val glow = if (kind == ButtonKind.TERRA) Cin.TERRA.copy(alpha = 0.55f) else Cin.CREAM.copy(alpha = 0.32f)
    Row(
        modifier.height(height)
            .bloom(glow, height / 2, 44.dp, spread = 4.dp, enabled = focused || kind == ButtonKind.TERRA)
            .clip(shape)
            .then(if (kind == ButtonKind.TERRA) Modifier.background(Cin.terraGradient) else Modifier.background(bg))
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(requester, onFocus) { onClick() }
            .padding(start = if (icon != null) (if (small) 22.dp else 30.dp) else (if (small) 28.dp else 38.dp), end = if (small) 28.dp else 38.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, if (small) 24.dp else 30.dp, textColor); Spacer(Modifier.width(if (small) 10.dp else 14.dp)) }
        T(text, ts(if (small) 21 else 25, textColor, weight = 600), maxLines = 1)
    }
}

// --- Cards ---------------------------------------------------------------------------------------------

/**
 * A Cinema poster card (`.card`): a rounded poster with its name and a quiet subline beneath. Focused, the card grows to
 * 1.1 about a point a third of the way down the poster, a soft saffron bloom spreads behind it, the name turns cream
 * and semibold and the subline appears. [progress] draws the Continue row's saffron bar along the poster's foot.
 */
@Composable
fun PosterCard(
    title: String?,
    imageUrl: String?,
    width: Dp = 260.dp,
    height: Dp = 390.dp,
    subline: String? = null,
    badge: String? = null,
    chip: String? = null,
    progress: Float = 0f,
    modifier: Modifier = Modifier,
    requester: FocusRequester? = null,
    onFocus: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.1f else 1f, tween(160))
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.width(width)
            .graphicsLayer { scaleX = grow; scaleY = grow; transformOrigin = TransformOrigin(0.5f, 0.35f * height.value / (height.value + 70f)) }
    ) {
        Box(
            Modifier.size(width, height)
                .bloom(Cin.SAFFRON.copy(alpha = 0.42f), 16.dp, 68.dp, spread = 6.dp, enabled = focused)
                .bloom(Color.Black.copy(alpha = 0.45f), 16.dp, 40.dp, offsetY = 18.dp, enabled = !focused)
                .clip(shape)
                .onFocusChanged { focused = it.isFocused }
                .tvFocusable(requester, onFocus) { onClick() }
        ) {
            RemoteImage(imageUrl, Modifier.matchParentSize(), fallback = title, placeholder = Cin.PLACEHOLDER)
            // The poster's foot darkens a touch, so a badge or bar reads against it.
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.25f))))
            badge?.let { Badge(it, Modifier.align(Alignment.BottomStart).padding(12.dp)) }
            chip?.let {
                Box(Modifier.align(Alignment.TopStart).padding(12.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xB80C0806)).padding(horizontal = 10.dp, vertical = 5.dp)) {
                    T(it, ts(15, Cin.CREAM, weight = 700, tracking = 0.08f), maxLines = 1)
                }
            }
            if (progress > 0f) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp).background(Color(0x66000000))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0.03f, 1f)).background(Cin.SAFFRON))
            }
        }
        if (title != null) {
            Spacer(Modifier.height(16.dp))
            T(title, ts(21, if (focused) Cin.CREAM else Cin.CARD_TITLE, weight = if (focused) 600 else 500), maxLines = 1)
            T(subline.orEmpty(), ts(17, Cin.DIM), maxLines = 1, modifier = Modifier.padding(top = 4.dp).alpha(if (focused && subline != null) 1f else 0f))
        }
    }
}

/** `.badge`: dark glass pill, saffron capitals, a check. */
@Composable
fun Badge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xB80C0806)).padding(start = 9.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon("check", 15.dp, Cin.SAFFRON); Spacer(Modifier.width(7.dp))
        T(text.uppercase(), ts(14, Cin.SAFFRON, weight = 700, tracking = 0.14f), maxLines = 1)
    }
}

// --- Backgrounds ---------------------------------------------------------------------------------------

/**
 * A backdrop laid on the right of the screen, faded in from its left edge ([fadeTo] of its width is the fade), with
 * the page's two scrims over everything: the hero's or the title page's (`bdgrad` / `detail` in the mockup).
 */
@Composable
fun BackdropLayer(url: String?, widthPx: Int, detail: Boolean, modifier: Modifier = Modifier, fadeTo: Float = 0.38f) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.align(Alignment.TopEnd).width(widthPx.dp).fillMaxHeight()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(Brush.horizontalGradient(0f to Color.Transparent, fadeTo to Color.Black), blendMode = BlendMode.DstIn)
                }
        ) { RemoteImage(url, Modifier.fillMaxSize(), placeholder = Color.Transparent) }
        val h = if (detail) listOf(0f to 0.96f, 0.38f to 0.8f, 0.72f to 0.3f, 1f to 0.15f) else listOf(0f to 0.98f, 0.34f to 0.85f, 0.66f to 0.25f, 1f to 0.1f)
        val v = if (detail) listOf(0f to 0.35f, 0.25f to 0f, 0.48f to 0f, 0.86f to 0.96f, 1f to 0.96f) else listOf(0f to 0.55f, 0.22f to 0f, 0.5f to 0f, 1f to 0.96f)
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(*h.map { (s, a) -> s to Cin.BG.copy(alpha = a) }.toTypedArray())))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(*v.map { (s, a) -> s to Cin.BG.copy(alpha = a) }.toTypedArray())))
    }
}

/**
 * The ambient ground of pages without a featured picture (`.amb` + `ARCH`): a warm glow top right, teal bottom left,
 * and the faint 70s arch columns on the right, fading out toward the left.
 */
@Composable
fun Ambient(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        val sx = w / 1920f; val sy = h / 1080f
        drawRect(Cin.BG)
        drawRect(Brush.radialGradient(listOf(Cin.TERRA.copy(alpha = 0.16f), Color.Transparent), center = Offset(w * 0.88f, -h * 0.1f), radius = 750f * sx))
        drawRect(Brush.radialGradient(listOf(Cin.TEAL.copy(alpha = 0.14f), Color.Transparent), center = Offset(0f, h * 1.1f), radius = 800f * sx))
    }
    Canvas(
        modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.2f }
            .drawWithContent {
                drawContent()
                drawRect(Brush.linearGradient(0.3f to Color.Transparent, 0.8f to Color.Black, start = Offset(0f, 0f), end = Offset(size.width, size.height * 0.18f)), blendMode = BlendMode.DstIn)
            }
    ) {
        val sx = size.width / 1920f; val sy = size.height / 1080f
        fun arch(x: Float, top: Float, r: Float, color: Color, a: Float = 1f) {
            val p = Path().apply {
                moveTo(x * sx, 1100 * sy); lineTo(x * sx, top * sy)
                arcTo(androidx.compose.ui.geometry.Rect(x * sx, (top - r) * sy, (x + 2 * r) * sx, (top + r) * sy), 180f, 180f, false)
                lineTo((x + 2 * r) * sx, 1100 * sy); close()
            }
            drawPath(p, color.copy(alpha = a))
        }
        arch(1170f, 560f, 150f, Color(0xFFE0A93A))
        arch(1400f, 470f, 130f, Color(0xFFC4532A))
        arch(1640f, 720f, 140f, Color(0xFF6E2214))
        arch(1290f, 820f, 160f, Color(0xFF4D5B2C), 0.9f)
    }
}

/** `key()` hints along the bottom: a glass cap, then what it does. */
@Composable
fun KeyHints(hints: List<Pair<String, String>>, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
        hints.forEach { (k, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(9.dp)).background(Cin.GLASS_STRONG).padding(horizontal = 12.dp, vertical = 5.dp)) {
                    T(k, ts(17, Cin.CREAM, weight = 600))
                }
                Spacer(Modifier.width(10.dp))
                T(label, ts(18, Cin.DIM, tracking = 0.04f))
            }
        }
    }
}

/** The saffron progress line used in a few places (now playing). */
@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(7.dp)).background(Cin.CREAM.copy(alpha = 0.08f))) {
        Canvas(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0.01f, 1f)).clip(RoundedCornerShape(7.dp))) {
            drawRect(stripeBrush(horizontalBands = true, length = size.height))
        }
    }
}

