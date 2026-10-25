package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.Catalog
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Entry
import com.danielportelabyrne.cinema.desktop.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// --- The QWERTY keys (mockup 22) ---------------------------------------------------------------------

/** What a key types; [CLEAR], [SPACE] and [DELETE] are the wide keys along the bottom. */
object Keys {
    const val CLEAR = "clear"; const val SPACE = "space"; const val DELETE = "delete"; const val SHIFT = "shift"
    val rows = listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm")
    /** For addresses and names: what the letter rows leave out. */
    const val SYMBOLS = ".:/-_@"
}

/**
 * The in-app keyboard from the TV's search page: 96×84 glass keys on a 98px grid, the letter rows stepped in as on a
 * real keyboard, then Clear, Space and Delete. [symbols] adds a row for addresses. The focused key is lit cream and
 * grows. [first] is the "q" key.
 */
@Composable
fun QwertyKeys(onKey: (String) -> Unit, first: FocusRequester, symbols: Boolean = false, shift: Boolean = false, doneLabel: String? = null, onDone: () -> Unit = {}) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Keys.rows.forEachIndexed { r, row ->
            Row(Modifier.padding(start = (if (r >= 2) (r - 1) * 49 else 0).dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                row.forEachIndexed { c, ch ->
                    KeyCap(if (shift) ch.uppercase() else ch.toString(), 96.dp, requester = if (r == 1 && c == 0) first else null) { onKey(ch.toString()) }
                }
            }
        }
        if (symbols) Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            KeyCap("⇧", 96.dp, lit = shift) { onKey(Keys.SHIFT) }
            Keys.SYMBOLS.forEach { ch -> KeyCap(ch.toString(), 96.dp) { onKey(ch.toString()) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyCap("Clear", 190.dp, icon = "x", wide = true) { onKey(Keys.CLEAR) }
            KeyCap("Space", if (doneLabel != null) 370.dp else 580.dp, wide = true) { onKey(Keys.SPACE) }
            KeyCap("Delete", 186.dp, icon = "bksp", wide = true) { onKey(Keys.DELETE) }
            if (doneLabel != null) KeyCap(doneLabel, 198.dp, wide = true, accent = true) { onDone() }
        }
    }
}

@Composable
private fun KeyCap(label: String, width: Dp, requester: FocusRequester? = null, icon: String? = null, wide: Boolean = false, lit: Boolean = false, accent: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.12f else 1f, tween(120))
    val text = when { focused -> Cin.INK; accent -> Color(0xFF1D0D05); wide -> Cin.MUTED; lit -> Cin.SAFFRON; else -> Cin.KEY_TEXT }
    Row(
        Modifier.size(width, 84.dp).scale(grow)
            .bloom(Cin.CREAM.copy(alpha = 0.16f), 18.dp, 30.dp, spread = 6.dp, enabled = focused)
            .clip(RoundedCornerShape(18.dp))
            .then(if (accent && !focused) Modifier.background(Cin.terraGradient) else Modifier.background(if (focused) Cin.CREAM else Cin.GLASS))
            .onFocusChanged { focused = it.isFocused }
            .tvFocusable(requester) { onClick() },
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, 24.dp, text); Spacer(Modifier.width(10.dp)) }
        T(label, if (wide) ts(20, text, weight = if (focused || accent) 600 else 400, tracking = 0.05f) else ts(30, text, weight = if (focused) 600 else 400), maxLines = 1)
    }
}

/** The long search field (`.search`): a saffron search icon and caret, then what has been typed or a grey prompt. */
@Composable
fun SearchField(text: String, prompt: String, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(56.dp)).background(Color(0x17FFF2DE)).padding(horizontal = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon("search", 40.dp, Cin.SAFFRON)
        leading?.let { Spacer(Modifier.width(22.dp)); it() }
        Spacer(Modifier.width(22.dp))
        if (text.isEmpty()) {
            Box(Modifier.size(3.dp, 48.dp).background(Cin.SAFFRON)); Spacer(Modifier.width(22.dp))
            T(prompt, ts(36, Color(0xFF8F8272), weight = 300), maxLines = 1)
        } else {
            T(text, ts(36, Cin.CREAM, weight = 400), maxLines = 1)
            Spacer(Modifier.width(6.dp)); Box(Modifier.size(3.dp, 48.dp).background(Cin.SAFFRON))
        }
    }
}

// --- Search ------------------------------------------------------------------------------------------

private class SearchResults(val query: String, val top: List<Entry>, val rows: List<Pair<String, List<Entry>>>)

/**
 * Search (`22-search`): the field, the keyboard on the left, trending titles and genres on the right; results arrive as
 * you type, as list cards on the right and poster rows below. Library and catalogue are searched together.
 */
@Composable
fun SearchPage(model: CinemaModel, art: Art) {
    val first = remember { FocusRequester() }
    val state = model.state(Page.Search)
    val scope = rememberCoroutineScope()
    model.pageEntry = { first.tryFocus() }
    LaunchedEffect(Unit) { if (!model.railOpen) focusSoon({ state.focus.restore() }, { first.tryFocus() }) }
    val query = model.search.trim()
    var results by remember { mutableStateOf<SearchResults?>(null) }
    LaunchedEffect(query) {
        if (query.length < 2) { results = null; return@LaunchedEffect }
        delay(500)
        val found = withContext(Dispatchers.IO) {
            val mine = model.searchLibraryLoaded(query)
            val shelves = Catalog.searchSpecs(query).map { spec -> runCatching { model.catalog.shelf(spec) }.getOrNull() }.filterNotNull()
            mine to shelves
        }
        val library = model.libraryEntries(found.first)
        val shelves = found.second.map { it.heading to it.titles.map { t -> model.entry(t) } }
        val top = (library + shelves.flatMap { it.second }).distinctBy { it.title.name.lowercase() + it.title.year }.take(5)
        results = SearchResults(query, top, listOf("In your library" to library) + shelves)
    }
    val trending by produceState<List<Entry>>(emptyList()) {
        value = withContext(Dispatchers.IO) { runCatching { model.catalog.shelf(Catalog.homePage().first()).titles.take(3) }.getOrNull().orEmpty() }.map { model.entry(it) }
    }
    val r = results?.takeIf { it.query == query }

    Box(Modifier.fillMaxSize()) {
        Ambient()
        LazyColumn(state = state.column, contentPadding = PaddingValues(bottom = 120.dp)) {
            item(key = "stage") {
                Box(Modifier.fillMaxWidth().height(1000.dp)) {
                    SearchField(model.search, "Search films, series and documentaries", Modifier.padding(start = 176.dp, top = 64.dp, end = PAGE_END.dp))
                    Eyebrow(listOf("Type to search"), color = Cin.DIM, modifier = Modifier.offset(176.dp, 222.dp))
                    Box(Modifier.offset(176.dp, 300.dp)) {
                        QwertyKeys(first = first, onKey = { k ->
                            when (k) {
                                Keys.CLEAR -> model.search = ""
                                Keys.SPACE -> model.search += " "
                                Keys.DELETE -> model.search = model.search.dropLast(1)
                                else -> model.search = (model.search + k).take(60)
                            }
                        })
                    }
                    Column(Modifier.offset(1232.dp, 230.dp).width(592.dp)) {
                        if (r == null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon("trend", 22.dp, Cin.DIM); Spacer(Modifier.width(14.dp))
                                T("TRENDING SEARCHES", ts(17, Cin.DIM, weight = 600, tracking = 0.24f))
                            }
                            trending.forEach { e -> Box(Modifier.padding(top = 18.dp)) { SearchRow(model, art, e, trending.map { it.title }) } }
                            T("BROWSE BY THEME", ts(17, Cin.DIM, weight = 600, tracking = 0.24f), modifier = Modifier.padding(top = 40.dp))
                            Themes(model)
                        } else {
                            T("RESULTS FOR “${r.query.uppercase()}”", ts(17, Cin.DIM, weight = 600, tracking = 0.24f), maxLines = 1)
                            if (r.top.isEmpty()) T("Nothing found for “${r.query}”", ts(22, Cin.MUTED), modifier = Modifier.padding(top = 18.dp))
                            r.top.forEach { e -> Box(Modifier.padding(top = 18.dp)) { SearchRow(model, art, e, r.top.map { it.title }) } }
                        }
                    }
                    KeyHints(listOf("A" to "Select", "X" to "Delete", "Y" to "Space", "B" to "Back"), Modifier.align(Alignment.BottomStart).padding(start = 176.dp, bottom = 48.dp))
                }
            }
            r?.rows?.filter { it.second.isNotEmpty() }?.let { rows ->
                itemsIndexed(rows, key = { _, p -> "results:${p.first}" }) { i, (heading, entries) ->
                    ShelfRow(state, "results:$heading", heading, "${entries.size}", { scope.launch { state.column.glideTo(i + 1) } }) {
                        itemsIndexed(entries, key = { _, e -> e.title.key }) { _, e -> EntryCard(model, art, e, entries.map { it.title }, state.focus, "results:$heading:${e.title.key}") }
                    }
                }
            }
        }
    }
}

/** Real genres, two rows of three, each opening its own page. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Themes(model: CinemaModel) {
    val themes = listOf("drm", "trl", "scf", "cmy", "hst", "ani").mapNotNull { code -> Catalog.GENRES.firstOrNull { it.code == code } }
    FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        themes.forEach { g -> Pill(g.label) { model.go(Page.InGenre(g), remember = true) } }
    }
}

/** `.chip`: a glass pill, cream when focused. */
@Composable
fun Pill(label: String, height: Dp = 56.dp, accent: Boolean = false, requester: FocusRequester? = null, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier.height(height).clip(RoundedCornerShape(50))
            .then(if (accent && !focused) Modifier.background(Cin.terraGradient) else Modifier.background(if (focused) Cin.CREAM else Cin.GLASS_STRONG))
            .onFocusChanged { focused = it.isFocused }.tvFocusable(requester) { onClick() }.padding(horizontal = 30.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, ts(23, if (focused) Cin.INK else if (accent) Color(0xFF1D0D05) else Cin.CREAM, weight = 600), maxLines = 1) }
}

/** One list card on the search page's right side (`searchRow`): poster, name, "Film · 2026", chevron. */
@Composable
private fun SearchRow(model: CinemaModel, art: Art, e: Entry, related: List<Title>) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(104.dp)
            .bloom(Cin.CREAM.copy(alpha = 0.18f), 24.dp, 30.dp, spread = 4.dp, enabled = focused)
            .clip(RoundedCornerShape(24.dp)).background(if (focused) Cin.CREAM else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable { model.open(e, related) }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(e.title.posterUrl, Modifier.size(58.dp, 78.dp).clip(RoundedCornerShape(9.dp)))
        Column(Modifier.weight(1f).padding(start = 22.dp)) {
            T(e.title.name, ts(27, if (focused) Cin.INK else Cin.CREAM, weight = 500), maxLines = 1)
            T(listOfNotNull(if (e.title.kind == Kind.SHOW) "Series" else "Film", e.title.year?.toString(), if (e.library != null) "In your library" else null).joinToString(" · "),
                ts(18, if (focused) Cin.INK.copy(alpha = 0.7f) else Cin.DIM), maxLines = 1, modifier = Modifier.padding(top = 5.dp))
        }
        Icon("chev", 26.dp, if (focused) Cin.INK else Cin.DIM)
    }
}
