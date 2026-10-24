package com.danielportelabyrne.cinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ImageCacheTest {
    @Test fun cache_keys_keep_the_existing_sha1_format() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", ImageCache.sha1("abc"))
    }
    @get:Rule val folder = TemporaryFolder()

    private val fetches = Collections.synchronizedList(ArrayList<String>())
    private var gate = CountDownLatch(0)
    private var network = true

    private fun cache(dir: java.io.File? = folder.root, cap: Long = 1_000_000, workers: Int = 1) = ImageCache(
        dir, cap, 1_000_000,
        fetch = { url, _ -> gate.await(5, TimeUnit.SECONDS); fetches += url; if (network) "bytes of $url".toByteArray() else null },
        decode = { bytes, width, _ -> "${String(bytes)} @$width" },
        sizeOf = { it.length },
        deliver = { it.run() },
        workers = workers
    )

    private fun ImageCache<String>.get(url: String, width: Int = 0, tag: Any? = null): String? {
        val got = arrayOfNulls<String>(1); val done = CountDownLatch(1)
        load(url, emptyMap(), width, tag) { got[0] = it; done.countDown() }
        done.await(5, TimeUnit.SECONDS)
        return got[0]
    }

    @Test fun many_cards_asking_for_one_poster_cost_one_download() {
        gate = CountDownLatch(1)
        val c = cache()
        val delivered = AtomicInteger(); val all = CountDownLatch(5)
        repeat(5) { c.load("http://p/1.jpg", emptyMap(), 332, null) { delivered.incrementAndGet(); all.countDown() } }
        gate.countDown()
        assertTrue(all.await(5, TimeUnit.SECONDS))
        assertEquals(5, delivered.get())
        assertEquals(1, fetches.size)
    }

    @Test fun a_downloaded_picture_comes_from_disk_next_time_even_offline() {
        assertEquals("bytes of http://p/1.jpg @332", cache().get("http://p/1.jpg", 332))
        network = false
        // A new process: memory is empty, the disk is not.
        assertEquals("bytes of http://p/1.jpg @332", cache().get("http://p/1.jpg", 332))
        assertEquals(1, fetches.size)
    }

    @Test fun memory_is_keyed_by_width_but_the_disk_by_address() {
        val c = cache()
        c.get("http://p/b.jpg", 640); c.get("http://p/b.jpg", 960)
        assertEquals("bytes of http://p/b.jpg @960", c.cached("http://p/b.jpg", 960))
        assertEquals(1, fetches.size)   // the second width decoded the bytes already on disk
    }

    @Test fun the_newest_request_is_served_first() {
        gate = CountDownLatch(1)
        val c = cache()
        val order = Collections.synchronizedList(ArrayList<String>()); val all = CountDownLatch(4)
        c.load("http://p/0", emptyMap(), 0, null) { order += "0"; all.countDown() }   // taken by the worker, blocks on the gate
        Thread.sleep(100)
        listOf("1", "2", "3").forEach { n -> c.load("http://p/$n", emptyMap(), 0, null) { order += n; all.countDown() } }
        gate.countDown()
        assertTrue(all.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("0", "3", "2", "1"), order)
    }

    @Test fun an_urgent_request_goes_ahead_of_posters_asked_for_after_it() {
        gate = CountDownLatch(1)
        val c = cache()
        val order = Collections.synchronizedList(ArrayList<String>()); val all = CountDownLatch(4)
        c.load("http://p/0", emptyMap(), 0, null) { order += "0"; all.countDown() }   // taken by the worker, blocks on the gate
        Thread.sleep(100)
        c.load("http://p/backdrop", emptyMap(), 0, null, urgent = true) { order += "backdrop"; all.countDown() }
        listOf("1", "2").forEach { n -> c.load("http://p/$n", emptyMap(), 0, null) { order += n; all.countDown() } }
        gate.countDown()
        assertTrue(all.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("0", "backdrop", "2", "1"), order)
    }

    @Test fun visible_artwork_does_not_wait_for_an_unrelated_download() {
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val visible = CountDownLatch(1)
        val c = ImageCache<String>(folder.root, 1_000_000, 1_000_000,
            fetch = { url, _ ->
                if (url == "slow") { blocked.countDown(); release.await(3, TimeUnit.SECONDS) }
                url.toByteArray()
            }, decode = { bytes, _, _ -> String(bytes) }, sizeOf = { it.length },
            deliver = { it.run() }, workers = 1, priorityWorkers = 1)
        try {
            c.load("slow", emptyMap(), 100, null) { }
            assertTrue(blocked.await(1, TimeUnit.SECONDS))
            c.load("visible", emptyMap(), 100, null, urgent = true) { visible.countDown() }
            assertTrue("Visible image queued behind another page's download", visible.await(500, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); c.close() }
    }

    @Test fun big_pictures_are_not_pushed_out_by_small_ones() {
        val c = ImageCache(folder.root, 1_000_000, 100,
            fetch = { url, _ -> url.toByteArray() }, decode = { bytes, _, _ -> String(bytes) },
            sizeOf = { if (it.contains("big")) 60 else 30 }, deliver = { it.run() }, workers = 1,
            largeCapBytes = 100, largeBytes = 50)
        val done = CountDownLatch(5)
        c.load("http://p/big", emptyMap(), 0, null) { done.countDown() }
        repeat(4) { i -> c.load("http://p/small$i", emptyMap(), 0, null) { done.countDown() } }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals("http://p/big", c.cached("http://p/big", 0))   // four posters overflowed their own space, not this
    }

    @Test fun a_rebuilt_page_drops_what_its_old_page_queued() {
        gate = CountDownLatch(1)
        val c = cache()
        val oldPage = Any(); val newPage = Any()
        val got = Collections.synchronizedList(ArrayList<String>()); val newDone = CountDownLatch(1)
        c.load("http://p/busy", emptyMap(), 0, null) { }
        Thread.sleep(100)
        repeat(3) { i -> c.load("http://p/old$i", emptyMap(), 0, oldPage) { got += "old$i" } }
        c.load("http://p/shared", emptyMap(), 0, oldPage) { got += "shared-old" }
        c.load("http://p/shared", emptyMap(), 0, newPage) { got += "shared-new"; newDone.countDown() }
        c.cancel(oldPage)
        assertEquals(1, c.queued)   // only the picture the new page still wants
        gate.countDown()
        assertTrue(newDone.await(5, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertEquals(listOf("shared-new"), got.toList())
        assertTrue(fetches.none { it.contains("old") })
    }

    @Test fun the_disk_cache_stays_under_its_cap_dropping_the_least_recently_used() {
        val c = cache(cap = 100)
        (0 until 6).forEach { i -> c.get("http://p/$i.jpg") }   // ~20 bytes each
        val total = folder.root.listFiles()!!.sumOf { it.length() }
        assertTrue("disk holds $total bytes", total <= 100)
        assertTrue(java.io.File(folder.root, ImageCache.sha1("http://p/5.jpg")).exists())   // the newest is kept
    }

    @Test fun failures_deliver_nothing_and_are_tried_again_later() {
        network = false
        val c = cache(dir = null)
        val called = AtomicInteger()
        c.load("http://p/x", emptyMap(), 0, null) { called.incrementAndGet() }
        Thread.sleep(200)
        assertEquals(0, called.get())
        network = true
        assertEquals("bytes of http://p/x @0", c.get("http://p/x"))
    }

    @Test fun the_memory_lru_drops_the_least_recently_used() {
        val lru = ByteLru<String>(10) { it.length }
        lru.put("a", "aaaa"); lru.put("b", "bbbb")
        lru.get("a")
        lru.put("c", "cccc")
        assertEquals(null, lru.get("b")); assertEquals("aaaa", lru.get("a")); assertEquals("cccc", lru.get("c"))
    }
}
