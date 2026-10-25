package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

/** Connection settings for qBittorrent, read from Sonarr/Radarr (see [DownloadClients.config]). */
class QbitConfig(val baseUrl: String, val username: String, val password: String)

data class TorrentState(val hash: String, val state: String, val priority: Int, val forced: Boolean, val progress: Double,
    val speedBytes: Long? = null, val seeds: Int? = null, val sizeBytes: Long? = null, val leftBytes: Long? = null, val etaSeconds: Long? = null)
enum class DownloadAction { NEXT, NOW }
enum class DownloadActionResult { DONE, QUEUE_DISABLED, NOT_FOUND, NOT_CONFIGURED, NEEDS_SIGN_IN, FAILED }

object QbitApi {
    /** 5.2 returns 204 and QBT_SID_<port>; older servers return 200/Ok. and SID. */
    fun loginCookie(code: Int, body: String, cookies: List<String>): String? {
        if (code !in 200..299 || body.trim() == "Fails.") return null
        return cookies.map { it.substringBefore(';').trim() }.firstOrNull {
            Regex("(?:SID|QBT_SID_[0-9]+)=[^;\\s]+").matches(it)
        }
    }

    fun validHash(hash: String?): Boolean = hash != null && Regex("(?:[a-fA-F0-9]{40}|[a-fA-F0-9]{64})").matches(hash)
    fun form(vararg fields: Pair<String, String>): String = fields.joinToString("&") { (key, value) ->
        URLEncoder.encode(key, "UTF-8") + "=" + URLEncoder.encode(value, "UTF-8")
    }

    /** Match the actual named client; never accidentally send a command to another download client. */
    fun config(json: String, clientName: String?, serverHost: String): QbitConfig? =
        DownloadClients.config(json, clientName, serverHost)?.takeIf { it.kind == ClientKind.QBITTORRENT }?.let { QbitConfig(it.baseUrl, it.username, it.password) }

    fun torrents(json: String): Map<String, TorrentState> = try {
        val all = JSONArray(json)
        (0 until all.length()).map { all.getJSONObject(it) }.mapNotNull {
            val hash = it.optString("hash").lowercase()
            if (!validHash(hash)) null else hash to TorrentState(hash, it.optString("state"), it.optInt("priority", -1),
                it.optBoolean("force_start"), it.optDouble("progress", 0.0),
                speedBytes = it.optLong("dlspeed", -1).takeIf { v -> v >= 0 },
                seeds = it.optInt("num_seeds", -1).takeIf { v -> v >= 0 },
                sizeBytes = it.optLong("size", -1).takeIf { v -> v >= 0 },
                leftBytes = it.optLong("amount_left", -1).takeIf { v -> v >= 0 },
                etaSeconds = it.optLong("eta", -1).takeIf { v -> v in 1 until 8_640_000 })
        }.toMap()
    } catch (_: Exception) { emptyMap() }

    fun apply(item: QueueItem, torrent: TorrentState): QueueItem {
        // Leave import errors visible even after the torrent has finished downloading.
        val status = if (torrent.progress >= 1.0) item.status else when (torrent.state) {
            "queuedDL" -> "queued"
            "pausedDL", "stoppedDL" -> "paused"
            "downloading", "forcedDL" -> "downloading"
            "metaDL", "forcedMetaDL" -> "metadata"
            "stalledDL" -> "stalled"
            "checkingDL", "checkingResumeData" -> "checking"
            "error", "missingFiles" -> "failed"
            else -> item.status
        }
        val size = torrent.sizeBytes?.takeIf { it > 0 }
        return item.copy(sizeBytes = size ?: item.sizeBytes,
            sizeLeftBytes = if (size != null) torrent.leftBytes?.coerceIn(0, size) ?: item.sizeLeftBytes else item.sizeLeftBytes,
            etaSeconds = torrent.etaSeconds ?: item.etaSeconds, speedBytes = torrent.speedBytes, seeds = torrent.seeds,
            status = status, position = if (status == "queued") torrent.priority.coerceAtLeast(0) else 0,
            canPrioritize = torrent.progress < 1.0 && status !in setOf("completed", "failed", "checking"), forced = torrent.forced, clientVerified = true)
    }
}

internal data class QbitReply(val code: Int, val body: String = "", val cookies: List<String> = emptyList())
internal fun interface QbitTransport {
    fun call(method: String, path: String, body: String?, cookie: String?): QbitReply
}

/** An isolated cookie session for this server; passwords, cookies and responses are never logged or saved. */
internal class QbitClient(
    private val qbit: QbitConfig, private val transport: QbitTransport = http(qbit),
    initialCookie: String? = null, private val onSession: (String) -> Unit = {}
) : DownloadClient {
    private var cookie: String? = initialCookie
    private var rejectedCredentials = false
    private var username = qbit.username
    private var password = qbit.password

    override val config: ClientConfig get() = ClientConfig(ClientKind.QBITTORRENT, "", qbit.baseUrl, username, password)

    @Synchronized
    override fun states(): Map<String, TorrentState>? {
        val reply = call("GET", "/api/v2/torrents/info")
        return if (reply.code == 200 && reply.body.trimStart().startsWith("[")) QbitApi.torrents(reply.body) else null
    }

    @Synchronized
    override fun perform(hash: String, action: DownloadAction): DownloadActionResult {
        if (!QbitApi.validHash(hash)) return DownloadActionResult.NOT_FOUND
        val selected = hash.lowercase()
        val check = call("GET", "/api/v2/torrents/info?" + QbitApi.form("hashes" to selected))
        if (check.code in setOf(401, 403)) return DownloadActionResult.NEEDS_SIGN_IN
        if (check.code != 200) return DownloadActionResult.FAILED
        val torrent = QbitApi.torrents(check.body)[selected] ?: return DownloadActionResult.NOT_FOUND
        if (torrent.progress >= 1.0) return DownloadActionResult.NOT_FOUND
        val hashes = QbitApi.form("hashes" to selected)
        if (action == DownloadAction.NOW) {
            return if (call("POST", "/api/v2/torrents/setForceStart", "$hashes&value=true").code in 200..299)
                DownloadActionResult.DONE else DownloadActionResult.FAILED
        }
        val moved = call("POST", "/api/v2/torrents/topPrio", hashes)
        if (moved.code == 409) return DownloadActionResult.QUEUE_DISABLED
        if (moved.code !in 200..299) return DownloadActionResult.FAILED
        if (torrent.forced && call("POST", "/api/v2/torrents/setForceStart", "$hashes&value=false").code !in 200..299)
            return DownloadActionResult.FAILED
        var resumed = call("POST", "/api/v2/torrents/start", hashes)
        if (resumed.code in setOf(404, 405)) resumed = call("POST", "/api/v2/torrents/resume", hashes)
        return if (resumed.code in 200..299) DownloadActionResult.DONE else DownloadActionResult.FAILED
    }

    /** User-entered password goes only to the login endpoint; only its session cookie is retained. */
    @Synchronized
    override fun signIn(username: String, password: String): Boolean {
        val login = try { transport.call("POST", "/api/v2/auth/login", QbitApi.form("username" to username, "password" to password), null) }
                    catch (_: Exception) { QbitReply(0) }
        val session = QbitApi.loginCookie(login.code, login.body, login.cookies) ?: return false
        cookie = session
        this.username = username; this.password = password
        rejectedCredentials = false
        onSession(session)
        return true
    }

    private fun call(method: String, path: String, body: String? = null): QbitReply {
        fun send() = try { transport.call(method, path, body, cookie) } catch (_: Exception) { QbitReply(0) }
        var reply = send()
        if (reply.code in setOf(401, 403)) {
            if (rejectedCredentials || password.isEmpty()) return reply // masked settings need one-time sign-in
            val login = try { transport.call("POST", "/api/v2/auth/login",
                QbitApi.form("username" to username, "password" to password), null) } catch (_: Exception) { QbitReply(0) }
            cookie = QbitApi.loginCookie(login.code, login.body, login.cookies)
            if (cookie == null) {
                if (login.code in 200..299 || login.code in setOf(401, 403)) rejectedCredentials = true
                return reply
            }
            onSession(cookie!!)
            reply = send()
        }
        return reply
    }

    companion object {
        private fun http(config: QbitConfig) = QbitTransport { method, path, body, cookie ->
            val connection = URL(config.baseUrl + path).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = 6000; connection.readTimeout = 15000
            connection.setRequestProperty("Referer", config.baseUrl + "/")
            cookie?.let { connection.setRequestProperty("Cookie", it) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            connection.exchange {
                if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val text = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
                QbitReply(code, text, connection.headerFields.entries.filter { it.key.equals("Set-Cookie", true) }.flatMap { it.value })
            }
        }
    }
}
