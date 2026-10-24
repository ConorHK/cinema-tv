package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.Executor

class ShelfCacheTest {
    @get:Rule val folder = TemporaryFolder()

    private fun answer(vararg names: String) = """{"data":{"popularTitles":{"edges":[""" +
        names.mapIndexed { i, n -> """{"node":{"id":"t$i","objectType":"MOVIE","content":{"title":"$n","externalIds":{"tmdbId":"${100 + i}"}}}}""" }.joinToString(",") +
        "]}}}"

    private var now = 10_000_000L
    private var served = answer("Alpha", "Beta")
    private val fetched = ArrayList<String>()
    private val queued = ArrayList<Runnable>()
    private val later = Executor { queued += it }
    private fun cache(dir: java.io.File = folder.root) = ShelfCache(dir, 1000L, { now }, { body -> fetched += body; served }, later)

    @Test fun a_missing_shelf_is_fetched_kept_on_disk_and_read_back_by_the_next_session() {
        assertEquals(listOf("Alpha", "Beta"), cache().titles("q1", keep = true).map { it.name })
        served = "unreachable"
        // A new cache (a new process) reads the file instead of the network.
        assertEquals(listOf("Alpha", "Beta"), cache().titles("q1", keep = true).map { it.name })
        assertEquals(1, fetched.size)
    }

    @Test fun a_fresh_shelf_is_parsed_once_and_then_served_from_memory() {
        val c = cache()
        c.titles("q1", keep = true)
        folder.root.listFiles()!!.forEach { it.delete() }   // memory, not the file, answers now
        assertEquals(2, c.titles("q1", keep = true).size)
        assertEquals(1, fetched.size)
        assertTrue(queued.isEmpty())
    }

    @Test fun an_old_shelf_is_shown_at_once_and_refreshed_behind_the_scenes() {
        val c = cache()
        c.titles("q1", keep = true)
        now += 5000
        served = answer("Gamma")
        assertEquals(listOf("Alpha", "Beta"), c.titles("q1", keep = true).map { it.name })   // stale, no waiting
        assertEquals(1, queued.size)
        assertEquals(0, c.titles("q1", keep = true).size - 2)   // a second ask while refreshing queues nothing more
        assertEquals(1, queued.size)
        queued.removeAt(0).run()
        assertEquals(listOf("Gamma"), c.titles("q1", keep = true).map { it.name })
        assertEquals(listOf("Gamma"), cache().titles("q1", keep = true).map { it.name })   // and on disk
    }

    @Test fun an_empty_or_failed_refresh_keeps_the_good_copy() {
        val c = cache()
        c.titles("q1", keep = true)
        now += 5000
        served = """{"data":{}}"""
        c.titles("q1", keep = true); queued.removeAt(0).run()
        assertEquals(2, cache().titles("q1", keep = true).size)
    }

    @Test fun different_requests_are_different_shelves() {
        val c = cache()
        c.titles("q1", keep = true)
        served = answer("Other")
        assertEquals(listOf("Other"), c.titles("q2", keep = true).map { it.name })
    }

    @Test fun what_someone_typed_is_never_kept() {
        val c = cache()
        c.titles("search", keep = false)
        c.titles("search", keep = false)
        assertEquals(2, fetched.size)
        assertTrue(folder.root.listFiles()!!.isEmpty())
    }

    @Test fun nothing_cached_and_no_network_is_an_empty_shelf() {
        served = "garbage"
        assertTrue(cache().titles("q1", keep = true).isEmpty())
    }
}
