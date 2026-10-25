package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StaleRequestTest {
    private fun media() = JSONObject("""{"id":34,"status":3,"status4k":1,"requests":[],"downloadStatus":[],"seasons":[{"seasonNumber":1,"status":3}]}""")
    private fun state(info: JSONObject) = SeerrApi.titleState(JSONObject().put("mediaInfo", info).toString())

    @Test fun imported_monitoring_with_no_request_can_offer_a_repair() {
        val state = state(media())
        assertEquals(Availability.PROCESSING, state.availability)
        assertTrue(state.staleCandidate)
    }

    @Test fun real_requests_downloads_available_files_and_4k_status_do_not_offer_a_repair() {
        assertFalse(state(media().put("requests", JSONArray().put(JSONObject().put("id", 7)))).staleCandidate)
        assertFalse(state(media().put("downloadStatus", JSONArray().put(JSONObject()))).staleCandidate)
        assertFalse(state(media().put("status", 4)).staleCandidate)
        assertFalse(state(media().put("status", 5)).staleCandidate)
        assertFalse(state(media().put("status4k", 3)).staleCandidate)
        assertFalse(state(media().put("seasons", JSONArray().put(JSONObject().put("status", 5)))).staleCandidate)
    }

    @Test fun missing_or_malformed_request_metadata_does_not_offer_a_repair() {
        assertFalse(state(media().apply { remove("requests") }).staleCandidate)
        assertFalse(SeerrApi.titleState("{}").staleCandidate)
        assertFalse(SeerrApi.titleState("broken").staleCandidate)
    }
}
