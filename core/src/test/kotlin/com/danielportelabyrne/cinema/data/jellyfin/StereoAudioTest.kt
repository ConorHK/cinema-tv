package com.danielportelabyrne.cinema.data.jellyfin

import org.junit.Assert.*
import org.junit.Test

class StereoAudioTest {
    @Test fun unsupported_soundtrack_is_converted_without_reencoding_the_picture() {
        val url = JellyfinApi.stereoStreamUrl("http://deck:8096", "episode", "source", "upstairs")
        assertTrue(url.contains("VideoCodec=copy"))
        assertTrue(url.contains("AudioCodec=aac"))
        assertTrue(url.contains("AllowAudioStreamCopy=false"))
        assertTrue(url.contains("TranscodingMaxAudioChannels=2"))
        assertTrue(url.contains("MaxAudioChannels=2"))
        assertFalse(url.contains("api_key", true) || url.contains("token", true))
    }

    @Test fun full_conversion_fallback_also_returns_stereo_audio() {
        val item = JellyfinItem("episode", "Episode", null, "Episode", "source")
        val url = JellyfinApi.transcodeUrl("http://deck:8096", item, "upstairs")
        assertTrue(url.contains("VideoCodec=h264"))
        assertTrue(url.contains("TranscodingMaxAudioChannels=2"))
        assertTrue(url.contains("MaxAudioChannels=2"))
    }
}
