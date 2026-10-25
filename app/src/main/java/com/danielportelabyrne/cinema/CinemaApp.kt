package com.danielportelabyrne.cinema

import android.app.Application
import android.util.Log as AndroidLog
import com.danielportelabyrne.cinema.platform.Log

/** Sends the shared code's log lines to logcat. */
class CinemaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.sink = Log.Sink { level, tag, message, error ->
            when (level) {
                'D' -> AndroidLog.d(tag, message, error)
                'I' -> AndroidLog.i(tag, message, error)
                'W' -> AndroidLog.w(tag, message, error)
                else -> AndroidLog.e(tag, message, error)
            }
        }
    }
}
