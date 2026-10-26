package com.danielportelabyrne.cinema.data.music

import org.junit.Assert.*
import org.junit.Test

class VisualsTest {
    @Test fun springs_settle_on_the_target_with_little_overshoot() {
        val s = Springs(1)
        val target = floatArrayOf(0.8f)
        var peak = 0f
        repeat(120) { s.step(target, 16); peak = maxOf(peak, s.values[0]) }
        assertEquals(0.8f, s.values[0], 0.01f)
        assertTrue(peak < 0.8f * 1.12f)
    }

    @Test fun springs_move_the_same_at_any_frame_rate() {
        val a = Springs(1); val b = Springs(1); val t = floatArrayOf(1f)
        repeat(30) { a.step(t, 33) }
        repeat(99) { b.step(t, 10) }
        assertEquals(a.values[0], b.values[0], 0.02f)
    }

    @Test fun groups_keep_bass_first_and_show_a_lone_peak() {
        val levels = FloatArray(64).also { it[2] = 1f; it[60] = 0.5f }
        val out = FloatArray(6)
        Bands.group(levels, out)
        assertTrue(out[0] > 0.5f); assertTrue(out[5] > 0.25f); assertEquals(0f, out[2], 0f)
    }

    @Test fun palette_finds_the_cover_colours() {
        val red = 0xFFC02020.toInt(); val blue = 0xFF2040C0.toInt()
        val px = IntArray(100) { if (it < 60) red else blue }
        val p = ArtPalette.pick(px, 3)
        assertEquals(3, p.size)
        assertTrue(ArtPalette.distance(p[0], ArtPalette.glowable(red)) < 30f)
        assertTrue(ArtPalette.distance(p[1], ArtPalette.glowable(blue)) < 30f)
        assertEquals(ArtPalette.CINEMA[0], p[2])
    }

    @Test fun a_grey_cover_falls_back_to_cinema_colours() {
        val p = ArtPalette.pick(IntArray(50) { 0xFF808080.toInt() }, 2)
        assertArrayEquals(ArtPalette.CINEMA.copyOf(2), p)
    }

    @Test fun visual_cycles_and_reads_back() {
        assertEquals(MusicVisual.GLASS, MusicVisual.ARCHES.next())
        assertEquals(MusicVisual.ARCHES, MusicVisual.GLASS.next())
        assertEquals(MusicVisual.ARCHES, MusicVisual.of(null))
        assertEquals(MusicVisual.GLASS, MusicVisual.of("GLASS"))
    }
}
