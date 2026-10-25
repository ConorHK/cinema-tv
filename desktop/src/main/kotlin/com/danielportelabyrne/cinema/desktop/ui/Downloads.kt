package com.danielportelabyrne.cinema.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.danielportelabyrne.cinema.data.movies.ByteSize
import com.danielportelabyrne.cinema.data.movies.DownloadAction
import com.danielportelabyrne.cinema.data.movies.DownloadActionResult
import com.danielportelabyrne.cinema.data.movies.Kind
import com.danielportelabyrne.cinema.data.movies.QueueItem
import com.danielportelabyrne.cinema.data.movies.QueueSource
import com.danielportelabyrne.cinema.data.movies.RequestDiagnostic
import com.danielportelabyrne.cinema.data.movies.RequestPipelineApi
import com.danielportelabyrne.cinema.data.movies.SeerrRequest
import com.danielportelabyrne.cinema.data.movies.Stage
import com.danielportelabyrne.cinema.data.movies.WantedEpisode
import com.danielportelabyrne.cinema.desktop.CinemaModel
import com.danielportelabyrne.cinema.desktop.Sheet
import com.danielportelabyrne.cinema.desktop.SheetAction
import com.danielportelabyrne.cinema.ui.EpisodeCards
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One look at everything on its way (`buildDownloads`' refresh). */
private class DownloadsView(
    val queue: List<QueueItem>?, val requestsFailed: Boolean, val helperDown: Boolean, val requests: List<SeerrRequest>,
    val diagnostics: Map<Int, RequestDiagnostic>, val wanted: Map<Long, List<WantedEpisode>>, val upcoming: Map<Long, List<WantedEpisode>>,
)

/** Requests still on their way to a finished file. */
private val pendingStages = setOf(Stage.WAITING_APPROVAL, Stage.SEARCHING, Stage.DOWNLOADING)

/** Name and poster per title, keyed by TMDB id, doubled, plus one for a series. Kept on disk between sessions. */
private class KnownTitles(model: CinemaModel) {
    private val prefs = model.platform.preferences("downloads")
    val map: MutableMap<Long, Pair<String, String?>> = java.util.concurrent.ConcurrentHashMap<Long, Pair<String, String?>>().also { m ->
        runCatching {
            val saved = JSONObject(prefs.getString("titles", "{}") ?: "{}")
            saved.keys().forEach { k -> saved.optJSONArray(k)?.let { a -> m[k.toLong()] = a.optString(0) to a.optString(1).ifBlank { null } } }
        }
    }
    fun save() {
        val o = JSONObject(); map.forEach { (k, v) -> o.put(k.toString(), JSONArray().put(v.first).put(v.second ?: "")) }
        prefs.edit().putString("titles", o.toString()).apply()
    }
}

private fun key(kind: Kind, tmdb: Long) = tmdb * 2 + if (kind == Kind.SHOW) 1 else 0

@Composable
fun DownloadsPage(model: CinemaModel) {
    val known = remember { KnownTitles(model) }
    var view by remember { mutableStateOf<DownloadsView?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    val first = remember { FocusRequester() }
    val still = androidx.compose.ui.platform.LocalInspectionMode.current

    // Progress moves, so look again every 15 seconds for as long as this page is the one on screen.
    LaunchedEffect(tick, model.seerr.isSignedIn) {
        if (!model.seerr.isSignedIn) return@LaunchedEffect
        model.bg({ load(model, known) }) { v -> if (v != null) view = v }
        if (!still) { delay(15_000); tick++ }
    }
    model.pageEntry = { first.tryFocus() }
    LaunchedEffect(view != null, model.seerr.isSignedIn) {
        if ((view != null || !model.seerr.isSignedIn) && !model.railOpen) focusSoon({ first.tryFocus() })
    }

    Box(Modifier.fillMaxSize()) {
        Ambient()
        // The ribbon looping in from the top right (`RibbonView.downloads`), fading in toward the right.
        Ribbon(listOf(1990f to 82f, 1640f to 82f, 1600f to 96f, 1578f to 144f, 1600f to 192f, 1640f to 206f, 1990f to 206f), 90f,
            Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen; alpha = 0.85f }
                .drawWithContent { drawContent(); drawRect(Brush.horizontalGradient(0.45f to Color.Transparent, 0.8f to Color.Black), blendMode = BlendMode.DstIn) })
        LazyColumn(contentPadding = PaddingValues(start = PAGE_START.dp, end = PAGE_END.dp, top = 52.dp, bottom = 120.dp)) {
            item {
                Eyebrow(listOf("Request queue"))
                T("Downloads", titleStyle(84), maxLines = 1, modifier = Modifier.padding(top = 14.dp))
                T("What you’ve asked for, and how far along it is", ts(24, Cin.MUTED, weight = 300), modifier = Modifier.padding(top = 14.dp))
            }
            if (!model.seerr.isSignedIn) {
                item {
                    ActionCard(if (model.seerr.hasServer) "Sign in to see your downloads" else "Set up Seerr to request films",
                        if (model.seerr.hasServer) "Sign in to Seerr again" else "Then your downloads show here", Modifier.padding(top = 56.dp), first) {
                        model.signInToSeerr { tick++ }
                    }
                }
                return@LazyColumn
            }
            val v = view
            item { StatTiles(v) }
            if (v == null) { item { Note("Loading…", start = 0, modifier = Modifier.padding(top = 20.dp)) }; return@LazyColumn }
            if (v.requestsFailed) item { Note("Couldn't read your requests. Showing the download queue only.", start = 0) }
            if (model.seerr.hasRequestHelper && v.helperDown) item { Note("Automatic search status is unavailable. Download-client progress is shown below.", start = 0) }
            val rows = ArrayList<@Composable (FocusRequester?) -> Unit>()
            v.queue.orEmpty().forEach { q -> rows += { r -> QueueRow(model, known, q, r) { tick++ } } }
            v.requests.forEach { r -> rows += { fr -> RequestRow(model, known, r, v.diagnostics, fr) { tick++ } } }
            v.wanted.forEach { (tmdb, eps) -> rows += { fr -> WantedRow(model, known, tmdb, eps, upcoming = false, fr) { tick++ } } }
            v.upcoming.forEach { (tmdb, eps) -> rows += { fr -> WantedRow(model, known, tmdb, eps, upcoming = true, fr) { tick++ } } }
            if (rows.isEmpty()) item { Note(if (v.queue == null) "Couldn't reach the download queue just now." else "Nothing requested yet. Open any title and press Queue download.", start = 0, modifier = Modifier.padding(top = 20.dp)) }
            items(rows.size) { i -> Box(Modifier.padding(top = if (i > 0) 14.dp else 28.dp)) { rows[i](if (i == 0) first else null) } }
            val pendingQueue = v.queue.orEmpty().filter { it.canCancel && (it.isActive || it.isWaiting) }
            val pendingRequests = v.requests.filter { it.stage in pendingStages && v.diagnostics[it.id]?.stopped != true }
            val wantedEpisodes = v.wanted.values.flatten() + v.upcoming.values.flatten()
            if (pendingQueue.isNotEmpty() || pendingRequests.isNotEmpty() || wantedEpisodes.isNotEmpty()) item {
                val total = pendingQueue.size + pendingRequests.size + wantedEpisodes.size
                Box(Modifier.padding(top = 36.dp)) {
                    CinemaButton("Cancel all pending ($total)", ButtonKind.GLASS) { cancelAllPending(model, pendingQueue, pendingRequests, wantedEpisodes) { tick++ } }
                }
            }
        }
    }
}

private fun load(model: CinemaModel, known: KnownTitles): DownloadsView {
    val queue = model.queueClient.items()
    val inQueue = queue.orEmpty().map { it.tmdbId * 2 + if (it.source == QueueSource.SONARR) 1 else 0 }.toSet()
    val snapshot = model.seerr.pipelineStatus()
    val diagnostics = snapshot?.takeIf { it.healthy }?.requests.orEmpty()
    val raw = model.seerr.requestsOrNull()
    // One queued season must not hide another season of the same show.
    val requests = raw.orEmpty().filter { !RequestPipelineApi.covered(it, queue.orEmpty()) }
    val listed = inQueue + requests.map { key(it.kind, it.tmdbId) }
    val wanted = model.sonarr.wanted().orEmpty().groupBy { it.seriesTmdbId }.filterKeys { it * 2 + 1 !in listed }
    val upcoming = model.sonarr.upcoming().orEmpty().groupBy { it.seriesTmdbId }.filterKeys { it * 2 + 1 !in inQueue }
    // Names and posters of everything shown, looked up once and kept.
    val wantedKeys = (queue.orEmpty().map { q -> (if (q.source == QueueSource.SONARR) Kind.SHOW else Kind.MOVIE) to q.tmdbId } +
        requests.map { it.kind to it.tmdbId } + wanted.keys.map { Kind.SHOW to it } + upcoming.keys.map { Kind.SHOW to it })
        .filter { (k, t) -> t > 0 && !known.map.containsKey(key(k, t)) }.distinct()
    if (wantedKeys.isNotEmpty()) {
        val pool = java.util.concurrent.Executors.newFixedThreadPool(4)
        runCatching { pool.invokeAll(wantedKeys.map { (k, t) -> java.util.concurrent.Callable { model.seerr.titleInfo(k, t)?.let { known.map[key(k, t)] = it } } }, 30, java.util.concurrent.TimeUnit.SECONDS) }
        pool.shutdown(); known.save()
    }
    return DownloadsView(queue, raw == null, snapshot?.healthy != true, requests, diagnostics, wanted, upcoming)
}

/** The four counts at the top of Downloads; the busiest one is lit saffron. */
@Composable
private fun StatTiles(v: DownloadsView?) {
    val q = v?.queue.orEmpty()
    val counts = if (v == null) null else listOf(
        q.count { it.isDownloading }, q.count { it.isWaiting }, q.count { it.status in setOf("metadata", "stalled") },
        v.requests.count { it.stage == Stage.SEARCHING && v.diagnostics[it.id]?.stopped != true } + v.upcoming.values.sumOf { it.size },
    )
    val busiest = counts?.withIndex()?.filter { it.value > 0 }?.maxByOrNull { it.value }?.index
    Row(Modifier.padding(top = 56.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        listOf("Downloading", "Queued", "Finding sources", "Awaiting release").forEachIndexed { i, label ->
            val lit = i == busiest
            Column(Modifier.size(276.dp, 96.dp).clip(RoundedCornerShape(22.dp)).background(if (lit) Cin.SAFFRON.copy(alpha = 0.1f) else Cin.GLASS).padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.Center) {
                T(counts?.get(i)?.toString() ?: "–", ts(42, if (lit) Cin.SAFFRON else Cin.CREAM, serif = true, lineHeight = 1f))
                T(label.uppercase(), ts(15, Cin.DIM, weight = 500, tracking = 0.12f), maxLines = 1, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/**
 * One row (`requestCard`): a glass card with the poster, "Title · S1 E7 · Name", a status dot and line, a striped bar
 * (sweeping while sources are being found) and the percentage in Fraunces ("—" when nothing is known).
 */
@Composable
private fun RequestCard(poster: String?, name: String, detail: String?, status: String, active: Boolean, progress: Float?, sweeping: Boolean, failed: Boolean = false,
                        requester: FocusRequester?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().height(128.dp)
            .bloom(Cin.SAFFRON.copy(alpha = 0.25f), 26.dp, 40.dp, 6.dp, enabled = focused)
            .clip(RoundedCornerShape(26.dp)).background(if (focused) Color(0x24FFF2DE) else Cin.GLASS)
            .onFocusChanged { focused = it.isFocused }.tvFocusable(requester) { onClick() }
            .padding(start = 20.dp, end = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RemoteImage(poster, Modifier.size(68.dp, 96.dp).clip(RoundedCornerShape(10.dp)))
        Column(Modifier.weight(1f).padding(start = 28.dp)) {
            Row {
                T(name, ts(27, Cin.CREAM, weight = 500), maxLines = 1)
                detail?.takeIf { it.isNotBlank() }?.let { T(" ·  $it", ts(27, Cin.MUTED, weight = 300), maxLines = 1) }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).clip(CircleShape).background(when { failed -> Cin.TERRA; active -> Cin.SAFFRON; else -> Cin.DIM }))
                Spacer(Modifier.width(10.dp))
                T(status, ts(19, Cin.MUTED), maxLines = 1)
            }
            CinemaBar(progress, sweeping, Modifier.padding(top = 14.dp))
        }
        Box(Modifier.width(148.dp), contentAlignment = Alignment.CenterEnd) {
            if (progress != null) T("${Math.round(progress * 100)}%", ts(44, Cin.CREAM, serif = true)) else T("—", ts(30, Cin.DIM))
        }
    }
}

@Composable
private fun QueueRow(model: CinemaModel, known: KnownTitles, item: QueueItem, requester: FocusRequester?, refresh: () -> Unit) {
    val kind = if (item.source == QueueSource.SONARR) Kind.SHOW else Kind.MOVIE
    val info = known.map[key(kind, item.tmdbId)]
    val status = queueStatus(item)
    RequestCard(info?.second, item.title, item.detail, status, active = item.isDownloading || item.status == "metadata",
        progress = item.progress(), sweeping = item.status == "metadata" || (item.status == "queued" && !item.clientVerified), failed = item.status == "failed", requester = requester) {
        val actions = mutableListOf<SheetAction>()
        if (item.canPrioritize) {
            actions += SheetAction("Download next", "Move to the front of the queue", primary = true) { prioritize(model, item, DownloadAction.NEXT, refresh) }
            actions += SheetAction("Download now", "Bypass the queue; other downloads continue") { prioritize(model, item, DownloadAction.NOW, refresh) }
        }
        if (item.canCancel) actions += SheetAction("Cancel download", "Stop it and remove its partial files") {
            model.bg({ model.queueClient.cancel(item) }) { done -> model.toast(if (done == true) "Cancelled ${item.title}" else "Couldn't cancel it right now"); if (done == true) refresh() }
        }
        model.sheet = Sheet("DOWNLOAD", item.title, item.detail, listOf(status), actions)
    }
}

private fun prioritize(model: CinemaModel, item: QueueItem, action: DownloadAction, refresh: () -> Unit) {
    model.bg({ model.queueClient.prioritize(item, action) to model.queueClient.clientFor(item)?.config?.kind }) { r ->
        val (result, kind) = r ?: (DownloadActionResult.FAILED to null)
        val clientName = kind?.label ?: "the download client"
        if (result == DownloadActionResult.NEEDS_SIGN_IN && kind != null) {
            model.setup.clientSignIn(kind.label, kind.needsUsername, { u, p -> model.queueClient.signIn(item, u, p) }) { prioritize(model, item, action, refresh) }
            return@bg
        }
        model.toast(when (result) {
            DownloadActionResult.DONE -> if (action == DownloadAction.NEXT) "Moved ${item.title} to the front" else "Starting ${item.title} now"
            DownloadActionResult.QUEUE_DISABLED -> "Queueing is switched off in $clientName. Use Download now instead."
            DownloadActionResult.NOT_FOUND -> "This download has finished or left the queue"
            DownloadActionResult.NOT_CONFIGURED -> "Couldn't connect to this download's client"
            DownloadActionResult.NEEDS_SIGN_IN -> "Sign in to $clientName"
            DownloadActionResult.FAILED -> "Couldn't change the download right now"
        })
        refresh()
    }
}

@Composable
private fun RequestRow(model: CinemaModel, known: KnownTitles, r: SeerrRequest, diagnostics: Map<Int, RequestDiagnostic>, requester: FocusRequester?, refresh: () -> Unit) {
    val info = known.map[key(r.kind, r.tmdbId)]
    val name = info?.first ?: "Title ${r.tmdbId}"
    val status = statusLine(r, diagnostics)
    RequestCard(info?.second, name, null, status, active = r.stage == Stage.DOWNLOADING || r.stage == Stage.SEARCHING,
        progress = r.progress(), sweeping = r.stage == Stage.SEARCHING && diagnostics[r.id]?.stopped != true, failed = r.stage == Stage.FAILED || r.stage == Stage.DECLINED, requester = requester) {
        if (!r.canCancel) return@RequestCard
        val actions = mutableListOf<SheetAction>()
        val canSearch = diagnostics[r.id]?.canRetry == true || (!model.seerr.hasRequestHelper && r.stage == Stage.SEARCHING)
        if (canSearch) actions += SheetAction("Search again", "Look for a release now", primary = true) {
            model.bg({ if (model.seerr.hasRequestHelper) model.seerr.retrySearch(r.id) else model.queueClient.searchAgain(r) }) { ok ->
                model.toast(if (ok == true) "Search requested" else "Search couldn't start yet. Try again shortly."); refresh()
            }
        }
        actions += SheetAction("Cancel request", "Stop looking for a release", primary = true) {
            model.bg({ model.queueClient.cancelRequest(r) }) { done -> model.toast(if (done == true) "Cancelled $name" else "Couldn't cancel this request right now"); if (done == true) refresh() }
        }
        model.sheet = Sheet("REQUEST", name, null, listOf(status, "Cancelling stops looking for this request. Completed files and other seasons are kept."), actions)
    }
}

@Composable
private fun WantedRow(model: CinemaModel, known: KnownTitles, tmdb: Long, episodes: List<WantedEpisode>, upcoming: Boolean, requester: FocusRequester?, refresh: () -> Unit) {
    val info = known.map[tmdb * 2 + 1]
    val title = info?.first ?: episodes.first().seriesTitle.ifBlank { "Title $tmdb" }
    val sorted = episodes.sortedWith(compareBy({ it.season }, { it.number }))
    val listed = sorted.take(3).joinToString(", ") { "S${it.season} E${it.number}" } + if (sorted.size > 3) " +${sorted.size - 3}" else ""
    val first = sorted.first()
    val status = if (upcoming) "Waiting to air · S${first.season} E${first.number} · " + (EpisodeCards.formatDate(first.airDate)?.substringBeforeLast(' ') ?: "TBA") else "Waiting for a release"
    RequestCard(info?.second, title, listed, status, active = false, progress = null, sweeping = false, requester = requester) {
        model.sheet = Sheet(if (upcoming) "UPCOMING" else "WANTED", title, null, listOf(if (upcoming) status else "$status · $listed",
            if (upcoming) "These download by themselves once they air. Cancelling stops waiting for them." else "Cancelling stops looking for these episodes. Anything already downloaded is kept."), listOf(
            SheetAction("Cancel", if (upcoming) "Stop waiting for them" else "Stop looking for them", primary = true) {
                model.bg({ model.sonarr.unwant(episodes.map { it.id }) }) { done ->
                    model.toast(if (done == true) (if (upcoming) "Stopped waiting for $title" else "Stopped looking for $title") else "Couldn't cancel it right now"); if (done == true) refresh()
                }
            },
            SheetAction("Keep them") { },
        ))
    }
}

private fun cancelAllPending(model: CinemaModel, items: List<QueueItem>, requests: List<SeerrRequest>, wanted: List<WantedEpisode>, refresh: () -> Unit) {
    model.sheet = Sheet("DOWNLOADS", "Cancel all pending", "${items.size + requests.size + wanted.size} waiting or downloading", listOf(
        "Stops everything in the queue, takes it out of the download client and deletes anything already downloaded for it. " +
            "Requests and episodes that have no release yet are withdrawn. Finished titles are not touched."), listOf(
        SheetAction("Cancel all", "Stop every pending download", primary = true) {
            model.bg({
                items.count { !model.queueClient.cancel(it) } + requests.count { !model.queueClient.cancelRequest(it) } +
                    if (wanted.isEmpty() || model.sonarr.unwant(wanted.map { it.id })) 0 else 1
            }) { failed -> model.toast(if (failed == 0) "Cancelled everything pending" else "${failed ?: "Some"} couldn't be cancelled right now"); refresh() }
        },
        SheetAction("Keep them") { },
    ))
}

/** Where a queued item stands, in words (`queueStatus`). */
private fun queueStatus(item: QueueItem): String = when {
    item.status == "metadata" -> "Finding torrent details" + if (item.forced) " · starting immediately" else ""
    item.status == "stalled" -> "Waiting for sources" + if (item.forced) " · starting immediately" else ""
    item.status == "checking" -> "Checking downloaded files"
    item.status == "downloading" -> {
        val left = ByteSize.format(item.sizeLeftBytes) + " left"
        val eta = item.etaSeconds?.takeIf { it < 604_800 && item.seeds != 0 }?.let { s -> if (s < 90) " · almost done" else if (s < 7200) " · about ${s / 60} min left" else " · about ${s / 3600} h left" }.orEmpty()
        val rate = item.speedBytes?.takeIf { it > 0 }?.let { b -> if (b >= 1_000_000) String.format(Locale.UK, " · %.1f MB/s", b / 1e6) else if (b >= 1000) " · ${b / 1000} KB/s" else " · $b B/s" }.orEmpty()
        val seeds = item.seeds?.let { " · $it " + if (it == 1) "seeder" else "seeders" }.orEmpty()
        (if (item.forced) "Downloading now" else "Downloading") + " · $left$rate$seeds$eta"
    }
    item.status == "paused" -> "Paused"
    item.status == "delayed" -> "Waiting for a better release"
    item.position == 1 -> "Next in line · waiting for a free download slot"
    item.position > 1 -> "In line · #${item.position} · waiting for a free download slot"
    item.status == "queued" && !item.clientVerified && item.sizeBytes == 0L -> "Sent to the download client · waiting for torrent details"
    item.status == "queued" && !item.clientVerified -> "Queued in the download client"
    item.status == "queued" -> "Queued · waiting for a free download slot"
    item.status == "completed" -> "Finished · importing"
    item.status == "failed" -> "Failed"
    item.status == "warning" -> "Needs attention"
    else -> item.status.replaceFirstChar { it.uppercase() }
}

private fun statusLine(r: SeerrRequest, diagnostics: Map<Int, RequestDiagnostic>): String {
    diagnostics[r.id]?.let { d ->
        val retry = d.nextSearchAtMs?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) / 60_000 }
        val last = d.lastSearchAtMs?.let { java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(it)) }
        return d.message + if (d.canRetry) (last?.let { " · checked $it" }.orEmpty()) + (retry?.let { if (it < 1) " · retry shortly" else " · retry in $it min" }.orEmpty()) else ""
    }
    return when (r.stage) {
        Stage.WAITING_APPROVAL -> "Waiting for approval"
        Stage.SEARCHING -> "Looking for a release · nothing downloading yet"
        Stage.DOWNLOADING -> {
            val done = ByteSize.format(r.totalBytes - r.leftBytes) + " of " + ByteSize.format(r.totalBytes)
            val left = r.etaMs()?.let { (it - System.currentTimeMillis()) / 60_000L }
            "Downloading · $done" + when { left == null -> ""; left < 1 -> " · almost done"; left < 120 -> " · about $left min left"; else -> " · about ${left / 60} h left" }
        }
        Stage.READY -> "Ready to watch in your library"
        Stage.DECLINED -> "Declined"
        Stage.FAILED -> "Failed · the download could not be completed"
    }
}
