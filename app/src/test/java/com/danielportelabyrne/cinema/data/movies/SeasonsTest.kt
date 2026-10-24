package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SeasonsTest {
    private val tv = """{"seasons":[
        {"seasonNumber":0,"name":"Specials","episodeCount":9},
        {"seasonNumber":1,"name":"Season 1","episodeCount":7},
        {"seasonNumber":2,"name":"","episodeCount":13}],
      "mediaInfo":{"status":4,"seasons":[{"seasonNumber":1,"status":5},{"seasonNumber":2,"status":3}]}}"""

    @Test fun lists_seasons_without_specials_and_with_their_state() {
        val s = SeerrSeasons.parseSeasons(tv)
        assertEquals(listOf(1, 2), s.map { it.number }); assertEquals(listOf(7, 13), s.map { it.episodeCount })
        assertEquals(Availability.AVAILABLE, s[0].availability); assertEquals(Availability.PROCESSING, s[1].availability)
        assertEquals("Season 2", s[1].name)
    }

    @Test fun seasons_nobody_has_asked_for_are_none() {
        val s = SeerrSeasons.parseSeasons("""{"seasons":[{"seasonNumber":1,"episodeCount":3}]}""")
        assertEquals(Availability.NONE, s.single().availability)
        assertTrue(SeerrSeasons.parseSeasons("junk").isEmpty())
    }

    @Test fun reads_episodes() {
        val e = SeerrSeasons.parseEpisodes("""{"episodes":[{"episodeNumber":1,"seasonNumber":1,"name":"Pilot","overview":"Walt.","airDate":"2008-01-20"},{"episodeNumber":2,"seasonNumber":1,"name":""}]}""")
        assertEquals(listOf("Pilot", "Episode 2"), e.map { it.name }); assertEquals("2008-01-20", e[0].airDate); assertNull(e[1].overview)
    }

    @Test fun reads_episode_stills_and_runtimes() {
        val e = SeerrSeasons.parseEpisodes("""{"episodes":[
            {"episodeNumber":1,"seasonNumber":1,"name":"Stick or Twist","stillPath":"/abc.jpg","runtime":46},
            {"episodeNumber":2,"seasonNumber":1,"name":"Jigsaw Puzzle","stillPath":null,"runtime":0},
            {"episodeNumber":3,"seasonNumber":1,"name":"Ha'penny Bridge"}]}""")
        assertEquals("/abc.jpg", e[0].stillPath); assertEquals("https://image.tmdb.org/t/p/w500/abc.jpg", e[0].stillUrl); assertEquals(46, e[0].runtime)
        assertNull(e[1].stillPath); assertNull(e[1].stillUrl); assertNull(e[1].runtime)
        assertNull(e[2].stillPath); assertNull(e[2].runtime)
    }

    @Test fun library_episodes_say_whether_they_have_a_still() {
        val items = JellyfinApi.parseItems("""{"Items":[
            {"Id":"a","Type":"Episode","ImageTags":{"Primary":"t1"},"RunTimeTicks":31200000000},
            {"Id":"b","Type":"Episode","ImageTags":{}}]}""")
        assertTrue(items[0].hasPrimaryImage); assertEquals(52, items[0].runtimeMin); assertFalse(items[1].hasPrimaryImage)
        assertEquals("http://d/Items/a/Images/Primary?maxWidth=480&quality=85", JellyfinApi.episodeImageUrl("http://d", "a"))
        assertTrue(JellyfinApi.episodesUrl("http://d", "u", "s").contains("RunTimeTicks"))
    }

    @Test fun a_request_names_its_seasons_or_asks_for_all() {
        val one = JSONObject(SeerrApi.requestBody(Kind.SHOW, 1396, listOf(2)))
        assertEquals(2, one.getJSONArray("seasons").getInt(0)); assertEquals(1, one.getJSONArray("seasons").length())
        assertEquals("all", JSONObject(SeerrApi.requestBody(Kind.SHOW, 1396)).getString("seasons"))
        assertFalse(JSONObject(SeerrApi.requestBody(Kind.MOVIE, 5, listOf(1))).has("seasons"))
    }

    @Test fun reads_full_seerr_image_urls_for_undownloaded_episodes() {
        val episodes = SeerrSeasons.parseEpisodes("""{"episodes":[
            {"episodeNumber":1,"seasonNumber":2,"stillPath":"https://image.tmdb.org/t/p/original//mAPNGxRqYmPeLzO0qJYVpIckNZf.jpg"},
            {"episodeNumber":2,"seasonNumber":2,"stillPath":"https://image.tmdb.org/t/p/w500/second.jpg"}]}""")
        assertEquals("https://image.tmdb.org/t/p/w500/mAPNGxRqYmPeLzO0qJYVpIckNZf.jpg", episodes[0].stillUrl)
        assertEquals("https://image.tmdb.org/t/p/w500/second.jpg", episodes[1].stillUrl)
        assertNull(EpisodeImages.path("null"))
        assertNull(EpisodeImages.path("https://example.org/image.jpg"))
        assertNull(EpisodeImages.path("//example.org/image.jpg"))
    }

    @Test fun playback_reports_use_ticks_and_the_right_addresses() {
        val b = JSONObject(JellyfinApi.playbackBody("item1", "src1", 90_000L, paused = true, sessionId = "sess"))
        assertEquals(900_000_000L, b.getLong("PositionTicks")); assertEquals("item1", b.getString("ItemId")); assertEquals("src1", b.getString("MediaSourceId"))
        assertTrue(b.getBoolean("IsPaused")); assertEquals("sess", b.getString("PlaySessionId"))
        assertEquals("http://d/Sessions/Playing", JellyfinApi.reportUrl("http://d", "Playing"))
        assertEquals("http://d/Sessions/Playing/Progress", JellyfinApi.reportUrl("http://d", "Progress"))
        assertEquals("http://d/Sessions/Playing/Stopped", JellyfinApi.reportUrl("http://d", "Stopped"))
        assertEquals(0L, JSONObject(JellyfinApi.playbackBody("i", "s", -5, false, "x")).getLong("PositionTicks"))
    }

    @Test fun episodes_url_names_the_series() {
        assertTrue(JellyfinApi.episodesUrl("http://d", "u", "ser1").contains("/Shows/ser1/Episodes?userId=u"))
    }
}
