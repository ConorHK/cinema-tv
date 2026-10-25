package com.danielportelabyrne.cinema.desktop

import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.desktop.input.NavAction
import java.awt.Window
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket

/**
 * Aids for checking a build on a device from another machine (the Deck over SSH, as the TV is checked over adb). Off
 * unless CINEMA_PRESS_PORT is set, and the port only listens on this machine.
 *
 * Lines on 127.0.0.1:<port> are words: a [NavAction] (`DOWN`, `SELECT`...) is acted on as a pad press, `WAIT` pauses
 * half a second, and `SNAP` saves the window as it is drawn now to CINEMA_SNAPSHOT (a PNG path):
 * `echo "DOWN DOWN SELECT WAIT SNAP" > /dev/tcp/127.0.0.1/5577`.
 */
object DebugHooks {
    fun start(window: () -> Window?, press: (NavAction) -> Unit) {
        val port = System.getenv("CINEMA_PRESS_PORT")?.toIntOrNull() ?: return
        val snapshot = System.getenv("CINEMA_SNAPSHOT")?.takeIf { it.isNotBlank() }?.let(::File)
        Thread({
            try {
                ServerSocket(port, 4, InetAddress.getLoopbackAddress()).use { server ->
                    Log.i(TAG, "Taking presses on 127.0.0.1:$port")
                    while (true) {
                        server.accept().use { s ->
                            s.getInputStream().bufferedReader().lineSequence().forEach { line ->
                                line.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.forEach { word ->
                                    when {
                                        word.equals("SNAP", true) -> snapshot?.let { snap(it, window()) }
                                        word.equals("WAIT", true) -> Thread.sleep(500)
                                        else -> NavAction.entries.firstOrNull { it.name.equals(word, ignoreCase = true) }?.let { press(it); Thread.sleep(250) }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) { Log.w(TAG, "Press port closed: ${e.javaClass.simpleName}") }
        }, "presses").apply { isDaemon = true; start() }
    }

    /** The frame as Skia drew it: reading the screen instead gives black for an OpenGL window under Xwayland. */
    private fun snap(file: File, w: Window?) {
        try {
            val layer = w?.let { findLayer(it) } ?: return
            var bytes: ByteArray? = null
            javax.swing.SwingUtilities.invokeAndWait { bytes = layer.screenshot()?.let { org.jetbrains.skia.Image.makeFromBitmap(it) }?.encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes }
            val data = bytes ?: return
            val tmp = File(file.parentFile, file.name + ".tmp.png")
            tmp.writeBytes(data); tmp.renameTo(file)
        } catch (e: Throwable) { Log.w(TAG, "Snapshot failed: ${e.javaClass.simpleName}: ${e.message}") }
    }

    private fun findLayer(c: java.awt.Component): org.jetbrains.skiko.SkiaLayer? {
        if (c is org.jetbrains.skiko.SkiaLayer) return c
        if (c is java.awt.Container) c.components.forEach { child -> findLayer(child)?.let { return it } }
        return null
    }

    private const val TAG = "Debug"
}
