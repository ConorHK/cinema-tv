package com.danielportelabyrne.cinema.data.music.cast

import org.junit.Assert.*
import org.junit.Test

class RendererTest {
    private class Fake : CastControl {
        val calls = mutableListOf<String>()
        var state = CastSnapshot(Transport.NO_MEDIA, null, null, 0, 0, 40, false)
        override fun snapshot() = state
        override fun load(item: CastItem) { calls += "load ${item.uri}"; state = state.copy(transport = Transport.STOPPED, current = item) }
        override fun loadNext(item: CastItem?) { calls += "next ${item?.uri}" }
        override fun play() { calls += "play" }
        override fun pause() { calls += "pause" }
        override fun stop() { calls += "stop" }
        override fun seek(positionMs: Long) { calls += "seek $positionMs" }
        override fun next() { calls += "skip" }
        override fun previous() { calls += "back" }
        override fun setVolume(percent: Int) { calls += "volume $percent" }
        override fun setMuted(muted: Boolean) { calls += "mute $muted" }
    }

    private val fake = Fake()
    private val renderer = Renderer(fake)
    private fun av(name: String, vararg args: Pair<String, String>) = renderer.handle(Upnp.AV_TRANSPORT, SoapAction(name, mapOf(*args)))

    @Test fun play_needs_media_first() {
        val r = av("Play", "Speed" to "1")
        assertEquals(500, r.status)
        assertTrue(r.body.contains("<errorCode>701</errorCode>"))
        assertEquals(200, av("SetAVTransportURI", "CurrentURI" to "http://s/1", "CurrentURIMetaData" to "").status)
        assertEquals(200, av("Play", "Speed" to "1").status)
        assertEquals(listOf("load http://s/1", "play"), fake.calls)
    }

    @Test fun seeks_by_time_only() {
        av("Seek", "Unit" to "REL_TIME", "Target" to "0:01:30")
        assertTrue(av("Seek", "Unit" to "TRACK_NR", "Target" to "2").body.contains("710"))
        assertTrue(av("Seek", "Unit" to "REL_TIME", "Target" to "soon").body.contains("711"))
        assertEquals(listOf("seek 90000"), fake.calls)
    }

    @Test fun an_empty_next_clears_it() {
        av("SetNextAVTransportURI", "NextURI" to "", "NextURIMetaData" to "")
        assertEquals(listOf("next null"), fake.calls)
    }

    @Test fun reports_position() {
        fake.state = CastSnapshot(Transport.PLAYING, Upnp.item("http://s/1", ""), null, 61_000, 200_000, 40, false)
        val body = av("GetPositionInfo").body
        assertTrue(body.contains("<RelTime>0:01:01</RelTime>"))
        assertTrue(body.contains("<TrackDuration>0:03:20</TrackDuration>"))
        assertTrue(av("GetTransportInfo").body.contains("<CurrentTransportState>PLAYING</CurrentTransportState>"))
    }

    @Test fun volume_and_mute() {
        renderer.handle(Upnp.RENDERING_CONTROL, SoapAction("SetVolume", mapOf("Channel" to "Master", "DesiredVolume" to "150")))
        renderer.handle(Upnp.RENDERING_CONTROL, SoapAction("SetMute", mapOf("Channel" to "Master", "DesiredMute" to "true")))
        assertEquals(listOf("volume 100", "mute true"), fake.calls)
        assertTrue(renderer.handle(Upnp.RENDERING_CONTROL, SoapAction("GetVolume", mapOf())).body.contains("<CurrentVolume>40</CurrentVolume>"))
    }

    @Test fun unknown_actions_fail() {
        assertTrue(av("Record").body.contains("<errorCode>401</errorCode>"))
    }

    @Test fun events_carry_escaped_last_change() {
        fake.state = fake.state.copy(transport = Transport.PAUSED)
        val body = Upnp.propertySet(renderer.state(Upnp.AV_TRANSPORT))
        assertTrue(body.contains("&lt;TransportState val=&quot;PAUSED_PLAYBACK&quot;/&gt;"))
    }
}
