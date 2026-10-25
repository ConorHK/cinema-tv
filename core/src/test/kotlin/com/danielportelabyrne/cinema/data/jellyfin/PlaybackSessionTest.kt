package com.danielportelabyrne.cinema.data.jellyfin

import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionTest {
    @Test fun reports_only_between_start_and_stop() {
        val session = JellyfinPlaybackSession { "first" }
        assertFalse(session.accept("Progress"))
        assertFalse(session.accept("Stopped"))
        assertTrue(session.accept("Playing"))
        assertFalse(session.accept("Playing"))
        assertTrue(session.accept("Progress"))
        assertTrue(session.accept("Stopped"))
        assertFalse(session.accept("Stopped"))
        assertFalse(session.accept("Progress"))
    }

    @Test fun returning_to_a_stopped_player_opens_a_new_session() {
        var count = 0
        val session = JellyfinPlaybackSession { (++count).toString() }
        session.accept("Playing")
        val original = session.id
        session.accept("Stopped")
        assertTrue(session.stopped)
        assertTrue(session.accept("Playing"))
        assertFalse(session.stopped)
        assertNotEquals(original, session.id)
        assertTrue(session.accept("Progress"))
    }

    @Test fun server_positions_override_old_local_positions_including_short_positions_and_restarts() {
        assertEquals(20_000L, JellyfinApi.resumePosition(20_000L, 150_000L))
        assertEquals(0L, JellyfinApi.resumePosition(0L, 150_000L))
        assertEquals(0L, JellyfinApi.resumePosition(-1L, 150_000L))
        assertEquals(150_000L, JellyfinApi.resumePosition(null, 150_000L))
        assertEquals(0L, JellyfinApi.resumePosition(null, 20_000L))
    }

    @Test fun paused_reports_preserve_pause_and_position() {
        val body = org.json.JSONObject(JellyfinApi.playbackBody("item", "source", 20_000L, true, "session"))
        assertTrue(body.getBoolean("IsPaused"))
        assertEquals(200_000_000L, body.getLong("PositionTicks"))
        assertEquals("session", body.getString("PlaySessionId"))
    }
}
