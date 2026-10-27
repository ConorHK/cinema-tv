package com.danielportelabyrne.cinema.data.music

import com.danielportelabyrne.cinema.data.music.LyricKeys.Action
import org.junit.Assert.*
import org.junit.Test

class LyricKeysTest {
    @Test fun a_tap_follows_once_no_second_tap_can_follow() {
        val k = LyricKeys()
        assertNull(k.down(-1, 0, 0))
        assertNull(k.up(80))
        assertNull(k.due(250))
        assertEquals(Action.Follow, k.due(301))
        assertNull(k.due(400))
    }

    @Test fun a_double_tap_hides_either_way() {
        val k = LyricKeys()
        k.down(-1, 0, 0); k.up(70)
        assertEquals(Action.Hide, k.down(1, 0, 200))
        k.up(260)
        assertNull(k.due(1_000))
    }

    @Test fun holding_scrolls_until_let_go() {
        val k = LyricKeys()
        k.down(1, 0, 0)
        assertNull(k.down(1, 1, 300))
        assertEquals(Action.ScrollStart(1), k.down(1, 2, 420))
        assertNull(k.down(1, 3, 470))
        assertEquals(Action.ScrollEnd, k.up(900))
        assertNull(k.due(2_000))
    }

    @Test fun a_tap_after_a_scroll_follows() {
        val k = LyricKeys()
        k.down(-1, 0, 0); k.down(-1, 1, 450); k.up(800)
        k.down(-1, 0, 1_500); k.up(1_560)
        assertEquals(Action.Follow, k.due(1_900))
    }
}
