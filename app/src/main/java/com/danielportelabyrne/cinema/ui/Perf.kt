package com.danielportelabyrne.cinema.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log

/**
 * Timing marks for the Movies performance work (`docs/movies-performance-plan-2026-10-23.md`), logged under the
 * "Perf" tag in debug builds only. Each mark carries the elapsed-realtime clock, so a press in one activity and the
 * first frame in another can be subtracted from the log.
 */
object Perf {
    @Volatile private var enabled = false
    private val starts = HashMap<String, Long>()

    fun init(context: Context) { enabled = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    fun now(): Long = SystemClock.elapsedRealtime()

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
