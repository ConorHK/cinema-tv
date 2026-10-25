package com.danielportelabyrne.cinema.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Density
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.desktop.input.NavAction
import com.danielportelabyrne.cinema.desktop.jellyfin.HomeShelves
import com.danielportelabyrne.cinema.desktop.ui.CinemaApp
import com.danielportelabyrne.cinema.desktop.ui.Dispatcher
import com.danielportelabyrne.cinema.desktop.ui.FocusHub
import com.danielportelabyrne.cinema.desktop.ui.ImageSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing
import org.jetbrains.skia.Bitmap
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import javax.swing.SwingUtilities

/**
 * Moving around a page with the D-pad, drawn headlessly at the Deck's size. The regression: pressing Down past the
 * last row moved focus into the side menu (it sits below-left), and Up from there only walked up the menu, so the page
 * could not be reached again.
 *
 * Everything that touches Compose runs on the AWT event thread, as in the app: scroll animations wait on a timer and
 * resume on the scene's dispatcher, and resuming them on another thread while the test renders races.
 */
class NavigationTest {
    private fun item(id: String, name: String, type: String, year: Int = 2023) = JellyfinItem(
        id = id, name = name, year = year, type = type, mediaSourceId = null, runtimeMin = 100, genres = listOf("Drama"),
        officialRating = "15", hasBackdrop = true, communityRating = 7.5, overview = "Overview for $name.", hasPrimaryImage = true)

    private val films = (1..12).map { item("film$it", "Film $it", "Movie") }
    private val series = (0..5).map { item("series$it", "Series $it", "Series", 2019 + it) }

    @Suppress("UNCHECKED_CAST")
    private fun <T> edt(block: () -> T): T {
        var result: Any? = null
        var failure: Throwable? = null
        SwingUtilities.invokeAndWait { try { result = block() } catch (e: Throwable) { failure = e } }
        failure?.let { throw it }
        return result as T
    }

    private val home = Files.createTempDirectory("nav-test").toFile()
    private val model = edt {
        CinemaModel(DesktopPlatform(home, home.resolve("cache")), CoroutineScope(SupervisorJob() + Dispatchers.Swing), ui = Dispatchers.Swing).apply {
            images = object : ImageSource {
                override fun cached(url: String) = ImageBitmap(300, 450)
                override fun load(url: String) = cached(url)
            }
            stack += Route.Browse
            library = films + series
            shelves = HomeShelves(resume = emptyList(), nextUp = emptyList(), films = films, series = series)
        }
    }
    private val dispatcher = Dispatcher(model, FocusHub())
    private val scene = edt { ImageComposeScene(960, 600, Density(1f), coroutineContext = Dispatchers.Swing) { CinemaApp(model, dispatcher) } }
    private var t = 0L
    private val railOpen get() = edt { model.railOpen }

    @After fun close() = edt { scene.close() }

    /** Renders [n] frames, pausing between them so timer-based animations (a 90 ms wait before a scroll) can run. */
    private fun frames(n: Int = 5) { repeat(n) { edt { scene.render(t) }; t += 100_000_000L; Thread.sleep(30) } }
    private fun look(): Int = edt { Bitmap.makeFromImage(scene.render(t)).readPixels()!!.contentHashCode() }
    private fun press(a: NavAction): Int { edt { dispatcher.handle(a) }; frames(); return look() }

    @Test(timeout = 120_000) fun downNeverEntersTheMenuAndUpComesBackToTheTop() {
        frames(12)
        var last = look()
        var unchanged = 0
        var steps = 0
        while (unchanged < 3 && steps < 30) {
            val now = press(NavAction.DOWN); steps++
            assertFalse("Down moved focus into the menu on press $steps", railOpen)
            unchanged = if (now == last) unchanged + 1 else 0
            last = now
        }
        assertTrue("the page should reach its last row", unchanged >= 3)
        val bottom = last

        unchanged = 0; steps = 0
        while (unchanged < 3 && steps < 40) {
            val now = press(NavAction.UP); steps++
            assertFalse("Up moved focus into the menu on press $steps", railOpen)
            unchanged = if (now == last) unchanged + 1 else 0
            last = now
        }
        assertTrue("Up should settle at the top of the page", unchanged >= 3)
        assertNotEquals("Up should have left the bottom of the page", bottom, last)
        // Still on the page: one more Up does nothing, and a Down moves again.
        assertEquals(last, press(NavAction.UP))
        assertNotEquals(last, press(NavAction.DOWN))
    }

    @Test(timeout = 120_000) fun theMenuOpensWithLeftFromTheLeftEdgeAndClosesWithRight() {
        frames(12)
        assertFalse(railOpen)
        // Put focus on the page, then go Left until the page's left edge is passed.
        edt { dispatcher.handle(NavAction.DOWN) }; frames()
        repeat(12) { if (!railOpen) { edt { dispatcher.handle(NavAction.LEFT) }; frames(8) } }
        assertTrue("Left from the left edge should open the menu", railOpen)
        edt { dispatcher.handle(NavAction.DOWN) }; frames()
        assertTrue("Down inside the menu stays in the menu", railOpen)
        edt { dispatcher.handle(NavAction.RIGHT) }; frames(12)
        assertFalse("Right should return to the page", railOpen)
    }
}
