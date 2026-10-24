package com.danielportelabyrne.cinema.data.movies

import org.json.JSONArray
import org.json.JSONObject

/** Reading Transmission's answers, kept apart from networking so it can be tested. */
object TransmissionApi {
    val FIELDS = listOf("hashString", "status", "queuePosition", "percentDone", "rateDownload", "peersSendingToUs",
        "totalSize", "leftUntilDone", "eta", "error", "metadataPercentComplete")

    /**
     * Transmission's numeric status in qBittorrent's words: 0 stopped, 1 waiting to check, 2 checking, 3 waiting to
     * download, 4 downloading, 5 waiting to seed, 6 seeding.
     */
    fun state(status: Int, error: Int, percentDone: Double, metadata: Double, peers: Int, rate: Long): String = when {
        error != 0 -> "error"
        percentDone >= 1.0 && status != 1 && status != 2 -> "uploading"
        status == 0 -> "pausedDL"
        status == 1 || status == 2 -> "checkingDL"
        status == 3 -> "queuedDL"
        status == 4 && metadata < 1.0 -> "metaDL"
        status == 4 && peers == 0 && rate == 0L -> "stalledDL"
        status == 4 -> "downloading"
        else -> "uploading"
    }

    fun torrents(json: String): Map<String, TorrentState> = try {
        val list = JSONObject(json).getJSONObject("arguments").getJSONArray("torrents")
        (0 until list.length()).map { list.getJSONObject(it) }.mapNotNull { t ->
            val hash = t.optString("hashString").lowercase()
            if (!QbitApi.validHash(hash)) return@mapNotNull null
            val done = t.optDouble("percentDone", 0.0)
            val rate = t.optLong("rateDownload", 0)
            val peers = t.optInt("peersSendingToUs", 0)
            val state = state(t.optInt("status"), t.optInt("error"), done, t.optDouble("metadataPercentComplete", 1.0), peers, rate)
            hash to TorrentState(hash, state,
                // Transmission counts its queue from 0; the queue row counts from 1, as qBittorrent does.
                priority = if (state == "queuedDL") t.optInt("queuePosition", -1) + 1 else 0,
                forced = false, progress = done,
                speedBytes = rate.takeIf { it >= 0 }, seeds = peers.takeIf { it >= 0 },
                sizeBytes = t.optLong("totalSize", -1).takeIf { it >= 0 },
                leftBytes = t.optLong("leftUntilDone", -1).takeIf { it >= 0 },
                etaSeconds = t.optLong("eta", -1).takeIf { it in 1 until 8_640_000 })
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    fun request(method: String, ids: List<String>? = null, fields: List<String>? = null): String {
        val args = JSONObject()
        ids?.let { args.put("ids", JSONArray(it)) }
        fields?.let { args.put("fields", JSONArray(it)) }
        return JSONObject().put("method", method).put("arguments", args).toString()
    }

    fun succeeded(json: String): Boolean = try { JSONObject(json).optString("result") == "success" } catch (_: Exception) { false }
}

/**
 * Transmission's RPC (`<urlBase>/rpc`). Every session starts with a 409 that hands over the X-Transmission-Session-Id to
 * send from then on; a username and password, when set, go as basic auth on each call. Nothing is logged.
 */
internal class TransmissionClient(
    initial: ClientConfig, private val transport: HttpTransport = httpTransport(initial.baseUrl)
) : DownloadClient {
    private var sessionId: String? = null
    private var login = initial
    override val config: ClientConfig get() = login

    @Synchronized
    override fun states(): Map<String, TorrentState>? {
        val reply = rpc(TransmissionApi.request("torrent-get", fields = TransmissionApi.FIELDS))
        return if (reply.code == 200 && TransmissionApi.succeeded(reply.body)) TransmissionApi.torrents(reply.body) else null
    }

    @Synchronized
    override fun perform(hash: String, action: DownloadAction): DownloadActionResult {
        if (!QbitApi.validHash(hash)) return DownloadActionResult.NOT_FOUND
        val selected = listOf(hash.lowercase())
        val check = rpc(TransmissionApi.request("torrent-get", selected, TransmissionApi.FIELDS))
        if (check.code == 401 || check.code == 403) return DownloadActionResult.NEEDS_SIGN_IN
        if (check.code != 200) return DownloadActionResult.FAILED
        val torrent = TransmissionApi.torrents(check.body)[selected[0]] ?: return DownloadActionResult.NOT_FOUND
        if (torrent.progress >= 1.0) return DownloadActionResult.NOT_FOUND
        fun ok(method: String) = rpc(TransmissionApi.request(method, selected)).let { it.code == 200 && TransmissionApi.succeeded(it.body) }
        val done = if (action == DownloadAction.NOW) ok("torrent-start-now")
                   else ok("queue-move-top") && ok("torrent-start")
        return if (done) DownloadActionResult.DONE else DownloadActionResult.FAILED
    }

    @Synchronized
    override fun signIn(username: String, password: String): Boolean {
        val before = login
        login = login.copy(username = username, password = password)
        val reply = rpc(TransmissionApi.request("session-get"))
        if (reply.code == 200) return true
        login = before
        return false
    }

    /** One call, repeating once with the session id when Transmission asks for it (409). */
    private fun rpc(body: String): HttpReply {
        var reply = send(body)
        if (reply.code == 409) {
            sessionId = reply.header(SESSION_HEADER) ?: return reply
            reply = send(body)
        }
        return reply
    }

    private fun send(body: String): HttpReply {
        val headers = HashMap<String, String>()
        headers["Content-Type"] = "application/json"
        sessionId?.let { headers[SESSION_HEADER] = it }
        if (login.username.isNotEmpty() || login.password.isNotEmpty()) headers["Authorization"] = basic(login.username, login.password)
        return transport.call("POST", "/rpc", body, headers)
    }

    companion object {
        const val SESSION_HEADER = "X-Transmission-Session-Id"
        fun basic(user: String, password: String): String = "Basic " + base64("$user:$password".toByteArray(Charsets.UTF_8))

        /** Standard Base64 with padding (java.util.Base64 needs Android 8, and this app runs on 7). */
        fun base64(bytes: ByteArray): String {
            val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
            val out = StringBuilder((bytes.size + 2) / 3 * 4)
            for (i in bytes.indices step 3) {
                val n = (bytes[i].toInt() and 0xFF shl 16) or
                    ((if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0) shl 8) or
                    (if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0)
                out.append(table[n shr 18 and 63]).append(table[n shr 12 and 63])
                out.append(if (i + 1 < bytes.size) table[n shr 6 and 63] else '=')
                out.append(if (i + 2 < bytes.size) table[n and 63] else '=')
            }
            return out.toString()
        }
    }
}
