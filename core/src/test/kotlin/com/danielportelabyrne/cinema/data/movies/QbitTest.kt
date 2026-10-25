package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QbitTest {
    private val hash = "a".repeat(40)
    private val config = QbitConfig("http://deck:8090", "example", "test password")
    private fun torrent(forced: Boolean = false) = """[{"hash":"$hash","state":"queuedDL","priority":9,"force_start":$forced,"progress":0.1}]"""
    private data class Call(val method: String, val path: String, val body: String?, val cookie: String?)
    private class Fake(private val respond: (Call) -> QbitReply) : QbitTransport {
        val calls = mutableListOf<Call>()
        override fun call(method: String, path: String, body: String?, cookie: String?): QbitReply =
            Call(method, path, body, cookie).let { calls += it; respond(it) }
    }

    @Test fun next_changes_only_the_selected_torrent_and_resumes_it() {
        val http = Fake { if (it.method == "GET") QbitReply(200, torrent()) else QbitReply(200) }
        assertEquals(DownloadActionResult.DONE, QbitClient(config, http).perform(hash.uppercase(), DownloadAction.NEXT))
        assertEquals(listOf("/api/v2/torrents/info?hashes=$hash", "/api/v2/torrents/topPrio", "/api/v2/torrents/start"), http.calls.map { it.path })
        assertTrue(http.calls.filter { it.method == "POST" }.all { it.body == "hashes=$hash" })
    }

    @Test fun now_bypasses_the_queue_without_pausing_any_other_download() {
        val http = Fake { if (it.method == "GET") QbitReply(200, torrent()) else QbitReply(200) }
        assertEquals(DownloadActionResult.DONE, QbitClient(config, http).perform(hash, DownloadAction.NOW))
        assertEquals(listOf("GET", "POST"), http.calls.map { it.method })
        assertEquals("/api/v2/torrents/setForceStart", http.calls.last().path)
        assertEquals("hashes=$hash&value=true", http.calls.last().body)
    }

    @Test fun queue_disabled_does_not_claim_success_or_force_anything() {
        val http = Fake { if (it.method == "GET") QbitReply(200, torrent()) else QbitReply(409) }
        assertEquals(DownloadActionResult.QUEUE_DISABLED, QbitClient(config, http).perform(hash, DownloadAction.NEXT))
        assertEquals(2, http.calls.size)
    }

    @Test fun rejects_all_multiple_and_missing_torrent_selectors() {
        val http = Fake { QbitReply(200, "[]") }
        val client = QbitClient(config, http)
        listOf("all", "$hash|$hash", "", "not-a-hash").forEach {
            assertEquals(DownloadActionResult.NOT_FOUND, client.perform(it, DownloadAction.NOW))
        }
        assertTrue(http.calls.isEmpty())
        assertEquals(DownloadActionResult.NOT_FOUND, client.perform(hash, DownloadAction.NOW))
        assertEquals(1, http.calls.size)
    }

    @Test fun authenticates_once_using_a_private_cookie_and_replays_the_read() {
        val http = Fake { call ->
            when {
                call.path.endsWith("auth/login") -> QbitReply(200, "Ok.", listOf("SID=test-session; HttpOnly; Path=/"))
                call.cookie == null -> QbitReply(403)
                else -> QbitReply(200, torrent())
            }
        }
        val client = QbitClient(config, http)
        assertNotNull(client.states()); assertNotNull(client.states())
        assertEquals(1, http.calls.count { it.path.endsWith("auth/login") })
        assertEquals("username=example&password=test+password", http.calls[1].body)
        assertEquals("SID=test-session", http.calls.last().cookie)
    }

    @Test fun an_invalid_login_never_sends_a_priority_command() {
        val http = Fake { if (it.path.endsWith("auth/login")) QbitReply(200, "Fails.") else QbitReply(403) }
        assertEquals(DownloadActionResult.NEEDS_SIGN_IN, QbitClient(config, http).perform(hash, DownloadAction.NOW))
        assertEquals(2, http.calls.size)
        QbitClient(config, http) // sessions are isolated; no global authenticator or cookie handler
        val client = QbitClient(config, http)
        client.states(); client.states()
        assertEquals(2, http.calls.count { it.path.endsWith("auth/login") }) // one for each attempted session
        assertFalse(http.calls.any { it.path.endsWith("setForceStart") })
    }

    @Test fun returns_forced_torrent_to_normal_queue_when_choosing_next() {
        val http = Fake { if (it.method == "GET") QbitReply(200, torrent(true)) else QbitReply(200) }
        assertEquals(DownloadActionResult.DONE, QbitClient(config, http).perform(hash, DownloadAction.NEXT))
        assertEquals("hashes=$hash&value=false", http.calls.single { it.path.endsWith("setForceStart") }.body)
    }

    @Test fun supports_the_older_resume_endpoint_without_retrying_other_errors() {
        val http = Fake { when {
            it.method == "GET" -> QbitReply(200, torrent())
            it.path.endsWith("/start") -> QbitReply(404)
            else -> QbitReply(200)
        } }
        assertEquals(DownloadActionResult.DONE, QbitClient(config, http).perform(hash, DownloadAction.NEXT))
        assertTrue(http.calls.last().path.endsWith("/resume"))
    }

    @Test fun uses_real_queue_positions_and_distinguishes_sources_from_slots() {
        val item = QueueItem(QueueSource.SONARR, 1, "Series", null, 7, "queued", 100, 90, null, null)
        val waiting = QbitApi.apply(item, TorrentState(hash, "queuedDL", 5, false, 0.1))
        assertEquals(5, waiting.position); assertTrue(waiting.canPrioritize)
        val stalled = QbitApi.apply(item, TorrentState(hash, "stalledDL", 5, true, 0.1))
        assertEquals("stalled", stalled.status); assertEquals(0, stalled.position); assertTrue(stalled.forced)
        val complete = QbitApi.apply(item.copy(status = "warning"), TorrentState(hash, "uploading", -1, false, 1.0))
        assertFalse(complete.canPrioritize); assertEquals("warning", complete.status)
    }

    @Test fun matches_the_named_download_client_and_maps_loopback_to_the_server() {
        val json = """[{"name":"Other","implementation":"SABnzbd","enable":true,"fields":[]},
            {"name":"qBittorrent","implementation":"QBittorrent","enable":true,"fields":[
            {"name":"host","value":"localhost"},{"name":"port","value":8090},{"name":"urlBase","value":null},
            {"name":"username","value":"example"},{"name":"password","value":"test"}]}]""".replace("\n+", "\n")
        assertEquals("http://deck:8090", QbitApi.config(json, "qBittorrent", "deck")!!.baseUrl)
        assertNull(QbitApi.config(json, "Other", "deck"))
        assertNull(QbitApi.config("junk", null, "deck"))
    }

    @Test fun supports_qbittorrent_52_cookie_name_and_204_action_responses() {
        val http = Fake { call -> when {
            call.path.endsWith("auth/login") -> QbitReply(204, cookies = listOf("QBT_SID_8090=new-session; HttpOnly; Path=/"))
            call.cookie == null -> QbitReply(403)
            call.method == "GET" -> QbitReply(200, torrent())
            else -> QbitReply(204)
        } }
        val client = QbitClient(config, http)
        assertEquals(DownloadActionResult.DONE, client.perform(hash, DownloadAction.NOW))
        assertEquals("QBT_SID_8090=new-session", http.calls.last().cookie)
        assertEquals(1, http.calls.count { it.path.endsWith("auth/login") })
    }

    @Test fun a_204_without_a_real_session_cookie_is_not_a_successful_signin() {
        assertNull(QbitApi.loginCookie(204, "", emptyList()))
        assertNull(QbitApi.loginCookie(204, "", listOf("QBT_SID_8090=; Path=/")))
        assertNull(QbitApi.loginCookie(401, "", listOf("SID=test")))
        assertNull(QbitApi.loginCookie(200, "Fails.", listOf("SID=test")))
        assertEquals("SID=legacy", QbitApi.loginCookie(200, "Ok.", listOf("SID=legacy; Path=/")))
    }

    @Test fun next_accepts_204_for_move_and_start_without_forcing_or_pausing_others() {
        val http = Fake { if (it.method == "GET") QbitReply(200, torrent()) else QbitReply(204) }
        assertEquals(DownloadActionResult.DONE, QbitClient(config, http).perform(hash, DownloadAction.NEXT))
        assertFalse(http.calls.any { it.path.contains("pause") || it.path.contains("setForceStart") })
        assertTrue(http.calls.filter { it.method == "POST" }.all { it.body == "hashes=$hash" })
    }

    @Test fun real_torrent_size_progress_and_peers_replace_unknown_arr_values() {
        val torrent = QbitApi.torrents("""[{"hash":"$hash","state":"downloading","priority":1,"progress":0.75,"size":1000,"amount_left":250,"dlspeed":1200000,"num_seeds":9,"eta":8640000}]""").getValue(hash)
        val item = QueueItem(QueueSource.SONARR, 1, "Series", null, 7, "queued", 0, 0, null, null)
        val actual = QbitApi.apply(item, torrent)
        assertEquals(0.75f, actual.progress()!!, 0.001f)
        assertEquals(1200000L, actual.speedBytes); assertEquals(9, actual.seeds)
        assertNull(torrent.etaSeconds); assertTrue(actual.clientVerified)
    }
}
