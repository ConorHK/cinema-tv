package com.danielportelabyrne.cinema.data.letterboxd

import javafx.animation.PauseTransition
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.scene.Scene
import javafx.scene.web.WebView
import javafx.util.Duration
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/** Internal worker. The parent bounds its lifetime and treats a browser failure as an unavailable page. */
fun main(args: Array<String>) {
    if (args.size != 2) exitProcess(1)
    val done = CountDownLatch(1)
    var html: String? = null
    try {
        Platform.startup {
            Platform.setImplicitExit(false)
            try {
                val view = WebView()
                // Give WebKit a scene for its image and font resources, without opening another window.
                val scene = Scene(view, 1280.0, 800.0)
                val engine = view.engine
                engine.userAgent = LetterboxdWeb.USER_AGENT
                engine.isJavaScriptEnabled = true
                fun poll(attempt: Int) {
                    if (done.count == 0L) return
                    val page = runCatching { engine.executeScript("document.documentElement.outerHTML") as? String }.getOrNull()
                    if (page != null && page.length > 40 && !LetterboxdWeb.isChallenge(page)) {
                        html = page; done.countDown()
                    } else if (attempt < 60) {
                        PauseTransition(Duration.millis(400.0)).apply { setOnFinished { poll(attempt + 1) }; play() }
                    } else done.countDown()
                }
                engine.loadWorker.stateProperty().addListener { _, _, state ->
                    if (state == Worker.State.SUCCEEDED) poll(0)
                    else if (state == Worker.State.FAILED || state == Worker.State.CANCELLED) done.countDown()
                }
                // The scene must stay reachable while WebKit loads.
                view.properties["scene"] = scene
                engine.load(args[0])
            } catch (_: Throwable) { done.countDown() }
        }
        if (!done.await(30, TimeUnit.SECONDS)) exitProcess(1)
        val page = html ?: exitProcess(1)
        File(args[1]).writeText(page)
        exitProcess(0)
    } catch (_: Throwable) { exitProcess(1) }
}
