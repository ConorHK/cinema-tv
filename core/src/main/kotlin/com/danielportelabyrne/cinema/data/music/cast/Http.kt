package com.danielportelabyrne.cinema.data.music.cast

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream

/** One HTTP request as UPnP controllers send them: headers keyed in upper case. */
class HttpRequest(val method: String, val path: String, val headers: Map<String, String>, val body: String) {
    fun header(name: String): String? = headers[name.uppercase()]

    companion object {
        private const val MAX_BODY = 1 shl 20

        /** The next request on [input], or null when it is closed or not HTTP. */
        fun read(input: InputStream): HttpRequest? {
            val first = line(input)?.takeIf { it.isNotBlank() } ?: return null
            val parts = first.split(' ')
            if (parts.size < 3) return null
            val headers = HashMap<String, String>()
            while (true) {
                val l = line(input) ?: return null
                if (l.isEmpty()) break
                val colon = l.indexOf(':')
                if (colon > 0) headers[l.substring(0, colon).trim().uppercase()] = l.substring(colon + 1).trim()
            }
            val body = when {
                headers["TRANSFER-ENCODING"]?.contains("chunked", ignoreCase = true) == true -> chunked(input) ?: return null
                else -> {
                    val length = headers["CONTENT-LENGTH"]?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
                    exactly(input, length) ?: return null
                }
            }
            return HttpRequest(parts[0].uppercase(), parts[1], headers, body.toString(Charsets.UTF_8))
        }

        private fun line(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b < 0) return if (out.size() == 0) null else out.toString("ISO-8859-1")
                if (b == '\n'.code) return out.toString("ISO-8859-1").trimEnd('\r')
                if (out.size() > 8192) return null
                out.write(b)
            }
        }

        private fun exactly(input: InputStream, n: Int): ByteArray? {
            val buf = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = input.read(buf, read, n - read)
                if (r < 0) return null
                read += r
            }
            return buf
        }

        private fun chunked(input: InputStream): ByteArray? {
            val out = ByteArrayOutputStream()
            while (true) {
                val size = line(input)?.substringBefore(';')?.trim()?.toIntOrNull(16) ?: return null
                if (size == 0) { line(input); return out.toByteArray() }
                if (out.size() + size > MAX_BODY) return null
                out.write(exactly(input, size) ?: return null)
                line(input)
            }
        }
    }
}

/** Writes one response and asks the controller to close the connection. */
fun writeResponse(out: OutputStream, status: Int, body: String = "", headers: List<Pair<String, String>> = emptyList()) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 412 -> "Precondition Failed"; else -> "Internal Server Error" }
    val head = StringBuilder("HTTP/1.1 $status $reason\r\n")
    if (bytes.isNotEmpty()) head.append("Content-Type: text/xml; charset=\"utf-8\"\r\n")
    for ((k, v) in headers) head.append("$k: $v\r\n")
    head.append("Content-Length: ${bytes.size}\r\nServer: ${Ssdp.SERVER}\r\nConnection: close\r\n\r\n")
    out.write(head.toString().toByteArray(Charsets.ISO_8859_1))
    out.write(bytes)
    out.flush()
}
