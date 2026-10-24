package com.danielportelabyrne.cinema.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ReachabilityTest {
    private var now = 1_000_000L
    private val gate = Reachability(30_000L) { now }

    @Test fun a_timeout_while_connecting_means_the_server_is_not_there() {
        assertTrue(gate.failed(SocketTimeoutException("connect timed out"), duringConnect = true))
        assertTrue(gate.backingOff)
    }

    @Test fun a_timeout_while_reading_is_a_slow_server_not_a_missing_one() {
        assertFalse(gate.failed(SocketTimeoutException("Read timed out"), duringConnect = false))
        assertFalse(gate.backingOff)
    }

    @Test fun refused_unroutable_and_unresolvable_count_whenever_they_happen() {
        assertTrue(Reachability.isUnreachable(ConnectException(), duringConnect = false))
        assertTrue(Reachability.isUnreachable(UnknownHostException(), duringConnect = true))
        assertFalse(Reachability.isUnreachable(java.io.IOException("reset"), duringConnect = false))
    }

    @Test fun the_backoff_lasts_its_window_then_lifts() {
        gate.failed(ConnectException(), duringConnect = true)
        now += 29_999; assertTrue(gate.backingOff)
        now += 2; assertFalse(gate.backingOff)
    }

    @Test fun any_answer_from_the_server_ends_the_backoff() {
        gate.failed(ConnectException(), duringConnect = true)
        gate.reached()
        assertFalse(gate.backingOff)
    }

    @Test fun callers_arriving_during_a_fetch_share_it() {
        val flight = SingleFlight<Int>()
        val calls = AtomicInteger()
        val started = CountDownLatch(1); val release = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        val first = pool.submit<Int> { flight.run { calls.incrementAndGet(); started.countDown(); release.await(); 7 } }
        started.await(2, TimeUnit.SECONDS)
        val others = (1..3).map { pool.submit<Int> { flight.run { calls.incrementAndGet(); 99 } } }
        Thread.sleep(100)
        release.countDown()
        assertEquals(7, first.get(2, TimeUnit.SECONDS))
        others.forEach { assertEquals(7, it.get(2, TimeUnit.SECONDS)) }
        assertEquals(1, calls.get())
        pool.shutdownNow()
    }

    @Test fun a_finished_fetch_does_not_answer_the_next_caller() {
        val flight = SingleFlight<Int>()
        assertEquals(1, flight.run { 1 })
        assertEquals(2, flight.run { 2 })
    }

    @Test(expected = IllegalStateException::class) fun a_failed_fetch_throws_to_its_callers() {
        SingleFlight<Int>().run { throw IllegalStateException("boom") }
    }
}
