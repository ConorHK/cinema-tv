package com.danielportelabyrne.cinema.ui

import com.danielportelabyrne.cinema.platform.Log

/**
 * Timing marks for the Movies performance work (`docs/movies-performance-plan-2026-10-23.md`), logged under the
 * "Perf" tag in debug builds only. Each mark carries the clock's reading, so a press in one activity and the
 * first frame in another can be subtracted from the log. The app turns it on ([enabled]) for a debuggable build and
 * points [clock] at a monotonic clock that suits it (the TV uses elapsed realtime).
 */
object Perf {
    @Volatile var enabled = false
    @Volatile var clock: () -> Long = { System.nanoTime() / 1_000_000 }
    private val starts = HashMap<String, Long>()

    fun now(): Long = clock()

    /** A named point in time. */
    fun mark(event: String) { if (enabled) Log.i("Perf", "$event @${now()}") }

    /** Starts a span that [end] closes. Starting it again restarts it. */
    @Synchronized fun begin(span: String) { if (enabled) starts[span] = now() }

    /** Logs how long [span] took since [begin], once; later calls do nothing until it begins again. */
    @Synchronized fun end(span: String, detail: String = "") {
        if (!enabled) return
        val at = starts.remove(span) ?: return
        Log.i("Perf", "$span ${now() - at} ms${if (detail.isEmpty()) "" else " ($detail)"} @${now()}")
    }

    /** Logs a measured duration. */
    fun took(what: String, ms: Long) { if (enabled) Log.i("Perf", "$what $ms ms") }
}
