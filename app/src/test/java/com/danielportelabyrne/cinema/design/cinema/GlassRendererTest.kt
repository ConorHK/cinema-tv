package com.danielportelabyrne.cinema.design.cinema

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassRendererTest {
    private val w = 240; private val h = 135

    @Test fun keeps_the_size_and_is_opaque() {
        val out = GlassRenderer.render(IntArray(w * h) { 0xFF336699.toInt() }, w, h)
        assertEquals(w * h, out.size)
        assertTrue(out.all { it ushr 24 == 0xFF })
    }

    @Test fun the_picture_is_the_light() {
        // Black stays dark (no lamp in front lights it up); a bright picture stays bright.
        val dark = GlassRenderer.render(IntArray(w * h) { 0xFF000000.toInt() }, w, h)
        val light = GlassRenderer.render(IntArray(w * h) { 0xFFE0E0E0.toInt() }, w, h)
        fun mean(px: IntArray) = px.map { ((it shr 16) and 0xFF) + ((it shr 8) and 0xFF) + (it and 0xFF) }.average() / 3
        assertTrue("dark ${mean(dark)}", mean(dark) < 40)
        assertTrue("light ${mean(light)}", mean(light) > 180)
    }

    @Test fun the_glass_varies_across_the_picture() {
        val out = GlassRenderer.render(IntArray(w * h) { 0xFF808080.toInt() }, w, h)
        val values = out.map { (it shr 8) and 0xFF }
        assertTrue("ripples should vary the light", values.max() - values.min() > 30)
    }
}
