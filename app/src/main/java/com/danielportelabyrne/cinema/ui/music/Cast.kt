package com.danielportelabyrne.cinema.ui.music

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.danielportelabyrne.cinema.data.music.cast.CastControl
import com.danielportelabyrne.cinema.data.music.cast.CastHost
import com.danielportelabyrne.cinema.data.music.cast.CastItem
import com.danielportelabyrne.cinema.data.music.cast.CastSnapshot
import com.danielportelabyrne.cinema.data.music.cast.Transport
import com.danielportelabyrne.cinema.data.music.cast.Upnp
import com.danielportelabyrne.cinema.platform.Log

/**
 * The renderer a phone casts to (Symfonium, say), between the network and the now-playing screen. It keeps what the
 * controller asked for, opens the screen to play it, and passes the screen's progress back.
 */
object Cast : CastControl {
    /** The now-playing screen; called on the main thread. */
    interface Link {
        fun castLoad(current: CastItem, next: CastItem?)
        fun castNext(next: CastItem?)
        fun castPlay()
        fun castPause()
        fun castStop()
        fun castSeek(positionMs: Long)
        fun castSkip(direction: Int)
    }

    private val ui = Handler(Looper.getMainLooper())
    private lateinit var app: Context
    private val audio by lazy { app.getSystemService(AudioManager::class.java) }
    @Volatile var host: CastHost? = null
    @Volatile private var current: CastItem? = null
    @Volatile private var next: CastItem? = null
    @Volatile private var transport = Transport.NO_MEDIA
    @Volatile private var positionMs = 0L
    @Volatile private var durationMs = 0L
    private var link: Link? = null
    /** Play once the screen opens. */
    private var playOnOpen = false

    fun init(context: Context) { app = context.applicationContext }

    // --- From the network -------------------------------------------------------------------------------

    override fun snapshot() = CastSnapshot(transport, current, next, positionMs, durationMs, volume(), muted())

    override fun load(item: CastItem) {
        current = item; next = null; positionMs = 0; durationMs = item.durationMs ?: 0
        if (transport == Transport.NO_MEDIA) transport = Transport.STOPPED
        ui.post { link?.castLoad(item, null) }
        changed()
    }

    override fun loadNext(item: CastItem?) {
        next = item
        ui.post { link?.castNext(item) }
        changed()
    }

    override fun play() { ui.post {
        val l = link
        if (l != null) { l.castPlay(); return@post }
        playOnOpen = true
        transport = Transport.TRANSITIONING
        changed()
        // Needs "display over other apps" when Cinema is closed.
        runCatching {
            app.startActivity(Intent(app, MusicPlayerActivity::class.java).putExtra(MusicPlayerActivity.EXTRA_CAST, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Log.w(TAG, "could not open now playing", it) }
    } }

    override fun pause() { ui.post { link?.castPause() } }

    override fun stop() { ui.post {
        playOnOpen = false
        val l = link
        if (l != null) l.castStop() else { transport = Transport.STOPPED; positionMs = 0; changed() }
    } }

    override fun seek(positionMs: Long) { ui.post { link?.castSeek(positionMs) } }

    override fun next() { ui.post { link?.castSkip(1) } }

    override fun previous() { ui.post { link?.castSkip(-1) } }

    override fun setVolume(percent: Int) {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, (percent * max + 50) / 100, 0)
        host?.changed(Upnp.RENDERING_CONTROL)
    }

    override fun setMuted(muted: Boolean) {
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0)
        host?.changed(Upnp.RENDERING_CONTROL)
    }

    private fun volume(): Int {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max
    }

    private fun muted() = audio.isStreamMute(AudioManager.STREAM_MUSIC)

    private fun changed() { host?.changed(Upnp.AV_TRANSPORT) }

    // --- From the screen (main thread) ------------------------------------------------------------------

    /** The screen opened; it plays what was cast if it was opened for that. */
    fun attach(l: Link, forCast: Boolean) {
        link = l
        val c = current ?: return
        if (!forCast) return
        l.castLoad(c, next)
        if (playOnOpen) { playOnOpen = false; l.castPlay() }
    }

    fun detach(l: Link, wasCasting: Boolean) {
        if (link !== l) return
        link = null
        if (wasCasting) { transport = Transport.STOPPED; positionMs = 0; changed() }
    }

    /** Each frame while casting. */
    fun report(t: Transport, position: Long, duration: Long) {
        positionMs = position
        if (duration > 0) durationMs = duration
        if (t != transport) { transport = t; changed() }
    }

    /** The player moved to another track. */
    fun showing(now: CastItem, after: CastItem?) {
        if (now === current && after === next) return
        current = now
        next = after
        positionMs = 0
        durationMs = now.durationMs ?: 0
        changed()
    }

    private const val TAG = "CinemaCast"
}
