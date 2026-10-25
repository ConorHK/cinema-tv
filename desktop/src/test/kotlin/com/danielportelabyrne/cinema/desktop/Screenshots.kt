package com.danielportelabyrne.cinema.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.Density
import com.danielportelabyrne.cinema.data.jellyfin.FoundServer
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.desktop.input.NavAction
import com.danielportelabyrne.cinema.desktop.jellyfin.HomeShelves
import com.danielportelabyrne.cinema.desktop.player.PlayerState
import com.danielportelabyrne.cinema.desktop.ui.CinemaApp
import com.danielportelabyrne.cinema.desktop.ui.Dispatcher
import com.danielportelabyrne.cinema.desktop.ui.FocusHub
import com.danielportelabyrne.cinema.desktop.ui.ImageSource
import com.danielportelabyrne.cinema.desktop.ui.KeyboardRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/**
 * Renders every screen from made-up data into PNGs (./gradlew :desktop:screenshots), at the Deck's 1280×800 and at
 * 1080p, so layout and focus can be checked without a server, a pad or a Deck. Pictures are flat colour blocks.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/screens").apply { mkdirs() }
    val shots = listOf<Pair<String, (CinemaModel, Dispatcher) -> Unit>>(
        "00-setup-welcome" to { m, _ -> m.stack += Route.Setup; m.setup.startFirstRun() },
        "05-keyboard" to { m, _ -> m.stack += Route.Setup; m.setup.startFirstRun(); m.keyboard = KeyboardRequest("Your Jellyfin server", "192.168.1.2", hint = "192.168.1.20:8096") {} },
        "10-library" to { m, _ -> home(m); m.page = Page.Library },
        "12-library-rail" to { m, d -> home(m); m.page = Page.Library; d.later(NavAction.LEFT, NavAction.LEFT, NavAction.DOWN) },
        "20-title-film" to { m, _ -> home(m); m.openItem(films[1].copy(resumeMs = 41 * 60_000L)) },
        "40-sheet" to { m, _ -> home(m); m.page = Page.Library; m.sheet = Sheet("DOWNLOAD", "Severance", "S1 E7 · Defiant Jazz", listOf("Downloading · 1.2 GB left · 3.4 MB/s · 12 seeders"),
            listOf(SheetAction("Download next", "Move to the front of the queue", primary = true) {}, SheetAction("Cancel download", "Stop it and remove its partial files") {})) },
        "41-stream-loading" to { m, _ -> home(m); m.streamLoading = StreamLoading("Past Lives", null) },
        "30-playing" to { m, _ -> home(m); m.playerState = PlayerState.Playing("Severance · S1 E3 · In Perpetuity", 1_200_000, 3_300_000, false) },
        "31-play-failed" to { m, _ -> home(m); m.playerState = PlayerState.Failed("This title wouldn't play (unsupported codec)") },
        "32-quit" to { m, _ -> home(m); m.confirmQuit = true },
    )
    for ((w, h) in listOf(1280 to 800, 1920 to 1080)) for ((name, setup) in shots) {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val model = CinemaModel(DesktopPlatform(), scope, ui = Dispatchers.Unconfined)
        model.images = blocks
        val dispatcher = Dispatcher(model, FocusHub())
        setup(model, dispatcher)
        val scene = ImageComposeScene(w, h, Density(1f)) {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalInspectionMode provides true) { CinemaApp(model, dispatcher) }
        }
        var t = 0L
        repeat(6) { scene.render(t); t += 100_000_000L }
        // Presses queued by the shot, one per frame, as a viewer would.
        dispatcher.queued.forEach { a -> dispatcher.handle(a); repeat(4) { scene.render(t); t += 100_000_000L } }
        repeat(8) { scene.render(t); t += 100_000_000L }
        val png = scene.render(t).encodeToData(EncodedImageFormat.PNG)!!.bytes
        File(out, "$name-${w}x$h.png").writeBytes(png)
        scene.close()
    }
    println("Screens written to ${out.absolutePath}")
    kotlin.system.exitProcess(0)
}

private val pending = HashMap<Dispatcher, MutableList<NavAction>>()
private fun Dispatcher.later(vararg a: NavAction) { pending.getOrPut(this) { mutableListOf() } += a }
private val Dispatcher.queued: List<NavAction> get() = pending[this].orEmpty()

private fun home(m: CinemaModel) {
    m.stack += Route.Browse
    m.library = films + series
    m.shelves = HomeShelves(
        resume = listOf(episodes(series[0])[2].copy(resumeMs = 20 * 60_000L), films[3].copy(resumeMs = 62 * 60_000L)),
        nextUp = listOf(episodes(series[1])[0], episodes(series[2])[4]),
        films = films, series = series,
    )

}

private fun film(i: Int, name: String, year: Int, mins: Int, genres: List<String>, played: Boolean = false) = JellyfinItem(
    id = "film$i", name = name, year = year, type = "Movie", mediaSourceId = null, runtimeMin = mins, genres = genres, played = played,
    officialRating = "15", hasBackdrop = true, communityRating = 7.0 + (i % 3) * 0.4,
    overview = "A sample overview for $name, long enough to wrap onto a second and a third line so the layout shows how a real description sits on screen.",
)

private val films = listOf(
    film(1, "Past Lives", 2023, 106, listOf("Drama", "Romance")),
    film(2, "The Zone of Interest", 2023, 105, listOf("Drama", "History")),
    film(3, "Anatomy of a Fall", 2023, 152, listOf("Thriller")),
    film(4, "Perfect Days", 2023, 124, listOf("Drama"), played = true),
    film(5, "Aftersun", 2022, 102, listOf("Drama")),
    film(6, "The Holdovers", 2023, 133, listOf("Comedy", "Drama")),
    film(7, "Poor Things", 2023, 141, listOf("Comedy", "Sci-Fi")),
    film(8, "Oppenheimer", 2023, 180, listOf("Drama", "History")),
    film(9, "Fallen Leaves", 2023, 81, listOf("Comedy")),
    film(10, "All of Us Strangers", 2023, 105, listOf("Drama")),
    film(11, "Godzilla Minus One", 2023, 125, listOf("Action")),
    film(12, "The Boy and the Heron", 2023, 124, listOf("Animation")),
)

private val series = listOf("Severance", "The Bear", "Slow Horses", "Shōgun", "Andor", "Mr Inbetween").mapIndexed { i, n ->
    JellyfinItem(id = "series$i", name = n, year = 2019 + i, type = "Series", mediaSourceId = null, genres = listOf("Drama", if (i % 2 == 0) "Thriller" else "Comedy"), officialRating = "15", hasBackdrop = true,
        overview = "A sample series overview for $n that runs on a little, as real ones do.")
}

private fun episodes(s: JellyfinItem) = (1..9).map { e ->
    JellyfinItem(id = "${s.id}e$e", name = listOf("Good News About Hell", "Half Loop", "In Perpetuity", "The You You Are", "The Grim Barbarity", "Hide and Seek", "Defiant Jazz", "What's for Dinner?", "The We We Are")[e - 1],
        year = s.year, type = "Episode", mediaSourceId = null, season = 1, episode = e, seriesName = s.name, seriesId = s.id,
        runtimeMin = 50, played = e < 3, hasPrimaryImage = true, resumeMs = if (e == 3) 20 * 60_000L else 0L,
        overview = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives.")
}

/** Each picture a flat two-tone block, coloured from its address, so cards are told apart. */
private val blockCache = HashMap<String, ImageBitmap>()
private val blocks = object : ImageSource {
    override fun cached(url: String): ImageBitmap = blockCache.getOrPut(url) { block(url) }
    override fun load(url: String): ImageBitmap = cached(url)
}

private fun block(url: String): ImageBitmap {
    val h = url.hashCode()
    val wide = url.contains("Backdrop") || url.contains("maxWidth")
    val img = ImageBitmap(if (wide) 640 else 300, if (wide) 360 else 450)
    val c = Canvas(img)
    val a = Color(0xFF000000 or (h.toLong() and 0x7F7F7F) or 0x302010)
    val b = Color(0xFF1A140E)
    c.drawRect(0f, 0f, img.width.toFloat(), img.height.toFloat(), Paint().apply { color = a })
    c.drawRect(0f, img.height * 0.6f, img.width.toFloat(), img.height.toFloat(), Paint().apply { color = a.copy(alpha = 0.5f).compositeOver(b) })
    return img
}

private fun Color.compositeOver(bg: Color): Color = Color(
    red = red * alpha + bg.red * (1 - alpha), green = green * alpha + bg.green * (1 - alpha), blue = blue * alpha + bg.blue * (1 - alpha), alpha = 1f,
)
