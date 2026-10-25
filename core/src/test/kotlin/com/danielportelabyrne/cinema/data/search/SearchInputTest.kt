package com.danielportelabyrne.cinema.data.search

import com.danielportelabyrne.cinema.data.search.Qwerty.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QwertyEditTest {
    @Test fun layoutIsNumbersThenThreeLetterRows() {
        assertEquals(listOf("1234567890", "qwertyuiop", "asdfghjkl", "zxcvbnm"), Qwerty.ROWS)
        assertEquals(26, Qwerty.ROWS.drop(1).sumOf { it.length })
    }

    @Test fun typingSpacingAndDeleting() {
        var t = ""
        "heat".forEach { t = Qwerty.type(t, Key.Char(it)) }
        t = Qwerty.type(t, Key.Space); t = Qwerty.type(t, Key.Char('1'))
        assertEquals("heat 1", t)
        assertEquals("heat ", Qwerty.type(t, Key.Delete))
        assertEquals("", Qwerty.type(t, Key.Clear))
    }

    @Test fun noLeadingOrDoubledSpaces() {
        assertEquals("", Qwerty.type("", Key.Space))
        assertEquals("a ", Qwerty.type("a ", Key.Space))
    }

    @Test fun deleteOnEmptyStaysEmpty() = assertEquals("", Qwerty.type("", Key.Delete))

    @Test fun lengthIsCapped() {
        val long = "x".repeat(Qwerty.MAX_LENGTH)
        assertEquals(long, Qwerty.type(long, Key.Char('y')))
    }
}

class VoiceStateTest {
    private val start = VoiceState.Idle

    @Test fun noRecogniserIsUnavailable() =
        assertTrue(start.next(VoiceState.Event.Start(recogniserAvailable = false, permitted = true)) is VoiceState.Unavailable)

    @Test fun withoutPermissionItAsksThenListensOrExplains() {
        val asking = start.next(VoiceState.Event.Start(true, permitted = false))
        assertEquals(VoiceState.Requesting, asking)
        assertEquals(VoiceState.Listening(), asking.next(VoiceState.Event.Permission(true)))
        assertTrue(asking.next(VoiceState.Event.Permission(false)) is VoiceState.Unavailable)
    }

    @Test fun partialsAndLoudnessThenTheFinalText() {
        var s = start.next(VoiceState.Event.Start(true, true))
        s = s.next(VoiceState.Event.Partial(" Tenzing "))
        s = s.next(VoiceState.Event.Rms(10f))
        assertEquals(VoiceState.Listening("Tenzing", 1f), s)
        assertEquals(VoiceState.Done("Tenzing mountain"), s.next(VoiceState.Event.Final("Tenzing mountain")))
    }

    @Test fun anEmptyFinalFallsBackToThePartialOrSaysNothingWasHeard() {
        val heard = VoiceState.Listening("Heat")
        assertEquals(VoiceState.Done("Heat"), heard.next(VoiceState.Event.Final("")))
        assertEquals(VoiceState.Unavailable(VoiceState.NO_MATCH), VoiceState.Listening().next(VoiceState.Event.Final("  ")))
    }

    @Test fun errorsBecomePlainWords() {
        assertEquals(VoiceState.Unavailable(VoiceState.NO_MATCH), VoiceState.Listening().next(VoiceState.Event.Error(7)))
        assertTrue((VoiceState.Listening().next(VoiceState.Event.Error(2)) as VoiceState.Unavailable).reason.contains("internet"))
    }

    @Test fun cancelAlwaysReturnsToIdleAndStrayEventsAreIgnored() {
        assertEquals(VoiceState.Idle, VoiceState.Listening("x").next(VoiceState.Event.Cancel))
        assertEquals(VoiceState.Idle, VoiceState.Idle.next(VoiceState.Event.Partial("late")))
        assertEquals(VoiceState.Done("a"), VoiceState.Done("a").next(VoiceState.Event.Error(7)))
    }
}
