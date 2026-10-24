package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class DownloadClientsTest {
    private val hash = "b".repeat(40)

    private fun client(name: String, implementation: String, protocol: String, vararg fields: Pair<String, Any?>) =
        """{"name":"$name","implementation":"$implementation","protocol":"$protocol","enable":true,"fields":[""" +
            fields.joinToString(",") { (k, v) -> """{"name":"$k","value":${if (v == null) "null" else if (v is String) "\"$v\"" else v}}""" } + "]}"

    private val arrList = "[" + listOf(
        client("SAB", "Sabnzbd", "usenet", "host" to "nas"),
        client("Trans", "Transmission", "torrent", "host" to "localhost", "port" to 9091, "urlBase" to "/transmission/", "username" to "me", "password" to "********"),
        client("Del", "Deluge", "torrent", "host" to "seedbox", "port" to 8112, "urlBase" to null, "password" to "deluge"),
        client("rT", "RTorrent", "torrent", "host" to "nas")
    ).joinToString(",") + "]"

    @Test fun picks_each_named_client_with_the_right_kind_and_address() {
        val t = DownloadClients.config(arrList, "Trans", "server")!!
        assertEquals(ClientKind.TRANSMISSION, t.kind)
        assertEquals("http://server:9091/transmission", t.baseUrl)
        assertTrue(t.passwordHidden); assertEquals("", t.password); assertEquals("me", t.username)

        val d = DownloadClients.config(arrList, "Del", "server")!!
        assertEquals(ClientKind.DELUGE, d.kind)
        assertEquals("http://seedbox:8112", d.baseUrl)
        assertFalse(d.passwordHidden); assertEquals("deluge", d.password)
    }

    @Test fun unsupported_or_unnamed_ambiguous_clients_get_no_controls() {
        assertNull(DownloadClients.config(arrList, "rT", "server"))
        assertNull(DownloadClients.config(arrList, "SAB", "server"))
        // Two supported clients and no name on the row: never guess which one to command.
        assertNull(DownloadClients.config(arrList, null, "server"))
        assertNull(DownloadClients.config("junk", "Trans", "server"))
    }

    @Test fun transmission_defaults_to_its_standard_url_base() {
        val json = "[" + client("T", "Transmission", "torrent", "host" to "nas") + "]"
        assertEquals("http://nas:9091/transmission", DownloadClients.config(json, null, "server")!!.baseUrl)
    }

    @Test fun lists_torrent_clients_only_and_says_which_are_supported() {
        val listed = DownloadClients.list(arrList, "server")
        assertEquals(listOf("Trans", "Del", "rT"), listed.map { it.name })
        assertEquals(listOf(ClientKind.TRANSMISSION, ClientKind.DELUGE, null), listed.map { it.kind })
        assertEquals("server", listed[0].host)
    }

    @Test fun a_saved_login_is_used_only_when_the_password_is_hidden_or_blank() {
        val hidden = ClientConfig(ClientKind.TRANSMISSION, "T", "http://nas:9091/transmission", "me", "", passwordHidden = true)
        assertEquals("saved", DownloadClients.create(hidden, "me", "saved", null) {}.config.password)
        val known = hidden.copy(password = "known", passwordHidden = false)
        assertEquals("known", DownloadClients.create(known, "me", "saved", null) {}.config.password)
    }

    // --- Transmission ------------------------------------------------------------------------------

    private class Fake(private val respond: (String, Map<String, String>) -> HttpReply) : HttpTransport {
        val bodies = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        override fun call(method: String, path: String, body: String?, headers: Map<String, String>): HttpReply {
            bodies += body.orEmpty(); this.headers += headers
            return respond(body.orEmpty(), headers)
        }
    }

    private fun transmissionTorrent(status: Int, done: Double = 0.2, queue: Int = 2) =
        """{"result":"success","arguments":{"torrents":[{"hashString":"$hash","status":$status,"queuePosition":$queue,"percentDone":$done,
            "rateDownload":0,"peersSendingToUs":0,"totalSize":1000,"leftUntilDone":800,"eta":-1,"error":0,"metadataPercentComplete":1}]}}"""

    @Test fun transmission_fetches_a_session_id_once_then_sends_it() {
        val http = Fake { _, h ->
            if (h["X-Transmission-Session-Id"] == null) HttpReply(409, headers = mapOf("X-Transmission-Session-Id" to listOf("abc")))
            else HttpReply(200, transmissionTorrent(3))
        }
        val c = TransmissionClient(ClientConfig(ClientKind.TRANSMISSION, "T", "http://nas:9091/transmission", "me", "pw"), http)
        val states = c.states()!!
        assertEquals(2, http.bodies.size)
        assertEquals("abc", http.headers[1]["X-Transmission-Session-Id"])
        assertEquals("Basic bWU6cHc=", http.headers[1]["Authorization"])
        assertEquals("queuedDL", states[hash]!!.state)
        assertEquals(3, states[hash]!!.priority)   // queue position 2, counted from 1
        c.states()
        assertEquals(3, http.bodies.size)           // the session id is reused
    }

    @Test fun transmission_next_moves_to_top_and_starts_now_starts_now() {
        val http = Fake { body, _ -> HttpReply(200, if (body.contains("torrent-get")) transmissionTorrent(3) else """{"result":"success","arguments":{}}""") }
        val c = TransmissionClient(ClientConfig(ClientKind.TRANSMISSION, "T", "x", "", ""), http)
        assertEquals(DownloadActionResult.DONE, c.perform(hash, DownloadAction.NEXT))
        assertTrue(http.bodies.any { it.contains("queue-move-top") && it.contains(hash) })
        assertTrue(http.bodies.any { it.contains("\"torrent-start\"") })
        assertEquals(DownloadActionResult.DONE, c.perform(hash, DownloadAction.NOW))
        assertTrue(http.bodies.any { it.contains("torrent-start-now") })
    }

    @Test fun transmission_without_the_right_login_asks_for_one() {
        val http = Fake { _, _ -> HttpReply(401) }
        assertEquals(DownloadActionResult.NEEDS_SIGN_IN, TransmissionClient(ClientConfig(ClientKind.TRANSMISSION, "T", "x", "", ""), http).perform(hash, DownloadAction.NEXT))
        assertFalse(TransmissionClient(ClientConfig(ClientKind.TRANSMISSION, "T", "x", "", ""), http).signIn("me", "wrong"))
    }

    @Test fun transmission_states_read_as_qbittorrent_words() {
        assertEquals("pausedDL", TransmissionApi.state(0, 0, 0.5, 1.0, 0, 0))
        assertEquals("checkingDL", TransmissionApi.state(2, 0, 0.5, 1.0, 0, 0))
        assertEquals("metaDL", TransmissionApi.state(4, 0, 0.0, 0.5, 3, 10))
        assertEquals("stalledDL", TransmissionApi.state(4, 0, 0.5, 1.0, 0, 0))
        assertEquals("downloading", TransmissionApi.state(4, 0, 0.5, 1.0, 2, 900))
        assertEquals("uploading", TransmissionApi.state(6, 0, 1.0, 1.0, 0, 0))
        assertEquals("error", TransmissionApi.state(4, 3, 0.5, 1.0, 2, 900))
    }

    @Test fun base64_matches_the_standard_encoding() {
        assertEquals("", TransmissionClient.base64(ByteArray(0)))
        assertEquals("Zg==", TransmissionClient.base64("f".toByteArray()))
        assertEquals("Zm8=", TransmissionClient.base64("fo".toByteArray()))
        assertEquals("Zm9v", TransmissionClient.base64("foo".toByteArray()))
        assertEquals("dXNlcjpw4pyTc3M=", TransmissionClient.base64("user:p✓ss".toByteArray()))
    }

    // --- Deluge ------------------------------------------------------------------------------------

    private fun delugeStatus(state: String, progress: Double = 20.0) =
        """{"id":1,"error":null,"result":{"$hash":{"state":"$state","queue":0,"progress":$progress,"download_payload_rate":0,
            "num_seeds":0,"total_wanted":1000,"total_done":200,"eta":0}}}"""

    private fun delugeServer(password: String, connected: Boolean = true) = Fake { body, h ->
        val method = Regex("\"method\":\"([^\"]+)\"").find(body)!!.groupValues[1]
        when {
            method == "auth.login" -> if (body.contains("\"$password\"")) HttpReply(200, """{"id":1,"error":null,"result":true}""",
                mapOf("Set-Cookie" to listOf("_session_id=s3ss; Path=/json"))) else HttpReply(200, """{"id":1,"error":null,"result":false}""")
            h["Cookie"] != "_session_id=s3ss" -> HttpReply(200, """{"id":1,"error":{"message":"Not authenticated","code":1},"result":null}""")
            method == "web.connected" -> HttpReply(200, """{"id":1,"error":null,"result":$connected}""")
            method == "web.get_hosts" -> HttpReply(200, """{"id":1,"error":null,"result":[["h1","127.0.0.1",58846,"Online"]]}""")
            method == "core.get_torrents_status" -> HttpReply(200, delugeStatus("Queued"))
            else -> HttpReply(200, """{"id":1,"error":null,"result":null}""")
        }
    }

    @Test fun deluge_logs_in_with_a_password_connects_and_reads_torrents() {
        val http = delugeServer("pw", connected = false)
        val states = DelugeClient(ClientConfig(ClientKind.DELUGE, "D", "x", "", "pw"), http).states()!!
        assertTrue(http.bodies.any { it.contains("web.connect") && it.contains("h1") })
        val t = states[hash]!!
        assertEquals("queuedDL", t.state); assertEquals(1, t.priority); assertEquals(0.2, t.progress, 0.001)
        assertEquals(800L, t.leftBytes)
    }

    @Test fun deluge_without_a_password_asks_for_one_and_a_wrong_one_is_refused() {
        val http = delugeServer("pw")
        assertEquals(DownloadActionResult.NEEDS_SIGN_IN, DelugeClient(ClientConfig(ClientKind.DELUGE, "D", "x", "", ""), http).perform(hash, DownloadAction.NEXT))
        val c = DelugeClient(ClientConfig(ClientKind.DELUGE, "D", "x", "", ""), http)
        assertFalse(c.signIn("", "nope"))
        assertTrue(c.signIn("ignored", "pw"))
        assertEquals(DownloadActionResult.DONE, c.perform(hash, DownloadAction.NOW))
        assertTrue(http.bodies.any { it.contains("core.queue_top") })
        assertTrue(http.bodies.any { it.contains("core.resume_torrents") })
    }

    @Test fun deluge_states_read_as_qbittorrent_words() {
        assertEquals("stalledDL", DelugeApi.state("Downloading", 0.5, 0, 0))
        assertEquals("downloading", DelugeApi.state("Downloading", 0.5, 4, 100))
        assertEquals("pausedDL", DelugeApi.state("Paused", 0.5, 0, 0))
        assertEquals("uploading", DelugeApi.state("Paused", 1.0, 0, 0))
        assertEquals("checkingDL", DelugeApi.state("Allocating", 0.0, 0, 0))
    }
}
