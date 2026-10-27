package com.danielportelabyrne.cinema

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log as AndroidLog
import com.danielportelabyrne.cinema.platform.Log
import com.danielportelabyrne.cinema.ui.music.Cast
import com.danielportelabyrne.cinema.ui.music.CastService
import com.danielportelabyrne.cinema.ui.music.MusicSession
import com.danielportelabyrne.cinema.ui.Perf

/** Connects the shared code to this device: its log lines go to logcat, and its timing marks (debug builds) use elapsed realtime. */
class CinemaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Perf.enabled = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        Perf.clock = SystemClock::elapsedRealtime
        MusicSession.init(this)
        Cast.init(this, MusicSession)
        CastService.sync(this)
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
