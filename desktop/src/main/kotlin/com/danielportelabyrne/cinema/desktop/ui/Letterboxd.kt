package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.letterboxd.Lb
import com.danielportelabyrne.cinema.data.letterboxd.DesktopLetterboxd
import com.danielportelabyrne.cinema.data.letterboxd.LbDiaryEntry
import com.danielportelabyrne.cinema.data.letterboxd.LbFilm
import com.danielportelabyrne.cinema.data.letterboxd.LbList
import com.danielportelabyrne.cinema.data.letterboxd.LbMember
import com.danielportelabyrne.cinema.data.letterboxd.LbMemberFilm
import com.danielportelabyrne.cinema.data.letterboxd.LbProfile
import com.danielportelabyrne.cinema.data.letterboxd.LbReview
import com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.Companion.HOUR
import com.danielportelabyrne.cinema.data.letterboxd.LetterboxdRepository.Companion.MINUTE
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.Title
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Page
import com.danielportelabyrne.cinema.desktop.PageState
import com.danielportelabyrne.cinema.desktop.Route
import com.danielportelabyrne.cinema.desktop.Sheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

// --- Loading ---------------------------------------------------------------------------------------

/** A Letterboxd page as it loads: the saved copy first, then a fresh one when the saved one is old (`lb.load`). */
class LbState<T> {
    var value by mutableStateOf<T?>(null)
    var done by mutableStateOf(false)
}

@Composable
fun <T> lbLoad(model: CinemaModel, path: String?, maxAgeMs: Long, parse: (String) -> T?): LbState<T> {
    val state = remember(path) { LbState<T>() }
    DisposableEffect(path) {
        var alive = true
        if (path != null) model.lb.load(path, maxAgeMs, parse) { v -> if (alive) { if (v != null || state.value == null) state.value = v; state.done = true } }
        onDispose { alive = false }
    }
    return state
}

@OptIn(ExperimentalCoroutinesApi::class)
private val posterLookups = Dispatchers.IO.limitedParallelism(4)

/** A Letterboxd film's poster address (looked up once, four at a time, and remembered for good). */
@Composable
fun lbPoster(model: CinemaModel, slug: String?): String? {
    val url by produceState<String?>(null, slug) { if (slug != null) value = withContext(posterLookups) { runCatching { model.lb.posterUrl(slug) }.getOrNull() } }
    return url
}

/** Display names of followed members, once their profiles have loaded ("Daniel"). */
private val lbNames = HashMap<String, String>()
private fun lbName(user: String) = lbNames[user.lowercase()] ?: user

/** "2026-10-16" -> "16 Oct". */
fun lbDay(iso: String?): String? {
    if (iso == null || iso.length < 10) return null
    val m = iso.substring(5, 7).toIntOrNull()?.takeIf { it in 1..12 } ?: return null
    return "${iso.substring(8, 10).trimStart('0')} ${arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[m - 1]}"
}

/** "2906266" -> "2.9M", "412000" -> "412K". */
private fun lbCount(n: Long): String = when {
    n >= 1_000_000 -> String.format(Locale.UK, "%.1fM", n / 1e6).replace(".0M", "M")
    n >= 1_000 -> "${n / 1000}K"
    else -> "$n"
}

/** A Letterboxd film as a page title, until its own page says more (TMDB id, synopsis, backdrop). */
private fun lbTitle(f: LbFilm, poster: String?) = Title(
    key = "lb:" + f.slug, kind = Kind.MOVIE, name = f.name, year = f.year, overview = null, runtimeMin = null, genres = emptyList(),
    age = null, imdbScore = null, posterUrl = poster, backdropUrl = null, imdbId = null, tmdbId = null)

/**
 * Opens a Letterboxd film as an ordinary title page (Watch now, Stream now, the library badge): its Letterboxd page
 * gives the TMDB id, synopsis, running time and backdrop (`lbOpen`).
 */
fun lbOpen(model: CinemaModel, f: LbFilm, poster: String?) {
    model.toast("Opening ${f.name}…")
    model.bg({
        val info = model.lb.pageNow("/film/${f.slug}/", 7 * 24 * HOUR)?.let { Lb.film(it) }
        if (model.client.isConnected) model.client.library()
        info
    }) { info ->
        val p = info?.page
        val t = lbTitle(f, poster).copy(
            kind = if (info?.isTv == true) Kind.SHOW else Kind.MOVIE, tmdbId = info?.tmdbId?.toString(),
            overview = p?.synopsis, runtimeMin = p?.runtimeMin, genres = p?.genres.orEmpty().take(3), year = f.year ?: p?.year,
            backdropUrl = info?.backdrop?.replace("-1200-1200-675-675-crop", "-1920-1920-1080-1080-crop"))
        model.libraryVersion++
        model.open(model.entry(t), emptyList())
    }
}

// --- Pieces --------------------------------------------------------------------------------------------

/** Letterboxd's three dots (orange, green, blue). */
@Composable
fun LbDots(size: Dp = 12.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(size * 5 / 12)) {
        listOf(Color(0xFFFF8000), Color(0xFF00E054), Color(0xFF40BCF4)).forEach { Box(Modifier.size(size).clip(CircleShape).background(it)) }
    }
}

/** The page heading the Letterboxd pages share (`lbHeader`): the dots, the eyebrow words, and a big title. */
@Composable
private fun LbHeader(eyebrow: List<String>, title: String, size: Int = 84, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) { LbDots(); Spacer(Modifier.width(16.dp)); Eyebrow(eyebrow, mark = false) }
        T(title, titleStyle(size), maxLines = 1, modifier = Modifier.padding(top = 18.dp))
    }
}

/** A round picture, or the person's initial on a warm gradient while there is none. [ringed] when its card is focused. */
@Composable
fun Avatar(name: String, picture: String?, size: Dp, ringed: Boolean = false) {
    Box(Modifier.size(size + 24.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val r = size.toPx() / 2; val gap = 4.dp.toPx()
            drawCircle(Color(0xD90E0A08), r + gap / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(gap))
            if (ringed) {
                drawCircle(Cin.STRIPE_SAFFRON, r + gap * 1.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(gap))
                drawCircle(Cin.STRIPE_TERRA, r + gap * 2.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(gap))
            } else drawCircle(Cin.CREAM.copy(alpha = 0.18f), r + gap * 1.5f, style = androidx.compose.ui.graphics.drawscope.Stroke(gap))
        }
        Box(Modifier.size(size).clip(CircleShape).background(Brush.linearGradient(listOf(Cin.STRIPE_RED, Cin.SAFFRON))), contentAlignment = Alignment.Center) {
            T(name.trim().firstOrNull()?.uppercase() ?: "?", ts(size.value * 0.44f, Cin.CREAM, serif = true))
            if (picture != null) RemoteImage(picture, Modifier.matchParentSize(), placeholder = Color.Transparent)
        }
    }
}

/** A row of Letterboxd posters (`lbFilmRow`): [line] is what a focused poster shows, [chip] its corner tag. */
@Composable
private fun LbFilmRow(model: CinemaModel, state: PageState, key: String, heading: String, caption: String?, films: List<LbFilm>?, onEnter: () -> Unit = {},
                      line: (LbFilm) -> String? = { it.stars }, chip: (LbFilm) -> String? = { null }, empty: String = "Nothing here yet") {
    if (films == null || films.isEmpty()) {
        Column { RowHeading(heading, caption, Modifier.padding(start = PAGE_START.dp, top = 50.dp, bottom = 14.dp)); Note(if (films == null) "Loading…" else empty) }
        return
    }
    ShelfRow(state, key, heading, "${films.size}", onEnter) {
        itemsIndexed(films.take(40), key = { i, f -> "$i:${f.slug}" }) { i, f ->
            val poster = lbPoster(model, f.slug)
            val k = "$key:$i"
            PosterCard(f.name, poster, subline = line(f), chip = chip(f), requester = state.focus.requester(k), onFocus = { state.focus.last = k }) { lbOpen(model, f, poster) }
        }
    }
}

/** A list (`.lc`): its first five posters overlapped like a hand of cards, then its name, owner, size and likes. */
@Composable
private fun ListFan(model: CinemaModel, l: LbList, posterW: Int, step: Int, width: Int, focused: Boolean) {
    Box(Modifier.size(width.dp, (posterW * 3 / 2).dp)) {
        if (focused) Box(Modifier.size((posterW + 4 * step).dp, (posterW * 3 / 2).dp).bloom(Cin.SAFFRON.copy(alpha = 0.26f), 24.dp, 80.dp, 14.dp)
            .border(4.dp, Cin.CREAM, RoundedCornerShape(24.dp)))
        l.previews.take(5).withIndex().reversed().forEach { (i, f) ->
            val url = lbPoster(model, f.slug)
            RemoteImage(url, Modifier.offset((i * step).dp, 0.dp).size(posterW.dp, (posterW * 3 / 2).dp).clip(RoundedCornerShape(12.dp)))
        }
    }
}

@Composable
private fun ListCard(model: CinemaModel, l: LbList, requester: FocusRequester?, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(500.dp).onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.tvFocusable(requester) { onClick() }) {
        ListFan(model, l, 120, 76, 500, focused)
        T(l.name, ts(30, if (focused) Cin.CREAM else Color(0xFFE6D9C6), serif = true), maxLines = 1, modifier = Modifier.padding(top = 18.dp))
        T(listOfNotNull(l.ownerName ?: lbName(l.owner), l.count?.let { "$it films" }, l.likes?.let { "♥ $it" }).joinToString("  ·  "), ts(19, Cin.MUTED), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun LbListRow(model: CinemaModel, state: PageState, key: String, heading: String, caption: String?, lists: List<LbList>?, onEnter: () -> Unit = {}) {
    if (lists == null || lists.isEmpty()) { Column { RowHeading(heading, caption, Modifier.padding(start = PAGE_START.dp, top = 50.dp, bottom = 14.dp)); Note(if (lists == null) "Loading…" else "No lists yet") }; return }
    ShelfRow(state, key, heading, "${lists.size}", onEnter, gap = 56) {
        itemsIndexed(lists, key = { i, l -> "$i:${l.path}" }) { i, l -> ListCard(model, l, state.focus.requester("$key:$i"), { state.focus.last = "$key:$i" }) { model.go(Page.LbList(l.path), remember = true) } }
    }
}

/** A review (`.rv`): reviewer, their stars and like, the text in Fraunces and when it was written. Cream when focused. */
@Composable
private fun ReviewCard(r: LbReview, byFilm: Boolean, requester: FocusRequester?, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val footer = listOfNotNull(lbDay(r.date), r.likes?.let { n -> if (n >= 1000) String.format(Locale.UK, "%.1fK likes", n / 1000.0).replace(".0K", "K") else if (n == 1) "1 like" else "$n likes" }).joinToString(" · ")
    Column(
        Modifier.size(540.dp, 300.dp).clip(RoundedCornerShape(26.dp)).background(if (focused) Cin.CREAM else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.tvFocusable(requester) { onClick() }
            .padding(start = 30.dp, top = 28.dp, end = 30.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!byFilm) { Avatar(r.authorName, r.avatar, 52.dp); Spacer(Modifier.width(4.dp)) }
            Column {
                T(if (byFilm) r.film?.name ?: r.authorName else r.authorName, ts(22, if (focused) Cin.INK else Cin.CREAM, weight = 600), maxLines = 1)
                Row {
                    r.rating?.let { T(Lb.stars(it), ts(19, if (focused) Color(0xFFB07C22) else Cin.SAFFRON, tracking = 0.06f)) }
                    if (r.liked) T("  ♥", ts(19, Color(0xFFFF6F61)))
                }
            }
        }
        T(r.text, ts(26, if (focused) Color(0xFF2A1F17) else Color(0xFFE5D8C4), serif = true, weight = 300, lineHeight = 1.18f), maxLines = 4, modifier = Modifier.padding(top = 20.dp).weight(1f))
        T(footer, ts(17, if (focused) Color(0xFF7A6858) else Cin.DIM))
    }
}

@Composable
private fun LbReviewRow(model: CinemaModel, state: PageState?, key: String, heading: String?, reviews: List<LbReview>?, byFilm: Boolean = false, startPad: Int = PAGE_START, memory: com.danielportelabyrne.cinema.desktop.FocusMemory, onEnter: () -> Unit = {}) {
    if (reviews == null || reviews.isEmpty()) { Note(if (reviews == null) "Loading…" else "No reviews yet", start = startPad); return }
    ShelfRow(state, key, heading, "${reviews.size}", onEnter, gap = 32, startPad = startPad) {
        itemsIndexed(reviews.take(16)) { i, r ->
            ReviewCard(r, byFilm, memory.requester("$key:$i"), { memory.last = "$key:$i" }) {
                if (byFilm && r.film != null) lbOpen(model, r.film!!, null)
                else model.sheet = Sheet("REVIEW", r.authorName, listOfNotNull(r.rating?.let { Lb.stars(it) }, lbDay(r.date)).joinToString(" · ").ifBlank { null }, listOf(r.text), emptyList())
            }
        }
    }
}

// --- Letterboxd home ----------------------------------------------------------------------------------

/** Letterboxd home (`buildLetterboxd`): the followed members as cards, what they watched lately, their watchlists and likes. */
@Composable
fun LetterboxdHome(model: CinemaModel, art: Art) {
    val state = model.state(Page.Letterboxd)
    val scope = rememberCoroutineScope()
    val users = model.lb.following
    val first = remember { FocusRequester() }
    model.pageEntry = { first.tryFocus() }
    LaunchedEffect(Unit) { if (!model.railOpen) focusSoon({ state.focus.restore() }, { first.tryFocus() }) }
    Box(Modifier.fillMaxSize()) {
        Ambient()
        LazyColumn(state = state.column, contentPadding = PaddingValues(top = 52.dp, bottom = 120.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    LbHeader(listOf("Letterboxd", "Following ${users.size}"), "Your film diary", 76, Modifier.padding(start = PAGE_START.dp))
                    Box(Modifier.align(Alignment.TopEnd).padding(end = PAGE_END.dp, top = 26.dp)) {
                        CinemaButton("Search lists", ButtonKind.GLASS, "search", small = true) { model.go(Page.LbSearch(), remember = true) }
                    }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(start = PAGE_START.dp, end = PAGE_END.dp, top = 34.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    itemsIndexed(users) { i, u -> MemberCard(model, u, if (i == 0) first else state.focus.requester("member:$u"), { state.focus.last = if (i == 0) null else "member:$u" }) { model.go(Page.LbPerson(u), remember = true) } }
                    item { FollowCard(if (users.isEmpty()) first else null) { model.go(Page.LbFollow(), remember = true) } }
                }
            }
            if (users.isEmpty()) {
                item {
                    T("Follow yourself and your friends to see what they have watched lately, their watchlists, and their stars on every film page. " +
                        "Nothing is signed in: Cinema only reads public Letterboxd profiles.", ts(22, Cin.MUTED, lineHeight = 1.2f), maxLines = 4,
                        modifier = Modifier.padding(start = PAGE_START.dp).width(1400.dp))
                }
                return@LazyColumn
            }
            item { RecentlyWatched(model, state, users) { scope.launch { state.column.glideTo(2) } } }
            itemsIndexed(users) { i, u ->
                val watch = lbLoad(model, "/$u/watchlist/", HOUR) { Lb.films(it) }
                val likes = lbLoad(model, "/$u/likes/films/", HOUR) { Lb.films(it) }
                Column {
                    LbFilmRow(model, state, "watch:$u", "${lbName(u)}’s watchlist", null, if (watch.done) watch.value.orEmpty().take(20) else null,
                        { scope.launch { state.column.glideTo(3 + i) } }, line = { null }, empty = if (watch.done && watch.value == null) "Couldn't load this watchlist just now" else "Nothing here yet")
                    LbFilmRow(model, state, "likes:$u", "${lbName(u)} liked", null, if (likes.done) likes.value.orEmpty().take(20) else null,
                        line = { it.stars?.let { s -> "$s  ♥" } ?: "♥" }, empty = if (likes.done && likes.value == null) "Couldn't load liked films just now" else "Nothing here yet")
                }
            }
        }
    }
}

/** "Recently watched by the people you follow": every member's diary, merged, newest first. */
@Composable
private fun RecentlyWatched(model: CinemaModel, state: PageState, users: List<String>, onEnter: () -> Unit) {
    val diaries = users.map { u -> u to lbLoad(model, "/$u/films/diary/", 30 * MINUTE) { DesktopLetterboxd.diary(it) } }
    val merged = diaries.flatMap { (u, d) -> d.value.orEmpty().map { u to it } }.sortedByDescending { it.second.date ?: "" }.take(18)
    val ready = diaries.all { it.second.done } || merged.isNotEmpty()
    LbFilmRow(model, state, "recent", "Recently watched", if (users.size > 1) "by the people you follow" else null,
        if (ready) merged.map { it.second.film.copy(rating = it.second.rating) } else null, onEnter,
        line = { f -> listOfNotNull(f.stars, merged.firstOrNull { it.second.film.slug == f.slug }?.second?.date?.let { lbDay(it) }).joinToString("  ·  ").ifBlank { null } },
        chip = { f -> merged.firstOrNull { it.second.film.slug == f.slug }?.first?.let { lbName(it).substringBefore(' ') } })
}

/**
 * A followed member's card (`.mc`): a wash of their favourite films, the four favourites fanned on the right, their
 * picture and name, what they watched last and their numbers. Focused, it grows a little, gains a cream edge with a
 * warm bloom, a striped foot and rings round the picture. 600×300.
 */
@Composable
private fun MemberCard(model: CinemaModel, user: String, requester: FocusRequester?, onFocus: () -> Unit, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val grow by animateFloatAsState(if (focused) 1.04f else 1f, tween(160))
    val profile = lbLoad(model, "/$user/", 2 * HOUR) { DesktopLetterboxd.profile(it, user) }.value
    val diary = lbLoad(model, "/$user/films/diary/", 30 * MINUTE) { DesktopLetterboxd.diary(it) }.value
    profile?.let { lbNames[user.lowercase()] = it.name.substringBefore(' ').ifBlank { user } }
    val last: LbDiaryEntry? = diary?.firstOrNull()
    val lastName = last?.film?.name ?: profile?.recent?.firstOrNull()?.name
    val lastStars = (last?.rating ?: profile?.recent?.firstOrNull()?.rating)?.let { Lb.stars(it) }
    val stats = profile?.stats.orEmpty().filter { it.second.lowercase() in setOf("films", "this year", "lists", "following") }.take(3)
    val favs = profile?.favourites.orEmpty().take(4)
    val wash = lbPoster(model, favs.getOrNull(1)?.slug)
    Box(
        Modifier.size(600.dp, 300.dp).scale(grow)
            .bloom(Cin.SAFFRON.copy(alpha = 0.32f), 34.dp, 88.dp, enabled = focused)
            .then(if (focused) Modifier.border(4.dp, Cin.CREAM, RoundedCornerShape(34.dp)) else Modifier)
            .clip(RoundedCornerShape(34.dp)).background(Color(0xFF17110D))
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocus() }.tvFocusable(requester) { onClick() }
    ) {
        if (wash != null) RemoteImage(wash, Modifier.matchParentSize().scale(1.3f).alpha(0.35f))
        Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xEB0E0A08), Color(0xC70E0A08), Color(0x400E0A08)))))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0xB30E0A08))))
        Box(Modifier.align(Alignment.TopEnd).padding(end = 26.dp, top = 40.dp).size(190.dp, 200.dp)) {
            val tops = listOf(22, 8, 0, 12); val turns = listOf(-9f, -3f, 3f, 9f)
            favs.forEachIndexed { i, f ->
                RemoteImage(lbPoster(model, f.slug), Modifier.offset((i * 30).dp, tops[i].dp).size(98.dp, 147.dp).rotate(turns[i]).clip(RoundedCornerShape(10.dp)))
            }
        }
        Column(Modifier.padding(start = 22.dp, top = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(profile?.name ?: lbName(user), profile?.avatar, 96.dp, ringed = focused)
                Column(Modifier.padding(start = 10.dp).width(260.dp)) {
                    T(profile?.name ?: lbName(user), ts(50, Cin.CREAM, serif = true, tracking = -0.015f), maxLines = 1)
                    T("@$user", ts(18, Cin.CREAM.copy(alpha = 0.55f)), modifier = Modifier.padding(top = 6.dp))
                }
            }
            if (lastName != null) {
                T(listOfNotNull("LAST WATCHED", lbDay(last?.date)).joinToString(" · "), ts(15, Cin.CREAM.copy(alpha = 0.5f), weight = 600, tracking = 0.2f), modifier = Modifier.padding(start = 12.dp, top = 12.dp))
                Row(Modifier.padding(start = 12.dp, top = 6.dp).width(330.dp)) {
                    T(lastName, ts(23, Cin.CREAM), maxLines = 1)
                    lastStars?.let { T("  $it", ts(23, Cin.SAFFRON), maxLines = 1) }
                }
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(start = 34.dp, bottom = 26.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.forEach { (value, label) ->
                Row(Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x1CF6ECDB)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                    T("$value ", ts(17, Color(0xFFE6D9C6), weight = 700)); T(label.lowercase(), ts(17, Color(0xFFE6D9C6)))
                }
            }
        }
        if (focused) Canvas(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(8.dp)) { drawRect(stripeBrush(false, size.width)) }
    }
}

/** "Follow someone": a glass card with a saffron plus. Cream when focused. */
@Composable
private fun FollowCard(requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier.size(384.dp, 300.dp).clip(RoundedCornerShape(34.dp)).background(if (focused) Cin.CREAM else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable(requester) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(92.dp).clip(CircleShape).background(Color(0x17FFF2DE)), contentAlignment = Alignment.Center) { T("+", ts(52, Cin.SAFFRON, weight = 300)) }
        T("Follow someone", ts(32, if (focused) Cin.INK else Cin.CREAM, serif = true), modifier = Modifier.padding(top = 20.dp))
        T("Any Letterboxd member", ts(18, if (focused) Color(0xFF6A5848) else Cin.DIM), modifier = Modifier.padding(top = 4.dp))
    }
}

// --- One member ------------------------------------------------------------------------------------------

/** One member (`buildLbPerson`): picture, name, numbers and favourites, then tabs for their watchlist, likes, diary, reviews and lists. */
@Composable
fun LetterboxdPerson(model: CinemaModel, art: Art, page: Page.LbPerson) {
    val user = page.user
    val state = model.state(page)
    var followed by remember(user) { mutableStateOf(model.lb.following.any { it.equals(user, true) }) }
    val profile = lbLoad(model, "/$user/", 2 * HOUR) { DesktopLetterboxd.profile(it, user) }
    val p: LbProfile? = profile.value
    p?.let { lbNames[user.lowercase()] = it.name.substringBefore(' ').ifBlank { user } }
    var tab by remember(user) { mutableIntStateOf(0) }
    val firstTab = remember(user) { FocusRequester() }
    model.pageEntry = { firstTab.tryFocus() }
    LaunchedEffect(user) { if (!model.railOpen) focusSoon({ state.focus.restore() }, { firstTab.tryFocus() }) }
    Box(Modifier.fillMaxSize()) {
        Ambient()
        LazyColumn(state = state.column, contentPadding = PaddingValues(top = 52.dp, bottom = 120.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = PAGE_START.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p?.name ?: lbName(user), p?.avatar, 180.dp)
                        LbHeader(listOf(if (followed) "Following" else "Letterboxd", "@$user"), p?.name ?: lbName(user), 104, Modifier.padding(start = 28.dp))
                    }
                    if (p != null && p.favourites.isNotEmpty()) Column(Modifier.align(Alignment.TopEnd).padding(end = PAGE_END.dp)) {
                        T("FAVOURITES", ts(17, Cin.DIM, weight = 600, tracking = 0.24f))
                        Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            p.favourites.forEach { f -> RemoteImage(lbPoster(model, f.slug), Modifier.size(150.dp, 225.dp).clip(RoundedCornerShape(12.dp))) }
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(start = PAGE_START.dp, top = 34.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                    if (profile.done && p == null) Note("Couldn't reach Letterboxd just now", start = 0)
                    p?.stats?.take(4)?.forEach { (value, label) ->
                        Column(Modifier.size(200.dp, 96.dp).clip(RoundedCornerShape(22.dp)).background(Cin.GLASS).padding(start = 22.dp, end = 16.dp), verticalArrangement = Arrangement.Center) {
                            T(value, ts(40, Cin.CREAM, serif = true, lineHeight = 1f)); T(label.uppercase(), ts(14, Cin.DIM, weight = 600, tracking = 0.14f), maxLines = 1)
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    CinemaButton(if (followed) "Unfollow" else "Follow", if (followed) ButtonKind.GLASS else ButtonKind.PRIMARY, small = true) {
                        if (followed) { model.lb.unfollow(user); model.toast("Stopped following ${lbName(user)}"); model.go(Page.Letterboxd) }
                        else { model.lb.follow(user); followed = true; model.toast("Following ${lbName(user)}") }
                    }
                }
            }
            item {
                Row(Modifier.padding(start = (PAGE_START - 26).dp, top = 44.dp).height(62.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf("Watchlist", "Liked", "Diary", "Reviews", "Lists").forEachIndexed { i, name ->
                        SeasonTab(name, tab == i, if (i == 0) firstTab else state.focus.requester("tab:$i"), { state.focus.last = if (i == 0) null else "tab:$i"; tab = i }) { tab = i }
                    }
                }
            }
            item {
                when (tab) {
                    0 -> { val f = lbLoad(model, "/$user/watchlist/", HOUR) { Lb.films(it) }; LbFilmRow(model, state, "p:watch", "Watchlist", null, if (f.done) f.value.orEmpty() else null, line = { null }) }
                    1 -> { val f = lbLoad(model, "/$user/likes/films/", HOUR) { Lb.films(it) }; LbFilmRow(model, state, "p:likes", "Liked films", null, if (f.done) f.value.orEmpty() else null, line = { it.stars?.let { s -> "$s  ♥" } ?: "♥" }) }
                    2 -> {
                        val d = lbLoad(model, "/$user/films/diary/", HOUR / 2) { DesktopLetterboxd.diary(it) }
                        val list = d.value.orEmpty()
                        LbFilmRow(model, state, "p:diary", "Diary", null, if (d.done) list.map { it.film.copy(rating = it.rating) } else null,
                            line = { f -> listOfNotNull(f.stars, list.firstOrNull { it.film.slug == f.slug }?.date?.let { lbDay(it) }).joinToString("  ·  ").ifBlank { null } })
                    }
                    3 -> { val r = lbLoad(model, "/$user/films/reviews/", HOUR) { Lb.reviews(it) }; Column { RowHeading("Reviews", null, Modifier.padding(start = PAGE_START.dp, top = 50.dp, bottom = 14.dp)); LbReviewRow(model, state, "p:reviews", null, if (r.done) r.value.orEmpty() else null, byFilm = true, memory = state.focus) } }
                    4 -> { val l = lbLoad(model, "/$user/lists/", HOUR) { Lb.lists(it) }; LbListRow(model, state, "p:lists", "Lists", null, if (l.done) l.value.orEmpty() else null) }
                }
            }
        }
    }
}

// --- One list --------------------------------------------------------------------------------------------

/** A list (`buildLbList`): its name, owner and description, a fan of its first posters, then its films in order, twelve to a row. */
@Composable
fun LetterboxdListPage(model: CinemaModel, art: Art, page: Page.LbList) {
    val state = model.state(page)
    val scope = rememberCoroutineScope()
    val owner = page.path.split('/').getOrNull(1).orEmpty()
    val l = lbLoad(model, page.path, HOUR) { Lb.listPage(it, page.path) }
    val list = l.value
    Box(Modifier.fillMaxSize()) {
        Ambient()
        LazyColumn(state = state.column, contentPadding = PaddingValues(top = 52.dp, bottom = 120.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(start = PAGE_START.dp).width(1080.dp)) {
                        LbHeader(listOf("List", "by ${lbName(owner)}"), list?.name ?: "…", when { (list?.name?.length ?: 0) > 30 -> 64; (list?.name?.length ?: 0) > 16 -> 84; else -> 104 })
                        list?.let { T(listOfNotNull(it.count?.let { c -> "$c films" }, if (it.previews.size < (it.count ?: 0)) "first ${it.previews.size} shown" else null).joinToString("  ·  "), ts(23, Cin.MUTED), modifier = Modifier.padding(top = 26.dp)) }
                        list?.description?.let { T(it, synopsisStyle(24), maxLines = 3, modifier = Modifier.padding(top = 18.dp).width(960.dp)) }
                    }
                    if (list != null) Box(Modifier.align(Alignment.TopEnd).padding(end = PAGE_END.dp)) { ListFan(model, list, 200, 96, 600, false) }
                }
            }
            if (list == null) { item { Note(if (l.done) "Couldn't load this list just now" else "Loading the list…", modifier = Modifier.padding(top = 40.dp)) }; return@LazyColumn }
            val films = list.previews
            val chunks = films.chunked(12)
            itemsIndexed(chunks) { r, chunk ->
                val from = r * 12 + 1
                LbFilmRow(model, state, "list:$r", if (films.size > 12) "$from–${from + chunk.size - 1}" else "In order", null, chunk,
                    { scope.launch { state.column.glideTo(r + 1) } }, line = { null }, chip = { f -> "${films.indexOf(f) + 1}" })
            }
        }
    }
}

// --- List search -----------------------------------------------------------------------------------------

/** List search (`buildLbSearch`): the keyboard, the best matches on the right, every match in a row beneath; with nothing typed, the followed members' lists. */
@Composable
fun LetterboxdSearch(model: CinemaModel, art: Art, page: Page.LbSearch) {
    val state = model.state(page)
    var typed by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    model.pageEntry = { first.tryFocus() }
    LaunchedEffect(Unit) { focusSoon({ first.tryFocus() }) }
    LaunchedEffect(typed) { delay(700); query = typed.trim() }
    androidx.compose.runtime.DisposableEffect(Unit) { model.typingTarget = { c -> typed = if (c == null) typed.dropLast(1) else (typed + c).take(60) }; onDispose { model.typingTarget = null } }
    val results = lbLoad(model, query.takeIf { it.length >= 2 }?.let { "/search/lists/${java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20")}/" }, 24 * HOUR) { Lb.lists(it) }
    Box(Modifier.fillMaxSize()) {
        Ambient()
        LazyColumn(state = state.column, contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(760.dp)) {
                    SearchField(typed, "Search Letterboxd lists", Modifier.padding(start = 176.dp, top = 12.dp, end = PAGE_END.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { LbDots(); Spacer(Modifier.width(16.dp)); T("LISTS", ts(20, Cin.DIM, weight = 600, tracking = 0.2f)) }
                    }
                    Eyebrow(listOf("Type to search"), color = Cin.DIM, modifier = Modifier.offset(176.dp, 170.dp))
                    Box(Modifier.offset(176.dp, 240.dp)) {
                        QwertyKeys(first = first, onKey = { k -> typed = when (k) { Keys.CLEAR -> ""; Keys.SPACE -> "$typed "; Keys.DELETE -> typed.dropLast(1); else -> (typed + k).take(60) } })
                    }
                    Column(Modifier.offset(1232.dp, 170.dp).width(592.dp)) {
                        if (query.length < 2) {
                            T("TRY A THEME, A DIRECTOR OR A MOOD", ts(17, Cin.DIM, weight = 600, tracking = 0.24f))
                            listOf("a24", "comfort", "horror", "ghibli", "noir").forEach { s -> Box(Modifier.padding(top = 12.dp)) { Pill(s, 54.dp) { typed = s; query = s } } }
                        } else {
                            val l = results.value
                            T(if (!results.done) "SEARCHING FOR “${query.uppercase()}”…" else "LISTS MATCHING “${query.uppercase()}”", ts(17, Cin.DIM, weight = 600, tracking = 0.24f), maxLines = 1)
                            if (results.done && l.isNullOrEmpty()) T(if (l == null) "Couldn't reach Letterboxd just now" else "No lists found", ts(22, Cin.MUTED), modifier = Modifier.padding(top = 18.dp))
                            l.orEmpty().take(3).forEach { item -> Box(Modifier.padding(top = 18.dp)) { ListResult(model, item) } }
                        }
                    }
                }
            }
            if (query.length >= 2) {
                item { LbListRow(model, state, "s:all", "All matching lists", null, if (results.done) results.value.orEmpty() else null) }
            } else {
                item {
                    val all = model.lb.following.map { u -> u to lbLoad(model, "/$u/lists/", HOUR) { Lb.lists(it) } }
                    val lists = all.flatMap { (u, s) -> s.value.orEmpty().map { it.copy(ownerName = it.ownerName ?: lbName(u)) } }
                    if (model.lb.following.isNotEmpty()) LbListRow(model, state, "s:followed", "Lists by the people you follow", null, if (all.all { it.second.done } || lists.isNotEmpty()) lists else null)
                }
            }
        }
    }
}

@Composable
private fun ListResult(model: CinemaModel, l: LbList) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(140.dp).bloom(Cin.SAFFRON.copy(alpha = 0.2f), 26.dp, 40.dp, 4.dp, enabled = focused)
            .clip(RoundedCornerShape(26.dp)).background(if (focused) Cin.CREAM.copy(alpha = 0.14f) else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable { model.go(Page.LbList(l.path), remember = true) }.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListFan(model, l, 66, 30, 186, false)
        Column(Modifier.padding(start = 20.dp)) {
            T(l.name, ts(28, Cin.CREAM, serif = true), maxLines = 1)
            T(listOfNotNull(l.ownerName ?: l.owner, l.count?.let { "$it films" }, l.likes?.let { "♥ $it" }).joinToString("  ·  "), ts(18, Cin.MUTED), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// --- Follow someone --------------------------------------------------------------------------------------

/** Follow someone (`buildLbFollow`): type a username and their profile appears beside the keyboard, with Follow; beneath, people to pick from. */
@Composable
fun LetterboxdFollow(model: CinemaModel, art: Art, page: Page.LbFollow) {
    val state = model.state(page)
    var typed by remember { mutableStateOf("") }
    var looking by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    model.pageEntry = { first.tryFocus() }
    LaunchedEffect(Unit) { focusSoon({ first.tryFocus() }) }
    LaunchedEffect(typed) { if (typed.length >= 2) { delay(900); looking = typed } else looking = "" }
    androidx.compose.runtime.DisposableEffect(Unit) { model.typingTarget = { c -> typed = if (c == null) typed.dropLast(1) else (typed + (if (c == ' ') '_' else c)).take(30) }; onDispose { model.typingTarget = null } }
    val found = lbLoad(model, looking.takeIf { it.isNotEmpty() }?.let { "/$it/" }, 2 * HOUR) { html -> if (model.lb.missing(html)) null else DesktopLetterboxd.profile(html, looking) }
    Box(Modifier.fillMaxSize()) {
        Ambient()
        LazyColumn(state = state.column, contentPadding = PaddingValues(top = 52.dp, bottom = 120.dp)) {
            item {
                Box(Modifier.fillMaxWidth().height(800.dp)) {
                    LbHeader(listOf("Follow someone"), "Who should we follow?", 76, Modifier.padding(start = 176.dp))
                    Row(Modifier.offset(176.dp, 170.dp).size(980.dp, 112.dp).clip(RoundedCornerShape(56.dp)).background(Color(0x17FFF2DE)).padding(horizontal = 42.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        T("letterboxd.com/", ts(30, Cin.DIM)); Spacer(Modifier.width(4.dp)); T(typed, ts(36, Cin.CREAM), maxLines = 1)
                        Spacer(Modifier.width(6.dp)); Box(Modifier.size(3.dp, 48.dp).background(Cin.SAFFRON))
                    }
                    Box(Modifier.offset(176.dp, 320.dp)) {
                        // Usernames have no spaces: Space types an underscore, which they may contain.
                        QwertyKeys(first = first, onKey = { k -> typed = when (k) { Keys.CLEAR -> ""; Keys.SPACE -> typed + "_"; Keys.DELETE -> typed.dropLast(1); else -> (typed + k).take(30) } })
                    }
                    if (looking.isNotEmpty()) Column(Modifier.offset(1240.dp, 170.dp).width(584.dp).clip(RoundedCornerShape(34.dp)).background(Cin.GLASS).padding(horizontal = 40.dp, vertical = 36.dp)) {
                        val p = found.value
                        when {
                            !found.done -> T("LOOKING FOR @${looking.uppercase()}…", ts(17, Cin.DIM, weight = 600, tracking = 0.2f))
                            p == null -> T("Couldn't load @$looking just now. Check the username or try again later.", ts(24, Cin.MUTED))
                            else -> {
                                T("FOUND ON LETTERBOXD", ts(15, Cin.DIM, weight = 600, tracking = 0.2f))
                                Row(Modifier.padding(top = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(p.name, p.avatar, 120.dp)
                                    Column(Modifier.padding(start = 14.dp)) {
                                        T(p.name, titleStyle(52), maxLines = 1)
                                        T(listOfNotNull("@$looking", p.stat("Films")?.let { "$it films" }).joinToString(" · "), ts(20, Cin.DIM), modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                                if (p.favourites.isNotEmpty()) {
                                    T("FAVOURITES", ts(15, Cin.DIM, weight = 600, tracking = 0.2f), modifier = Modifier.padding(top = 30.dp))
                                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                        p.favourites.forEach { f -> RemoteImage(lbPoster(model, f.slug), Modifier.size(118.dp, 177.dp).clip(RoundedCornerShape(10.dp))) }
                                    }
                                }
                                val already = model.lb.following.any { it.equals(looking, true) }
                                Box(Modifier.padding(top = 34.dp)) {
                                    CinemaButton(if (already) "Already following" else "Follow", ButtonKind.PRIMARY) {
                                        if (already) return@CinemaButton
                                        model.lb.follow(looking); lbNames[looking.lowercase()] = p.name.substringBefore(' ')
                                        model.toast("Following ${p.name}"); model.go(Page.Letterboxd)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            model.lb.following.firstOrNull()?.let { u ->
                item {
                    val m = lbLoad(model, "/$u/following/", 12 * HOUR) { Lb.members(it) }
                    val people = m.value.orEmpty().filterNot { x -> model.lb.following.any { it.equals(x.user, true) } }.take(20)
                    if (people.isNotEmpty()) ShelfRow(state, "follow:people", "People ${lbName(u)} follows", null, {}, gap = 18) {
                        itemsIndexed(people) { i, x -> PersonChip(x, state.focus.requester("person:$i")) { typed = x.user; looking = x.user } }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonChip(x: LbMember, requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.height(80.dp).bloom(Cin.SAFFRON.copy(alpha = 0.24f), 40.dp, 40.dp, 4.dp, enabled = focused)
            .clip(RoundedCornerShape(40.dp)).background(if (focused) Cin.CREAM.copy(alpha = 0.14f) else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable(requester) { onClick() }.padding(start = 2.dp, end = 28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(x.name, x.avatar, 56.dp)
        T(x.name, ts(22, Cin.CREAM, weight = 500), maxLines = 1, modifier = Modifier.padding(start = 2.dp))
    }
}

// --- On a film's page --------------------------------------------------------------------------------------

/**
 * "On Letterboxd" on a film's page (`lbFilmSection`): the average with its chart, what the followed members made of it,
 * and the reviews people are liking this week. Nothing shows for a title Letterboxd does not know.
 */
@Composable
fun LetterboxdFilmSection(model: CinemaModel, route: Route.Title) {
    val t = route.entry.title
    val slugHint = t.key.removePrefix("lb:").takeIf { t.key.startsWith("lb:") }
    val tmdb = t.tmdbId?.toLongOrNull()
    if (slugHint == null && (tmdb == null || t.kind == Kind.SHOW)) return
    val info by produceState<com.danielportelabyrne.cinema.data.letterboxd.LbFilmInfo?>(null, t.key) {
        value = withContext(Dispatchers.IO) { runCatching { model.lb.pageNow(slugHint?.let { "/film/$it/" } ?: "/tmdb/$tmdb/", 72 * HOUR)?.let { Lb.film(it) } }.getOrNull() }
    }
    val film = info ?: return
    val histogram = lbLoad(model, "/csi/film/${film.slug}/rating-histogram/", 24 * HOUR) { Lb.histogram(it) }.value
    val reviews = lbLoad(model, "/film/${film.slug}/reviews/by/activity/", 24 * HOUR) { Lb.reviews(it) }.value
    Column(Modifier.padding(top = 64.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { RowHeading("On Letterboxd", sizePx = 46); Spacer(Modifier.width(18.dp)); LbDots() }
        Row(Modifier.padding(top = 28.dp).height(250.dp)) {
            Column(Modifier.width(520.dp).fillMaxHeight().clip(RoundedCornerShape(30.dp)).background(Cin.GLASS).padding(start = 34.dp, end = 34.dp, top = 30.dp)) {
                T("AVERAGE RATING", ts(15, Cin.DIM, weight = 600, tracking = 0.2f))
                Row(verticalAlignment = Alignment.Bottom) {
                    T(film.average?.let { String.format(Locale.UK, "%.1f", it) } ?: "–", ts(96, Cin.CREAM, serif = true, lineHeight = 1f))
                    Histogram(histogram.orEmpty(), Modifier.padding(start = 26.dp, bottom = 14.dp).size(260.dp, 90.dp))
                }
                T(listOfNotNull(film.ratings?.let { "from ${lbCount(it)} ratings" }, film.reviews?.let { "${lbCount(it)} reviews" }).joinToString(" · "), ts(19, Cin.MUTED), modifier = Modifier.padding(top = 8.dp))
            }
            if (model.lb.following.isNotEmpty()) Column(Modifier.weight(1f).padding(start = 40.dp).fillMaxHeight().clip(RoundedCornerShape(30.dp)).background(Cin.GLASS).padding(start = 34.dp, end = 34.dp, top = 30.dp)) {
                T("YOUR CIRCLE", ts(15, Cin.DIM, weight = 600, tracking = 0.2f))
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    model.lb.following.forEach { u -> CirclePerson(model, u, film.slug) { model.stack.removeAt(model.stack.lastIndex); model.go(Page.LbPerson(u), remember = true) } }
                }
            }
        }
        if (!reviews.isNullOrEmpty()) {
            RowHeading("Popular reviews", modifier = Modifier.padding(top = 50.dp, bottom = 14.dp))
            LbReviewRow(model, null, "film:reviews", null, reviews.take(12), startPad = 0, memory = route.focus)
        }
    }
}

@Composable
private fun CirclePerson(model: CinemaModel, user: String, slug: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val p = lbLoad(model, "/$user/", 2 * HOUR) { DesktopLetterboxd.profile(it, user) }.value
    p?.let { lbNames[user.lowercase()] = it.name.substringBefore(' ').ifBlank { user } }
    val m = lbLoad(model, "/$user/film/$slug/", 6 * HOUR) { h -> if (model.lb.missing(h)) LbMemberFilm(user, null, false, null) else Lb.memberFilm(h, user) }
    val mf = m.value
    Row(
        Modifier.clip(RoundedCornerShape(24.dp)).background(if (focused) Cin.CREAM.copy(alpha = 0.14f) else Color.Transparent)
            .onFocusChanged { focused = it.isFocused }.tvFocusable { onClick() }.padding(start = 0.dp, end = 24.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(lbName(user), p?.avatar, 84.dp)
        Column(Modifier.padding(start = 8.dp)) {
            T(lbName(user), ts(26, Cin.CREAM, weight = 600))
            when {
                !m.done -> T("…", ts(24, Cin.SAFFRON))
                mf == null || (mf.rating == null && mf.review == null && !mf.liked) -> T("Not logged yet", ts(22, Cin.MUTED))
                else -> Row { T(mf.rating?.let { Lb.stars(it) } ?: "Watched", ts(24, Cin.SAFFRON, tracking = 0.06f)); if (mf.liked) T("  ♥", ts(24, Color(0xFFFF6F61))) }
            }
            mf?.review?.let { T("“$it”", ts(18, Cin.DIM), maxLines = 1, modifier = Modifier.width(330.dp)) }
        }
    }
}

/** The ratings chart (`LbHistogram`): ten bars from ½ to ★★★★★, the top three in the stripe colours. */
@Composable
private fun Histogram(counts: List<Long>, modifier: Modifier) {
    Canvas(modifier) {
        if (counts.size != 10) return@Canvas
        val max = counts.maxOrNull()?.takeIf { it > 0 } ?: return@Canvas
        val gap = 5.dp.toPx(); val w = (size.width - gap * 9) / 10f; val r = 4.dp.toPx()
        val lit = Brush.verticalGradient(listOf(Cin.STRIPE_SAFFRON, Cin.STRIPE_ORANGE, Cin.STRIPE_TERRA, Cin.STRIPE_RED), 0f, size.height)
        counts.forEachIndexed { i, n ->
            val h = (size.height * (n.toFloat() / max)).coerceAtLeast(4.dp.toPx())
            val tl = Offset(i * (w + gap), size.height - h)
            if (i >= 7) drawRoundRect(lit, tl, Size(w, h), CornerRadius(r)) else drawRoundRect(Cin.CREAM.copy(alpha = 0.22f), tl, Size(w, h), CornerRadius(r))
        }
    }
}
