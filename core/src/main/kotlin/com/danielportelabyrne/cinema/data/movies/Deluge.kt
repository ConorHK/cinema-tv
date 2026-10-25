package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/** Reading Deluge's answers, kept apart from networking so it can be tested. */
object DelugeApi {
    val FIELDS = listOf("state", "queue", "progress", "download_payload_rate", "num_seeds", "total_wanted", "total_done", "eta")

    /** Deluge's state names in qBittorrent's words. */
    fun state(state: String, progress: Double, seeds: Int, rate: Long): String = when (state) {
        "Error" -> "error"
        "Seeding" -> "uploading"
        "Checking", "Allocating", "Moving" -> "checkingDL"
        "Queued" -> if (progress >= 1.0) "uploading" else "queuedDL"
        "Paused" -> if (progress >= 1.0) "uploading" else "pausedDL"
        "Downloading" -> if (seeds == 0 && rate == 0L) "stalledDL" else "downloading"
        else -> "downloading"
    }

    /** `core.get_torrents_status`'s result: an object of torrents by hash. */
    fun torrents(json: String): Map<String, TorrentState> = try {
        val result = JSONObject(json).getJSONObject("result")
        result.keys().asSequence().mapNotNull { key ->
            val hash = key.lowercase()
            if (!QbitApi.validHash(hash)) return@mapNotNull null
            val t = result.getJSONObject(key)
            val progress = (t.optDouble("progress", 0.0) / 100.0).coerceIn(0.0, 1.0)
            val rate = t.optLong("download_payload_rate", 0)
            val seeds = t.optInt("num_seeds", 0)
            val state = state(t.optString("state"), progress, seeds, rate)
            val wanted = t.optLong("total_wanted", -1)
            val done = t.optLong("total_done", -1)
            hash to TorrentState(hash, state,
                // Deluge counts its queue from 0; the queue row counts from 1, as qBittorrent does.
                priority = if (state == "queuedDL") t.optInt("queue", -1) + 1 else 0,
                forced = false, progress = progress,
                speedBytes = rate.takeIf { it >= 0 }, seeds = seeds.takeIf { it >= 0 },
                sizeBytes = wanted.takeIf { it >= 0 },
                leftBytes = if (wanted >= 0 && done >= 0) (wanted - done).coerceAtLeast(0) else null,
                etaSeconds = t.optLong("eta", -1).takeIf { it in 1 until 8_640_000 })
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    fun call(id: Int, method: String, vararg params: Any): String =
        JSONObject().put("id", id).put("method", method).put("params", JSONArray(params.toList())).toString()

    /** Error code 1 is "Not authenticated". */
    fun needsLogin(json: String): Boolean = try { JSONObject(json).optJSONObject("error")?.optInt("code") == 1 } catch (_: Exception) { false }
    fun failed(json: String): Boolean = try { !JSONObject(json).isNull("error") } catch (_: Exception) { true }
    fun resultTrue(json: String): Boolean = try { JSONObject(json).optBoolean("result", false) } catch (_: Exception) { false }

    /** The first host's id from `web.get_hosts` (`[["id", "127.0.0.1", 58846, "Online"], ...]`). */
    fun firstHost(json: String): String? = try { JSONObject(json).getJSONArray("result").getJSONArray(0).getString(0) } catch (_: Exception) { null }
}

/**
 * Deluge's Web UI JSON-RPC (`<urlBase>/json`). It signs in with a password only, keeps a session cookie in memory, and
 * makes sure the Web UI is connected to a Deluge daemon before asking about torrents. "Now" is the same as "next" here,
 * since Deluge has no force-start: the torrent goes to the top of the queue and is resumed. Nothing is logged.
 */
internal class DelugeClient(
    initial: ClientConfig, private val transport: HttpTransport = httpTransport(initial.baseUrl)
) : DownloadClient {
    private var login = initial
    private var cookie: String? = null
    private var nextId = 1
    override val config: ClientConfig get() = login

    @Synchronized
    override fun states(): Map<String, TorrentState>? {
        if (!ready()) return null
        val reply = rpc("core.get_torrents_status", JSONObject(), JSONArray(DelugeApi.FIELDS))
        return if (reply.code == 200 && !DelugeApi.failed(reply.body)) DelugeApi.torrents(reply.body) else null
    }

    @Synchronized
    override fun perform(hash: String, action: DownloadAction): DownloadActionResult {
        if (!QbitApi.validHash(hash)) return DownloadActionResult.NOT_FOUND
        if (!ready()) return if (cookie == null) DownloadActionResult.NEEDS_SIGN_IN else DownloadActionResult.FAILED
        val selected = hash.lowercase()
        val check = rpc("core.get_torrents_status", JSONObject().put("id", JSONArray().put(selected)), JSONArray(DelugeApi.FIELDS))
        if (check.code != 200 || DelugeApi.failed(check.body)) return DownloadActionResult.FAILED
        val torrent = DelugeApi.torrents(check.body)[selected] ?: return DownloadActionResult.NOT_FOUND
        if (torrent.progress >= 1.0) return DownloadActionResult.NOT_FOUND
        val ids = JSONArray().put(selected)
        if (rpc("core.queue_top", ids).let { it.code != 200 || DelugeApi.failed(it.body) }) return DownloadActionResult.FAILED
        // Deluge 2 resumes a list with resume_torrents; Deluge 1.3 took the list in resume_torrent.
        val resumed = rpc("core.resume_torrents", ids).takeIf { it.code == 200 && !DelugeApi.failed(it.body) }
            ?: rpc("core.resume_torrent", ids)
        return if (resumed.code == 200 && !DelugeApi.failed(resumed.body)) DownloadActionResult.DONE else DownloadActionResult.FAILED
    }

    /** Deluge has no usernames: only the password is used. */
    @Synchronized
    override fun signIn(username: String, password: String): Boolean {
        val before = login
        login = login.copy(password = password)
        cookie = null
        if (authenticate()) return true
        login = before
        return false
    }

    /** Signed in (with the saved password when the session lapsed) and connected to a daemon. */
    private fun ready(retry: Boolean = true): Boolean {
        if (cookie == null && !authenticate()) return false
        val connected = rpc("web.connected")
        if (DelugeApi.needsLogin(connected.body)) { cookie = null; return retry && authenticate() && ready(retry = false) }
        if (DelugeApi.resultTrue(connected.body)) return true
        val host = DelugeApi.firstHost(rpc("web.get_hosts").body) ?: return false
        return rpc("web.connect", host).let { it.code == 200 && !DelugeApi.failed(it.body) }
    }

    private fun authenticate(): Boolean {
        if (login.password.isEmpty()) return false
        val reply = send(DelugeApi.call(nextId++, "auth.login", login.password))
        if (reply.code != 200 || !DelugeApi.resultTrue(reply.body)) return false
        cookie = reply.cookies().map { it.substringBefore(';').trim() }.firstOrNull { it.startsWith("_session_id=") && it.length > 12 }
        return cookie != null
    }

    private fun rpc(method: String, vararg params: Any): HttpReply = send(DelugeApi.call(nextId++, method, *params))

    private fun send(body: String): HttpReply {
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/json"; headers["Accept"] = "application/json"
        cookie?.let { headers["Cookie"] = it }
        return transport.call("POST", "/json", body, headers)
    }
}
