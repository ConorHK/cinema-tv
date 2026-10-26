package com.danielportelabyrne.cinema.data.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BurnInTest {
    @Test fun drift_stays_in_range_and_keeps_moving() {
        val points = (0L..30 * 60_000L step 5_000L).map { BurnIn.drift(it, 24f) }
        assertTrue(points.all { abs(it.first) <= 24f && abs(it.second) <= 24f })
        assertEquals(0f to 0f, BurnIn.drift(0, 24f))
        assertTrue(points.maxOf { it.first } > 20f && points.minOf { it.second } < -20f)
        val step = BurnIn.drift(1_000, 24f)
        assertTrue(abs(step.first) < 0.5f)   // slow enough not to be seen
    }

    @Test fun chrome_dims_gently_after_thirty_quiet_seconds() {
        assertEquals(1f, BurnIn.chromeAlpha(0), 0f)
        assertEquals(1f, BurnIn.chromeAlpha(30_000), 0f)
        assertEquals(0.8f, BurnIn.chromeAlpha(32_000), 0.001f)
        assertEquals(BurnIn.DIM, BurnIn.chromeAlpha(60_000), 0f)
    }
}
