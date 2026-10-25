package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.exchange
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import org.json.JSONArray

/**
 * The torrent clients the download controls (Download next, Download now, live speed and seeds) can talk to. Each is
 * named exactly as Sonarr and Radarr name it in their download-client settings. Anything else (rTorrent, Flood, Usenet
 * clients...) still shows progress and can be cancelled through Sonarr and Radarr; only the controls are missing.
 */
enum class ClientKind(val label: String, val arrName: String, val defaultPort: Int, val needsUsername: Boolean, val canForceStart: Boolean) {
    QBITTORRENT("qBittorrent", "QBittorrent", 8080, needsUsername = true, canForceStart = true),
    TRANSMISSION("Transmission", "Transmission", 9091, needsUsername = true, canForceStart = true),
    DELUGE("Deluge", "Deluge", 8112, needsUsername = false, canForceStart = false);

    companion object {
        fun fromArr(implementation: String?): ClientKind? = entries.firstOrNull { it.arrName.equals(implementation, ignoreCase = true) }
    }
}

/**
 * Where a client is and how to sign in to it, as Sonarr/Radarr hold it. [baseUrl] includes any URL base (Transmission's
 * "/transmission" by default). [password] is empty when Sonarr/Radarr masked it, and the owner is asked once instead.
 */
data class ClientConfig(val kind: ClientKind, val name: String, val baseUrl: String, val username: String, val password: String,
                        val passwordHidden: Boolean = false)

/** A download client Sonarr or Radarr uses, as listed in their settings, whether or not this app can control it. */
data class ListedClient(val name: String, val implementation: String, val kind: ClientKind?, val host: String)

/**
 * One torrent client. States use qBittorrent's vocabulary ("queuedDL", "pausedDL", "downloading", "stalledDL", "metaDL",
 * "checkingDL", "error", "uploading"), so [QbitApi.apply] turns any client's answer into the same queue row.
 * Every call blocks and none throw.
 */
interface DownloadClient {
    val config: ClientConfig
    /** Every torrent by lower-case hash, or null when the client cannot be read (unreachable or not signed in). */
    fun states(): Map<String, TorrentState>?
    fun perform(hash: String, action: DownloadAction): DownloadActionResult
    /** Tries a username and password the owner typed. True when they work; the caller decides whether to keep them. */
    fun signIn(username: String, password: String): Boolean
}

object DownloadClients {
    /** Every enabled torrent client in a Sonarr/Radarr `/api/v3/downloadclient` answer. */
    fun list(json: String, serverHost: String): List<ListedClient> = try {
        val all = JSONArray(json)
        (0 until all.length()).map { all.getJSONObject(it) }
            .filter { it.optBoolean("enable") && it.optString("protocol").let { p -> p.isBlank() || p.equals("torrent", true) } }
            .map { c ->
                val values = fields(c)
                ListedClient(c.optString("name"), c.optString("implementation"), ClientKind.fromArr(c.optString("implementation")), hostOf(values, serverHost))
            }
    } catch (_: Exception) { emptyList() }

    /**
     * The client a queue row came from. Sonarr/Radarr name it on each row ([clientName]); without a name there must be
     * exactly one supported client, so a command can never go to the wrong one. Null when it is not one we support.
     */
    fun config(json: String, clientName: String?, serverHost: String): ClientConfig? = try {
        val all = JSONArray(json)
        val enabled = (0 until all.length()).map { all.getJSONObject(it) }.filter { it.optBoolean("enable") }
        val chosen = if (!clientName.isNullOrBlank()) enabled.singleOrNull { it.optString("name") == clientName }
                     else enabled.filter { ClientKind.fromArr(it.optString("implementation")) != null }.singleOrNull()
        chosen?.let { c ->
            val kind = ClientKind.fromArr(c.optString("implementation")) ?: return@let null
            val values = fields(c)
            val port = values["port"]?.toIntOrNull() ?: kind.defaultPort
            val rawBase = values["urlBase"].orEmpty().trim('/').ifEmpty { if (kind == ClientKind.TRANSMISSION) "transmission" else "" }
            val prefix = if (rawBase.isEmpty()) "" else "/$rawBase"
            val scheme = if (values["useSsl"].toBoolean()) "https" else "http"
            val password = values["password"].orEmpty()
            val hidden = password.matches(Regex("\\*+"))
            ClientConfig(kind, c.optString("name"), URI(scheme, null, hostOf(values, serverHost), port, prefix, null, null).toString(),
                values["username"].orEmpty(), if (hidden) "" else password, passwordHidden = hidden)
        }
    } catch (_: Exception) { null }

    /** A client for [config], signed in with a login the owner saved earlier when Sonarr/Radarr hide the password. */
    fun create(config: ClientConfig, savedUser: String?, savedPassword: String?, cookie: String?, onSession: (String) -> Unit): DownloadClient {
        val login = if (savedPassword != null && (config.passwordHidden || config.password.isEmpty()))
            config.copy(username = savedUser ?: config.username, password = savedPassword) else config
        return when (config.kind) {
            ClientKind.QBITTORRENT -> QbitClient(QbitConfig(login.baseUrl, login.username, login.password), initialCookie = cookie, onSession = onSession)
            ClientKind.TRANSMISSION -> TransmissionClient(login)
            ClientKind.DELUGE -> DelugeClient(login)
        }
    }

    private fun fields(client: org.json.JSONObject): Map<String, String> {
        val fields = client.optJSONArray("fields") ?: return emptyMap()
        return (0 until fields.length()).map { fields.getJSONObject(it) }.associate {
            it.getString("name") to if (it.isNull("value")) "" else it.optString("value")
        }
    }

    /** Sonarr sees a client on the same machine as "localhost"; from the TV that is the server's own address. */
    private fun hostOf(values: Map<String, String>, serverHost: String): String =
        values["host"].takeUnless { h -> h.isNullOrBlank() || h in listOf("localhost", "127.0.0.1", "::1") } ?: serverHost
}

// --- Plain HTTP, swappable in tests ------------------------------------------------------------------

internal data class HttpReply(val code: Int, val body: String = "", val headers: Map<String, List<String>> = emptyMap()) {
    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()
    fun cookies(): List<String> = headers.entries.filter { it.key.equals("Set-Cookie", ignoreCase = true) }.flatMap { it.value }
}

internal fun interface HttpTransport {
    fun call(method: String, path: String, body: String?, headers: Map<String, String>): HttpReply
}

/** Requests to [baseUrl] + path. Passwords and answers are never logged. */
internal fun httpTransport(baseUrl: String) = HttpTransport { method, path, body, headers ->
    try {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        connection.requestMethod = method
        connection.connectTimeout = 6000; connection.readTimeout = 15000
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        if (body != null) connection.doOutput = true
        connection.exchange {
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val text = if (code in 200..299) connection.inputStream.bufferedReader().use { it.readText() } else ""
            HttpReply(code, text, connection.headerFields.filterKeys { it != null })
        }
    } catch (_: Exception) { HttpReply(0) }
}
