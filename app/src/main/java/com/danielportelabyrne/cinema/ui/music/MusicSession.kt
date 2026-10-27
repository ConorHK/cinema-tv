package com.danielportelabyrne.cinema.ui.music

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.danielportelabyrne.cinema.data.music.MusicTrack
import com.danielportelabyrne.cinema.data.music.cast.CastItem
import com.danielportelabyrne.cinema.data.music.cast.Transport
import com.danielportelabyrne.cinema.platform.Log
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * The music playing in this process, apart from any screen: the library can be browsed while it plays. It plays
 * while something holds it (now playing, or the Music pages on screen) and stops soon after nothing does.
 * Main thread only.
 */
@OptIn(markerClass = [UnstableApi::class])
object MusicSession : Cast.Link {
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var app: Context
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    private val holders = HashSet<String>()

    var player: ExoPlayer? = null
        private set
    /** The queue, one per player item. */
    val tracks: MutableList<MusicTrack> = mutableListOf()
    /** While a phone casts: what it sent, one per entry in [tracks]. */
    val castItems: MutableList<CastItem> = mutableListOf()
    var casting = false
        private set
    /** Set while now playing scans with Left or Right held. */
    var scanning = false
    /** Three tracks in a row would not play. */
    var stuck = false
        private set
    private var failures = 0
    private var scrobbled: MusicTrack? = null
    private var announced: MusicTrack? = null

    fun init(context: Context) { app = context.applicationContext }

    val active: Boolean get() = player != null && tracks.isNotEmpty()

    val index: Int get() = player?.currentMediaItemIndex ?: -1

    val current: MusicTrack? get() = tracks.getOrNull(index)

    /** Called on track, play state and queue changes, and when the music stops. */
    fun listen(l: () -> Unit) { listeners += l }

    fun unlisten(l: () -> Unit) { listeners -= l }

    private fun changed() = listeners.forEach { it() }

    /** [key] is on screen and wants the music kept, or no longer. */
    fun hold(key: String, on: Boolean) {
        if (on) holders += key else holders -= key
        ui.removeCallbacks(release)
        // Moving between screens briefly holds nothing.
        if (holders.isEmpty() && player != null) ui.postDelayed(release, 1_500)
    }

    private val release = Runnable { if (holders.isEmpty()) stop() }

    // --- Queue ----------------------------------------------------------------------------------------

    fun play(list: List<MusicTrack>, start: Int) {
        if (list.isEmpty() || Music.client == null) return
        val p = ensure()
        leaveCast()
        tracks.clear(); tracks += list
        p.setMediaItems(list.map { item(it) }, start.coerceIn(0, list.lastIndex), 0L)
        p.prepare(); p.play()
        changed()
    }

    /** After the current track; starts playing when nothing is. */
    fun playNext(list: List<MusicTrack>) {
        if (!active || casting) return play(list, 0)
        val at = index + 1
        tracks.addAll(at, list)
        player?.addMediaItems(at, list.map { item(it) })
        changed()
    }

    /** At the end of the queue; starts playing when nothing is. */
    fun enqueue(list: List<MusicTrack>) {
        if (!active || casting) return play(list, 0)
        tracks += list
        player?.addMediaItems(list.map { item(it) })
        changed()
    }

    fun stop() {
        ui.removeCallbacks(release)
        ui.removeCallbacks(tick)
        val wasCasting = casting
        player?.release(); player = null
        tracks.clear(); castItems.clear(); casting = false; stuck = false
        scrobbled = null; announced = null
        if (wasCasting) Cast.stopped()
        changed()
    }

    private fun leaveCast() {
        if (!casting) return
        casting = false
        castItems.clear()
        Cast.stopped()
    }

    private fun item(t: MusicTrack): MediaItem {
        val c = Music.client!!
        return MediaItem.Builder()
            .setUri(c.config.baseUrl + c.streamPath(t.id)).setMediaId(t.id)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
            .build()
    }

    private fun ensure(): ExoPlayer {
        player?.let { return it }
        val renderers = DefaultRenderersFactory(app).setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        // Cast addresses come signed by the phone.
        val signing = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)) { spec ->
            val c = Music.client
            val uri = spec.uri.toString()
            if (c != null && !casting && uri.startsWith(c.config.baseUrl)) spec.withUri(Uri.parse(c.signed(uri.removePrefix(c.config.baseUrl)))) else spec
        }
        val p = ExoPlayer.Builder(app, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(signing)).build()
        p.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { onTrack(); changed() }
            override fun onIsPlayingChanged(isPlaying: Boolean) = changed()
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) { failures = 0; stuck = false }; changed() }
            override fun onPlayerError(error: PlaybackException) = skipBroken(error)
        })
        player = p
        ui.post(tick)
        return p
    }

    private fun onTrack() {
        val t = current ?: return
        if (casting) { castItems.getOrNull(index)?.let { Cast.showing(it, castItems.getOrNull(index + 1)) }; return }
        if (t === announced) return
        announced = t
        // The phone scrobbles what it casts.
        Music.client?.let { c -> worker.execute { c.scrobble(t.id, submission = false) } }
    }

    /** A track that will not play is skipped; three in a row stops trying. A casting phone decides for itself. */
    private fun skipBroken(error: PlaybackException) {
        Log.w(TAG, "track failed: ${error.errorCodeName}")
        val p = player ?: return
        failures++
        if (!casting && failures < 3 && p.hasNextMediaItem()) { p.seekToNextMediaItem(); p.prepare(); p.play() }
        else stuck = true
        changed()
    }

    fun length(): Long {
        val p = player ?: return 0L
        return p.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (current?.durationSec?.times(1000L) ?: 0L)
    }

    /** Scrobbles and tells a casting phone where playback is. */
    private val tick = object : Runnable {
        override fun run() {
            val p = player ?: return
            val pos = p.currentPosition.coerceAtLeast(0)
            val len = length()
            if (casting) Cast.report(transport(), pos, len)
            else {
                val t = current
                // Played at half the track or four minutes, the usual scrobble rule.
                if (t != null && !scanning && len > 0 && scrobbled !== t && (pos > len / 2 || pos > 4 * 60_000)) {
                    scrobbled = t
                    Music.client?.let { c -> worker.execute { c.scrobble(t.id, submission = true) } }
                }
            }
            ui.postDelayed(this, 500)
        }
    }

    private fun transport(): Transport {
        val p = player ?: return Transport.STOPPED
        return when {
            p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED -> Transport.STOPPED
            p.playbackState == Player.STATE_BUFFERING && p.playWhenReady -> Transport.TRANSITIONING
            p.playWhenReady || scanning -> Transport.PLAYING
            else -> Transport.PAUSED
        }
    }

    // --- Casting --------------------------------------------------------------------------------------

    override fun castLoad(current: CastItem, next: CastItem?) {
        val p = ensure()
        casting = true
        castItems.clear(); castItems += listOfNotNull(current, next)
        tracks.clear(); tracks += castItems.map { trackOf(it) }
        p.setMediaItems(castItems.map { MediaItem.fromUri(it.uri) }, 0, 0L)
        p.prepare()
        changed()
    }

    override fun castNext(next: CastItem?) {
        val p = player ?: return
        if (!casting) return
        val keep = p.currentMediaItemIndex + 1
        if (keep < castItems.size) {
            p.removeMediaItems(keep, castItems.size)
            castItems.subList(keep, castItems.size).clear()
            tracks.subList(keep, tracks.size).clear()
        }
        if (next != null) { castItems += next; tracks += trackOf(next); p.addMediaItem(MediaItem.fromUri(next.uri)) }
        changed()
    }

    override fun castPlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        p.play()
        if (!MusicPlayerActivity.showing) {
            // Needs "display over other apps" when Cinema is closed.
            val intent = Intent(app, MusicPlayerActivity::class.java)
            runCatching {
                // A new-task start would only bring Cinema's task forward.
                val task = app.getSystemService(ActivityManager::class.java).appTasks.firstOrNull()
                if (task != null) task.startActivity(app, intent, null)
                else app.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }.onFailure { Log.w(TAG, "could not open now playing", it) }
        }
    }

    override fun castPause() { player?.pause() }
    override fun castStop() { if (casting) stop() }
    override fun castSeek(positionMs: Long) { player?.seekTo(positionMs) }
    override fun castSkip(direction: Int) = skip(direction)

    fun skip(direction: Int) {
        val p = player ?: return
        if (direction > 0) { if (p.hasNextMediaItem()) p.seekToNextMediaItem() }
        else if (p.hasPreviousMediaItem()) p.seekToPreviousMediaItem() else p.seekTo(0)
    }

    /** A cast track as the screen shows it; the server's id when it streams from the same server, for lyrics. */
    private fun trackOf(item: CastItem): MusicTrack {
        val base = Music.client?.config?.baseUrl
        val id = if (base != null && item.uri.startsWith(base)) Uri.parse(item.uri).getQueryParameter("id") else null
        return MusicTrack(
            id.orEmpty(), item.title ?: "Unknown track", item.artist, item.album, null, null, null,
            item.durationMs?.let { (it / 1000).toInt() }, null, null
        )
    }

    private const val TAG = "CinemaMusic"
}
