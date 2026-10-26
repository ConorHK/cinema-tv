package com.danielportelabyrne.cinema.data.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrumTest {
    private fun tone(hz: Double, rate: Int, n: Int, amp: Float = 0.8f) = FloatArray(n) { (amp * sin(2 * PI * hz * it / rate)).toFloat() }

    private fun settle(s: Spectrum, samples: FloatArray, rate: Int) = s.apply { repeat(30) { update(samples, rate, 16) } }.levels

    @Test fun fft_finds_a_pure_tone_in_its_bin() {
        val n = 64
        val re = FloatArray(n) { sin(2 * PI * 5 * it / n).toFloat() }; val im = FloatArray(n)
        Spectrum.fft(re, im)
        val mags = (0 until n / 2).map { kotlin.math.hypot(re[it], im[it]) }
        assertEquals(5, mags.indices.maxByOrNull { mags[it] })
        assertEquals(n / 2f, mags[5], 0.01f)
    }

    @Test fun a_low_tone_lights_low_bands_and_a_high_tone_high_ones() {
        val rate = 44_100
        val low = settle(Spectrum(), tone(80.0, rate, 4096), rate)
        val high = settle(Spectrum(), tone(8000.0, rate, 4096), rate)
        assertTrue(low.indices.maxByOrNull { low[it] }!! < 16)
        assertTrue(high.indices.maxByOrNull { high[it] }!! > 48)
        assertTrue(low.all { it in 0f..1f })
    }

    @Test fun neighbouring_bass_bands_differ() {
        val rate = 44_100
        val l = settle(Spectrum(), tone(60.0, rate, 4096), rate)
        assertTrue((0 until 10).zipWithNext().count { (a, b) -> l[a] == l[b] } < 3)
    }

    @Test fun a_quiet_track_still_fills_the_range() {
        val rate = 48_000
        val quiet = settle(Spectrum(), tone(440.0, rate, 4096, amp = 0.01f), rate)
        assertTrue(quiet.maxOrNull()!! > 0.9f)
    }

    @Test fun silence_reads_as_zero_and_levels_fall_smoothly() {
        val rate = 48_000
        val s = Spectrum(bands = 16)
        s.update(FloatArray(4096), rate, 16)
        assertTrue(s.levels.all { it == 0f })
        settle(s, tone(200.0, rate, 4096), rate)
        val peak = s.levels.maxOrNull()!!
        s.decay(16)
        val after = s.levels.maxOrNull()!!
        assertTrue(after < peak && after > peak * 0.5f)
        s.reset()
        assertTrue(s.levels.all { it == 0f })
    }

    @Test fun history_returns_what_plays_now_not_what_was_decoded_last() {
        val h = AudioHistory(capacity = 1000)
        val rate = 1000   // one sample per millisecond
        h.add(FloatArray(500) { it.toFloat() }, 500, startUs = 0, sampleRate = rate)
        val out = FloatArray(10)
        assertTrue(h.window(playingUs = 200_000, out = out))
        assertEquals(190f, out.first(), 0f); assertEquals(199f, out.last(), 0f)
        assertFalse(h.window(playingUs = 600_000, out = out))   // not decoded yet
        assertFalse(h.window(playingUs = 5_000, out = out))     // too early for a full window
        h.clear()
        assertFalse(h.window(playingUs = 200_000, out = out))
    }
}
