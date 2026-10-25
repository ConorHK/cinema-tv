package com.danielportelabyrne.cinema.data.stream

import org.junit.Assert.*
import org.junit.Test

class ProviderStatsTest {
    private val day = 86_400_000L
    private val now = 100 * day

    private fun rec(ageDays: Long, failed: Boolean = false, bufN: Int = 0, bufMs: Long = 0, startup: Long = 2000, observed: Long = 50_000_000) =
        PlaybackRecord("tt1", "P", Resolution.P1080, VideoCodec.H264, SourceType.WEB_DL, 8_000_000, startup, observed, bufN, bufMs, 600_000, !failed, failed, now - ageDays * day)

    @Test fun empty_history_is_neutral() = assertEquals(ProviderStats.NONE, ProviderStatsCalculator.compute(emptyList(), now))

    @Test fun recent_failures_raise_failure_rate() {
        val s = ProviderStatsCalculator.compute(listOf(rec(1, failed = true), rec(2, failed = true), rec(3)), now)
        assertTrue(s.failureRate > 0.5)
        assertTrue(s.reliability < 0.7)
    }

    @Test fun old_failures_fade() {
        val recent = ProviderStatsCalculator.compute(listOf(rec(1, failed = true), rec(1), rec(2)), now).failureRate
        val ancient = ProviderStatsCalculator.compute(listOf(rec(200, failed = true), rec(1), rec(2)), now).failureRate
        assertTrue(ancient < recent / 3)
    }

    @Test fun brief_buffering_is_ignored_but_real_buffering_counts() {
        assertEquals(0.0, ProviderStatsCalculator.compute(listOf(rec(1, bufN = 1, bufMs = 800)), now).bufferRate, 0.0)
        assertTrue(ProviderStatsCalculator.compute(listOf(rec(1, bufN = 4, bufMs = 12_000)), now).bufferRate > 0.9)
    }

    @Test fun averages_startup_and_throughput() {
        val s = ProviderStatsCalculator.compute(listOf(rec(0, startup = 1000, observed = 40_000_000), rec(0, startup = 3000, observed = 80_000_000)), now)
        assertEquals(2000L, s.averageStartupMs)
        assertEquals(60_000_000L, s.averageObservedBps)
    }
}
