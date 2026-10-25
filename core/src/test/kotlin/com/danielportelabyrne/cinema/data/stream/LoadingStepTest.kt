package com.danielportelabyrne.cinema.data.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadingStepTest {
    @Test fun noAnswerYetIsFindingPeers() = assertEquals(LoadingStep.FINDING_PEERS, LoadingStep.from(null, 0, 0))

    @Test fun connectingWithNobodyIsStillFindingPeers() = assertEquals(LoadingStep.FINDING_PEERS, LoadingStep.from("connecting", 0, 0))

    @Test fun connectingWithASeedOrPeerIsConnecting() {
        assertEquals(LoadingStep.CONNECTING, LoadingStep.from("connecting", 1, 0))
        assertEquals(LoadingStep.CONNECTING, LoadingStep.from("connecting", 0, 3))
    }

    @Test fun bufferingAndReadyAreTheLastStep() {
        assertEquals(LoadingStep.BUFFERING, LoadingStep.from("buffering", 0, 0))
        assertEquals(LoadingStep.BUFFERING, LoadingStep.from("ready", 12, 40))
    }

    @Test fun unknownPhaseFallsBackToTheFirstStep() = assertEquals(LoadingStep.FINDING_PEERS, LoadingStep.from("something-new", 5, 5))

    @Test fun speedIsFormattedOrHidden() {
        assertNull(LoadingStep.speed(null))
        assertNull(LoadingStep.speed(0))
        assertEquals("512 KB/s", LoadingStep.speed(512_000))
        assertEquals("24.6 MB/s", LoadingStep.speed(24_600_000))
        assertEquals("1 KB/s", LoadingStep.speed(300))
    }
}
