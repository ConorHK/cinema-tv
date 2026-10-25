package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SonarrTest {
    @Test fun reads_monitored_episodes_with_no_file_as_wanted_and_groups_them_by_series() {
        val json = """{"records":[
            {"id":21,"seasonNumber":2,"episodeNumber":4,"series":{"title":"The Pitt","tmdbId":2,"id":7}},
            {"id":22,"seasonNumber":2,"episodeNumber":5,"series":{"title":"The Pitt","tmdbId":2,"id":7}},
            {"id":30,"seasonNumber":1,"episodeNumber":1,"series":{"title":"No id"}}]}"""
        val wanted = SonarrApi.wantedEpisodes(json)
        assertEquals(listOf(21, 22), wanted.map { it.id })   // the one without a TMDB id is dropped, not shown as a broken row
        assertEquals(WantedEpisode(21, 2L, "The Pitt", 2, 4), wanted.first())
        assertEquals("""{"episodeIds":[21,22],"monitored":false}""", SonarrApi.monitorBody(listOf(21, 22), on = false))
        assertTrue(SonarrApi.wantedEpisodes("junk").isEmpty())
    }

    // the shape Seerr returned for this server: Sonarr is "localhost" as far as Seerr is concerned
    private val settings = """[{"apiKey":"abc123","baseUrl":"","port":8989,"is4k":false,"isDefault":true,"hostname":"localhost","useSsl":false,
        "activeProfileId":4,"activeDirectory":"/media/Shows","enableSeasonFolders":true,"seriesType":"standard","id":0}]"""

    @Test fun reads_sonarr_from_seerrs_settings_using_the_machine_seerr_is_on() {
        val c = SonarrApi.parseConfig(settings, "192.168.1.20")!!
        assertEquals("http://192.168.1.20:8989", c.baseUrl); assertEquals("abc123", c.apiKey)
        assertEquals(4, c.profileId); assertEquals("/media/Shows", c.rootFolder); assertTrue(c.seasonFolders)
    }

    @Test fun keeps_a_real_hostname_and_skips_the_4k_server() {
        val two = """[{"apiKey":"k4","port":8990,"is4k":true,"hostname":"x","isDefault":true},
                      {"apiKey":"k","port":8989,"is4k":false,"hostname":"sonarr.lan","useSsl":true,"baseUrl":"/tv","activeProfileId":1,"activeDirectory":"/s"}]"""
        val c = SonarrApi.parseConfig(two, "h")!!
        assertEquals("https://sonarr.lan:8989/tv", c.baseUrl); assertEquals("k", c.apiKey)
        assertNull(SonarrApi.parseConfig("[]", "h")); assertNull(SonarrApi.parseConfig("junk", "h"))
    }

    @Test fun finds_a_series_by_tmdb_id() {
        val list = """[{"id":3,"title":"A","tmdbId":10},{"id":7,"title":"B","tmdbId":1396}]"""
        assertEquals(7, SonarrApi.findSeries(list, 1396)); assertNull(SonarrApi.findSeries(list, 99)); assertNull(SonarrApi.findSeries("junk", 1))
    }

    @Test fun adding_a_series_wants_nothing_until_episodes_are_chosen() {
        val config = SonarrApi.parseConfig(settings, "h")!!
        val body = JSONObject(SonarrApi.addBody(JSONObject("""{"title":"Breaking Bad","tvdbId":81189,"seasons":[{"seasonNumber":1}]}"""), config))
        assertEquals("Breaking Bad", body.getString("title")); assertEquals(81189, body.getInt("tvdbId"))
        assertEquals(4, body.getInt("qualityProfileId")); assertEquals("/media/Shows", body.getString("rootFolderPath"))
        val add = body.getJSONObject("addOptions")
        assertEquals("none", add.getString("monitor")); assertFalse(add.getBoolean("searchForMissingEpisodes"))
    }

    @Test fun picks_episode_ids_for_the_asked_numbers_of_one_season() {
        val eps = """[{"id":11,"seasonNumber":1,"episodeNumber":1},{"id":12,"seasonNumber":1,"episodeNumber":2},
                      {"id":13,"seasonNumber":1,"episodeNumber":3},{"id":21,"seasonNumber":2,"episodeNumber":1}]"""
        assertEquals(listOf(12, 13), SonarrApi.episodeIds(eps, 1, listOf(2, 3, 9)))
        assertTrue(SonarrApi.episodeIds(eps, 5, listOf(1)).isEmpty()); assertTrue(SonarrApi.episodeIds("junk", 1, listOf(1)).isEmpty())
    }

    @Test fun reads_what_is_on_disk_and_what_is_wanted() {
        val s = SonarrApi.episodeStates("""[{"seasonNumber":1,"episodeNumber":1,"hasFile":true,"monitored":true},
            {"seasonNumber":1,"episodeNumber":2,"hasFile":false,"monitored":true},{"seasonNumber":1,"episodeNumber":3,"hasFile":false,"monitored":false}]""")
        assertEquals(EpisodeState.DOWNLOADED, s[1 to 1]); assertEquals(EpisodeState.REQUESTED, s[1 to 2]); assertEquals(EpisodeState.NONE, s[1 to 3])
    }

    @Test fun monitor_and_search_bodies() {
        val m = JSONObject(SonarrApi.monitorBody(listOf(5, 6)))
        assertEquals(2, m.getJSONArray("episodeIds").length()); assertTrue(m.getBoolean("monitored"))
        val c = JSONObject(SonarrApi.searchBody(listOf(5)))
        assertEquals("EpisodeSearch", c.getString("name")); assertEquals(5, c.getJSONArray("episodeIds").getInt(0))
    }

    private val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse("2026-10-22T12:00:00")!!.time

    @Test fun episodes_not_yet_aired_are_monitored_but_never_searched_for() {
        val eps = """[{"id":11,"seasonNumber":2,"episodeNumber":4,"airDateUtc":"2026-10-15T02:00:00Z"},
                      {"id":12,"seasonNumber":2,"episodeNumber":5,"airDateUtc":"2026-12-21T02:00:00Z"},
                      {"id":13,"seasonNumber":2,"episodeNumber":6}]"""
        val plan = SonarrApi.requestPlan(eps, 2, listOf(4, 5, 6), now)
        assertEquals(listOf(11, 12, 13), plan.monitor)
        assertEquals(listOf(11), plan.search)                                        // only the one that is out
        assertTrue(SonarrApi.requestPlan(eps, 2, listOf(5, 6), now).search.isEmpty())   // nothing to search: no command is sent
    }

    @Test fun reads_episodes_asked_for_ahead_from_the_calendar() {
        val cal = """[{"id":40,"seasonNumber":2,"episodeNumber":5,"airDate":"2026-12-21","airDateUtc":"2026-12-21T02:00:00Z","monitored":true,"hasFile":false,"series":{"title":"The Pitt","tmdbId":2}},
                      {"id":41,"seasonNumber":2,"episodeNumber":6,"airDate":"2026-12-28","airDateUtc":"2026-12-28T02:00:00Z","monitored":false,"hasFile":false,"series":{"title":"The Pitt","tmdbId":2}},
                      {"id":42,"seasonNumber":2,"episodeNumber":3,"airDate":"2026-10-20","airDateUtc":"2026-10-20T02:00:00Z","monitored":true,"hasFile":false,"series":{"title":"The Pitt","tmdbId":2}}]"""
        assertEquals(listOf(WantedEpisode(40, 2L, "The Pitt", 2, 5, "2026-12-21")), SonarrApi.upcomingEpisodes(cal, now))
        assertTrue(SonarrApi.upcomingEpisodes("junk", now).isEmpty())
    }
}
