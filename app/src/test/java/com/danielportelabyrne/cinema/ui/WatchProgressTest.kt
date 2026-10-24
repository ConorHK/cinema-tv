package com.danielportelabyrne.cinema.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchProgressTest {
    @Test fun an_unstarted_film_offers_watch_now_only() {
        val s = WatchProgress.of(played = false, resumeMs = 0, runtimeMin = 100)
        assertEquals(WatchState.NotStarted, s)
        assertEquals(listOf(FilmAction.WATCH), WatchProgress.filmActions(s))
        assertEquals("Watch now", WatchProgress.label(FilmAction.WATCH, s))
    }

    @Test fun a_part_watched_film_offers_continue_with_time_left_then_restart() {
        val s = WatchProgress.of(played = false, resumeMs = 58 * 60_000L, runtimeMin = 100)
        assertEquals(WatchState.InProgress(58 * 60_000L, 0.58f, 42), s)
        assertEquals(listOf(FilmAction.CONTINUE, FilmAction.RESTART), WatchProgress.filmActions(s))
        assertEquals("Continue · 42 min left", WatchProgress.label(FilmAction.CONTINUE, s))
        assertEquals("Restart", WatchProgress.label(FilmAction.RESTART, s))
    }

    @Test fun long_times_left_read_in_hours() {
        val s = WatchProgress.of(played = false, resumeMs = 5 * 60_000L, runtimeMin = 130)
        assertEquals("Continue · 2 h 05 min left", WatchProgress.label(FilmAction.CONTINUE, s))
    }

    @Test fun without_a_running_time_continue_has_no_time() {
        val s = WatchProgress.of(played = false, resumeMs = 60_000, runtimeMin = null)
        assertTrue(s is WatchState.InProgress)
        assertEquals("Continue watching", WatchProgress.label(FilmAction.CONTINUE, s))
        assertNull(WatchProgress.episodeBar(s))   // no length, no bar
    }

    @Test fun a_watched_film_is_watch_now_again() {
        val s = WatchProgress.of(played = true, resumeMs = 0, runtimeMin = 90)
        assertEquals(WatchState.Watched, s)
        assertEquals(listOf(FilmAction.WATCH), WatchProgress.filmActions(s))
    }

    @Test fun played_wins_over_a_stale_resume_point() {
        assertEquals(WatchState.Watched, WatchProgress.of(played = true, resumeMs = 30 * 60_000L, runtimeMin = 90))
    }

    @Test fun episodes_show_a_bar_while_part_watched_and_a_tick_once_watched() {
        val notStarted = WatchProgress.of(false, 0, 45)
        val half = WatchProgress.of(false, 22 * 60_000L + 30_000, 45)
        val done = WatchProgress.of(true, 0, 45)
        assertNull(WatchProgress.episodeBar(notStarted)); assertFalse(WatchProgress.episodeShowsTick(notStarted))
        assertEquals(0.5f, WatchProgress.episodeBar(half)!!, 0.001f); assertFalse(WatchProgress.episodeShowsTick(half))
        assertNull(WatchProgress.episodeBar(done)); assertTrue(WatchProgress.episodeShowsTick(done))
    }
}
