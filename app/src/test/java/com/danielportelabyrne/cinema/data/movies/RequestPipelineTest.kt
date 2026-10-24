package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class RequestPipelineTest {
    @Test fun reads_real_search_status_and_retry_schedule_without_inventing_progress() {
        val result = RequestPipelineApi.parse("""{"healthy":true,"requests":{"7":{"state":"no_release","message":"No acceptable release found; retry scheduled","lastSearchAt":100.5,"nextSearchAt":1000}}}""")!!
        val request = result.requests[7]!!
        assertTrue(result.healthy); assertTrue(request.canRetry)
        assertEquals(100500L, request.lastSearchAtMs); assertEquals(1000000L, request.nextSearchAtMs)
    }

    @Test fun active_or_stopped_searches_cannot_be_repeated_from_retry_controls() {
        assertFalse(RequestDiagnostic("searching", "Searching", null, null).canRetry)
        val stopped = RequestDiagnostic("cancelled", "Stopped", null, null)
        assertTrue(stopped.stopped); assertFalse(stopped.canRetry)
        assertNull(RequestPipelineApi.parse("bad response"))
    }

    @Test fun a_download_for_one_season_does_not_hide_other_season_requests() {
        val request = SeerrRequest(1, Kind.SHOW, 605, Stage.SEARCHING, emptyList(), null, listOf(7))
        val queue = listOf(QueueItem(QueueSource.SONARR, 1, "Sabrina", null, 605, "downloading", 100, 50, null, null, season = 5))
        assertFalse(RequestPipelineApi.covered(request, queue))
        assertTrue(RequestPipelineApi.covered(request.copy(seasons = listOf(5)), queue))
        assertFalse(RequestPipelineApi.covered(request.copy(seasons = listOf(5, 7)), queue))
    }
}
