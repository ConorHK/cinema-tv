package com.danielportelabyrne.cinema.data.music

import org.junit.Assert.*
import org.junit.Test

class BurnInTest {
    @Test fun chrome_dims_gently_after_thirty_quiet_seconds() {
        assertEquals(1f, BurnIn.chromeAlpha(0), 0f)
        assertEquals(1f, BurnIn.chromeAlpha(30_000), 0f)
        assertEquals(0.8f, BurnIn.chromeAlpha(32_000), 0.001f)
        assertEquals(BurnIn.DIM, BurnIn.chromeAlpha(60_000), 0f)
    }
}
