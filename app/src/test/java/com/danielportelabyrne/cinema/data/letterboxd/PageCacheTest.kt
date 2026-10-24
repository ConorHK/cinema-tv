package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PageCacheTest {
    @Test fun saved_pages_are_delivered_while_both_network_workers_are_blocked() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val started = CountDownLatch(2)
        val release = CountDownLatch(1)
        val cache = PageCache(dir, { path ->
            if (path.startsWith("/slow")) { started.countDown(); release.await(3, TimeUnit.SECONDS) }
            path
        })
        try {
            assertEquals("/saved", cache.pageNow("/saved", 60000))
            repeat(2) { cache.load("/slow$it", 0, {}, {}) }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            val delivered = CountDownLatch(1)
            cache.load("/saved", 60000, { assertEquals("/saved", it); delivered.countDown() }, { fail("Missing saved page") })
            assertTrue("A saved page queued behind network I/O", delivered.await(500, TimeUnit.MILLISECONDS))
        } finally { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun concurrent_cold_readers_share_one_request_and_all_receive_the_result() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val cache = PageCache(dir, { calls.incrementAndGet(); release.await(3, TimeUnit.SECONDS); "fresh" })
        try {
            val delivered = CountDownLatch(8)
            repeat(8) { cache.load("/same", 60000, { assertEquals("fresh", it); delivered.countDown() }, { fail("Missing page") }) }
            Thread.sleep(100)
            release.countDown()
            assertTrue(delivered.await(2, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
        } finally { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun stale_copy_is_immediate_and_a_changed_refresh_is_delivered_once() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        var now = 1000L
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val cache = PageCache(dir, {
            if (calls.incrementAndGet() == 1) "old" else { release.await(3, TimeUnit.SECONDS); "new" }
        }, { now })
        try {
            assertEquals("old", cache.pageNow("/page", 0))
            now += 2000
            val old = CountDownLatch(1)
            val fresh = CountDownLatch(1)
            cache.load("/page", 1000, { if (it == "old") old.countDown() else if (it == "new") fresh.countDown() }, { fail() })
            assertTrue(old.await(500, TimeUnit.MILLISECONDS))
            assertEquals(1L, fresh.count)
            release.countDown()
            assertTrue(fresh.await(2, TimeUnit.SECONDS))
        } finally { release.countDown(); cache.close(); dir.deleteRecursively() }
    }

    @Test fun disk_copy_survives_a_new_cache_instance_and_failed_refresh() {
        val dir = Files.createTempDirectory("lb-cache").toFile()
        val first = PageCache(dir, { "saved" })
        assertEquals("saved", first.pageNow("/page", 60000))
        first.close()
        val second = PageCache(dir, { null })
        try {
            val delivered = CountDownLatch(1)
            second.load("/page", -1, { assertEquals("saved", it); delivered.countDown() }, { fail("Lost offline copy") })
            assertTrue(delivered.await(1, TimeUnit.SECONDS))
            assertEquals("saved", second.pageNow("/page", -1))
        } finally { second.close(); dir.deleteRecursively() }
    }
}
