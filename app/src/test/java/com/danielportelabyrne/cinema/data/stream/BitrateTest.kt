package com.danielportelabyrne.cinema.data.stream

import org.junit.Assert.*
import org.junit.Test

class BitrateTest {
    @Test fun average_is_size_bits_over_runtime() {
        val size = 18L * (1L shl 30)   // 18 GB over 2 hours
        assertEquals(size * 8 / 7200, BitrateEstimator.averageBps(size, 7200))
    }

    @Test fun unknown_when_size_or_runtime_missing() {
        assertNull(BitrateEstimator.averageBps(null, 7200))
        assertNull(BitrateEstimator.averageBps(1000, null))
        assertNull(BitrateEstimator.averageBps(1000, 0))
    }

    @Test fun remux_gets_more_peak_headroom_than_web() {
        val size = 20L * (1L shl 30)
        val remux = BitrateEstimator.requiredBps(StreamMetadata(source = SourceType.REMUX, sizeBytes = size), 7200)
        val web = BitrateEstimator.requiredBps(StreamMetadata(source = SourceType.WEB_DL, sizeBytes = size), 7200)
        assertTrue(remux.first > web.first)
        assertTrue(remux.second && web.second)
    }

    @Test fun unknown_size_falls_back_to_typical_and_is_flagged_unknown() {
        val r = BitrateEstimator.requiredBps(StreamMetadata(resolution = Resolution.P2160, source = SourceType.REMUX), 7200)
        assertFalse(r.second)
        assertTrue(r.first > 70_000_000L)
    }
}
