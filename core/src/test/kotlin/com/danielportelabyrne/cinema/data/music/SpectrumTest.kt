package com.danielportelabyrne.cinema.data.music

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

class SpectrumTest {
    private fun tone(hz: Double, rate: Int, n: Int, amp: Float = 0.8f) = FloatArray(n) { (amp * sin(2 * PI * hz * it / rate)).toFloat() }

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
        val low = Spectrum(bands = 16).apply { repeat(4) { feed(tone(80.0, rate, 2048), sampleRate = rate) } }.levels
        val high = Spectrum(bands = 16).apply { repeat(4) { feed(tone(8000.0, rate, 2048), sampleRate = rate) } }.levels
        assertTrue(low.indices.maxByOrNull { low[it] }!! < 4)
        assertTrue(high.indices.maxByOrNull { high[it] }!! > 11)
        assertTrue(low.all { it in 0f..1f })
    }

    @Test fun silence_reads_as_zero_and_levels_fall_slowly() {
        val rate = 48_000
        val s = Spectrum(bands = 8)
        s.feed(FloatArray(2048), sampleRate = rate)
        assertTrue(s.levels.all { it == 0f })
        repeat(4) { s.feed(tone(200.0, rate, 2048), sampleRate = rate) }
        val peak = s.levels.maxOrNull()!!
        s.decay()
        val after = s.levels.maxOrNull()!!
        assertTrue(after < peak && after > peak * 0.5f)
        s.reset()
        assertTrue(s.levels.all { abs(it) == 0f })
    }

    @Test fun waits_for_a_full_window() {
        val s = Spectrum(bands = 8)
        s.feed(tone(200.0, 48_000, 1000), sampleRate = 48_000)
        assertTrue(s.levels.all { it == 0f })
    }
}
