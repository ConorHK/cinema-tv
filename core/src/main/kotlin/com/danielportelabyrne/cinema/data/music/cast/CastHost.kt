package com.danielportelabyrne.cinema.data.music.cast

import com.danielportelabyrne.cinema.platform.Log
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A UPnP media renderer on the local network, playing through [control]: discovery, the description and service
 * documents, SOAP control and event subscriptions. [changed] tells subscribers the player moved on its own.
 */
class CastHost(private val control: CastControl, private val uuid: String, private val name: String) {
    private val renderer = Renderer(control)
    private val handlers = Executors.newCachedThreadPool { r -> Thread(r, "cast-http").apply { isDaemon = true } }
    private val events = Executors.newSingleThreadExecutor { r -> Thread(r, "cast-events").apply { isDaemon = true } }
    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private var server: ServerSocket? = null
    private var ssdp: Ssdp? = null

    private class Subscription(val service: String, val callbacks: List<String>, var expires: Long) { var seq = 0L }

    /** Opens the port and starts announcing; call off the main thread. */
    @Synchronized fun start() {
        if (server != null) return
        val s = runCatching { ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress(PREFERRED_PORT)) } }
            .getOrElse { ServerSocket(0) }
        server = s
        Thread({ accept(s) }, "cast-accept").apply { isDaemon = true }.start()
        ssdp = Ssdp(uuid, s.localPort).also { it.start() }
        Log.i(TAG, "renderer \"$name\" on port ${s.localPort}")
    }

    /** Says goodbye and closes the port; call off the main thread. */
    @Synchronized fun stop() {
        ssdp?.stop(); ssdp = null
        server?.close(); server = null
        subscriptions.clear()
    }

    /** Tells subscribers to [services] what changed. */
    fun changed(vararg services: String) {
        val now = System.currentTimeMillis()
        subscriptions.entries.removeIf { it.value.expires < now }
        for ((sid, sub) in subscriptions) if (sub.service in services) send(sid, sub)
    }

    private fun accept(s: ServerSocket) {
        while (!s.isClosed) {
            val client = runCatching { s.accept() }.getOrNull() ?: continue
            handlers.execute { client.use { serve(it) } }
        }
    }

    private fun serve(socket: Socket) {
        runCatching {
            socket.soTimeout = 10_000
            val req = HttpRequest.read(socket.getInputStream()) ?: return
            val out = socket.getOutputStream()
            val path = req.path.substringBefore('?')
            val service = Upnp.SERVICES.firstOrNull { path.startsWith("/" + Upnp.shortName(it) + "/") || path == "/" + Upnp.shortName(it) + ".xml" }
            when {
                req.method == "GET" && path == "/description.xml" -> writeResponse(out, 200, Upnp.description(uuid, name))
                req.method == "GET" && service != null && path.endsWith(".xml") -> writeResponse(out, 200, Upnp.scpd(service)!!)
                req.method == "POST" && service != null && path.endsWith("/control") -> {
                    val action = Upnp.parseAction(req.body)
                    if (action == null) writeResponse(out, 400)
                    else renderer.handle(service, action).let { writeResponse(out, it.status, it.body) }
                }
                req.method == "SUBSCRIBE" && service != null -> subscribe(req, service, out)
                req.method == "UNSUBSCRIBE" -> { req.header("SID")?.let { subscriptions.remove(it) }; writeResponse(out, 200) }
                else -> writeResponse(out, 404)
            }
        }.onFailure { Log.w(TAG, "request failed", it) }
    }

    private fun subscribe(req: HttpRequest, service: String, out: OutputStream) {
        val seconds = req.header("TIMEOUT")?.substringAfter("Second-", "")?.toLongOrNull()?.coerceIn(60, 3600) ?: 1800
        val expires = System.currentTimeMillis() + seconds * 1000
        val renew = req.header("SID")
        if (renew != null) {
            val sub = subscriptions[renew] ?: return writeResponse(out, 412)
            sub.expires = expires
            return writeResponse(out, 200, headers = listOf("SID" to renew, "TIMEOUT" to "Second-$seconds"))
        }
        val callbacks = Regex("<([^>]+)>").findAll(req.header("CALLBACK").orEmpty()).map { it.groupValues[1] }.toList()
        if (callbacks.isEmpty() || req.header("NT") != "upnp:event") return writeResponse(out, 412)
        val sid = "uuid:" + UUID.randomUUID()
        val sub = Subscription(service, callbacks, expires)
        subscriptions[sid] = sub
        writeResponse(out, 200, headers = listOf("SID" to sid, "TIMEOUT" to "Second-$seconds"))
        send(sid, sub)
    }

    /** NOTIFY is not a method HttpURLConnection allows, so it goes out by hand. */
    private fun send(sid: String, sub: Subscription) {
        val body = Upnp.propertySet(renderer.state(sub.service)).toByteArray(Charsets.UTF_8)
        val seq = synchronized(sub) { sub.seq++ }
        events.execute {
            for (callback in sub.callbacks) {
                val ok = runCatching {
                    val uri = URI(callback)
                    Socket().use { s ->
                        s.connect(InetSocketAddress(uri.host, if (uri.port > 0) uri.port else 80), 3_000)
                        s.soTimeout = 3_000
                        val head = "NOTIFY ${uri.rawPath.ifEmpty { "/" }}${uri.rawQuery?.let { "?$it" }.orEmpty()} HTTP/1.1\r\n" +
                            "HOST: ${uri.host}:${uri.port}\r\nCONTENT-TYPE: text/xml; charset=\"utf-8\"\r\nNT: upnp:event\r\n" +
                            "NTS: upnp:propchange\r\nSID: $sid\r\nSEQ: $seq\r\nCONTENT-LENGTH: ${body.size}\r\nCONNECTION: close\r\n\r\n"
                        s.getOutputStream().apply { write(head.toByteArray(Charsets.ISO_8859_1)); write(body); flush() }
                        s.getInputStream().bufferedReader().readLine()
                    }
                }.isSuccess
                if (ok) break
            }
        }
    }

    companion object {
        private const val TAG = "CinemaCast"
        private const val PREFERRED_PORT = 49494
    }
}
