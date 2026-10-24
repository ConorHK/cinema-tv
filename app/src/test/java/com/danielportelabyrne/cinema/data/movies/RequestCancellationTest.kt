package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RequestCancellationTest {
    @Test fun searching_and_unapproved_requests_can_be_cancelled_but_completed_titles_cannot() {
        fun request(stage: Stage) = SeerrRequest(1, Kind.MOVIE, 7, stage, emptyList(), null)
        assertTrue(request(Stage.SEARCHING).canCancel)
        assertTrue(request(Stage.WAITING_APPROVAL).canCancel)
        assertFalse(request(Stage.READY).canCancel)
        assertFalse(request(Stage.DECLINED).canCancel)
    }

    @Test fun cancelling_one_season_preserves_other_seasons_and_files() {
        val json = """{"id":1,"monitored":true,"seasons":[{"seasonNumber":1,"monitored":true},{"seasonNumber":2,"monitored":true}],"path":"/media/Shows/Example"}"""
        val result = JSONObject(RequestCancellationApi.stopSeasons(json, listOf(2))!!)
        val seasons = result.getJSONArray("seasons")
        assertTrue(seasons.getJSONObject(0).getBoolean("monitored"))
        assertFalse(seasons.getJSONObject(1).getBoolean("monitored"))
        assertTrue(result.getBoolean("monitored"))
        assertEquals("/media/Shows/Example", result.getString("path"))
        assertEquals(listOf(12), RequestCancellationApi.episodeIds("""[{"id":11,"seasonNumber":1},{"id":12,"seasonNumber":2}]""", listOf(2)))
    }

    @Test fun missing_season_information_never_cancels_an_entire_series() {
        assertNull(RequestCancellationApi.stopSeasons("{}", emptyList()))
        assertNull(RequestCancellationApi.episodeIds("[]", emptyList()))
        assertNull(RequestCancellationApi.episodeIds("junk", listOf(1)))
    }

    @Test fun reads_only_the_seasons_belonging_to_the_request() {
        val json = """{"results":[{"id":1,"type":"tv","status":2,"seasons":[{"seasonNumber":2},{"seasonNumber":2}],"media":{"tmdbId":7,"status":3,"seasons":[{"seasonNumber":1}]}}]}"""
        assertEquals(listOf(2), SeerrRequests.parse(json).single().seasons)
    }
}
