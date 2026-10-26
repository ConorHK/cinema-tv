package com.danielportelabyrne.cinema.ui.music

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Outline
import android.graphics.Paint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
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
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.danielportelabyrne.cinema.data.music.BurnIn
import com.danielportelabyrne.cinema.data.music.MusicTrack
import com.danielportelabyrne.cinema.data.music.Spectrum
import com.danielportelabyrne.cinema.data.music.SubsonicClient
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.ui.RemoteImages
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors

/**
 * Full-screen now playing for a queue of tracks from the music server. Built to sit on an OLED panel for hours: the
 * layout drifts, the art swaps sides each track, the words dim when the remote is idle, and the spectrum below has
 * no fixed line. Paused, the screen may sleep. Leaving the screen stops the music.
 *
 * Remote: OK plays or pauses, Left and Right step through the queue, rewind and fast forward seek, Back leaves.
 */
@UnstableApi
class MusicPlayerActivity : Activity() {
    private lateinit var client: SubsonicClient
    private lateinit var tracks: List<MusicTrack>
    private lateinit var player: ExoPlayer
    private val ui = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val images by lazy { RemoteImages.shared(this) }

    private val spectrum = Spectrum(BANDS)
    private val levels = FloatArray(BANDS)
    private val tap = Tap()

    private lateinit var stage: LinearLayout
    private lateinit var art: ImageView
    private lateinit var words: LinearLayout
    private lateinit var eyebrow: TextView
    private lateinit var title: TextView
    private lateinit var byline: TextView
    private lateinit var progress: Progress
    private lateinit var times: TextView
    private lateinit var upNext: TextView
    private lateinit var bars: SpectrumView

    private val opened = SystemClock.elapsedRealtime()
    private var lastInput = opened
    private var shownIndex = -1
    private var scrobbled = -1
    private var failures = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val queue = Music.queued
        val c = Music.client
        if (queue == null || c == null || queue.tracks.isEmpty()) { finish(); return }
        client = c; tracks = queue.tracks
        buildViews()

        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context).setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf(TeeAudioProcessor(tap))).build()
        }.setEnableDecoderFallback(true).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        val base = client.config.baseUrl
        val signing = ResolvingDataSource.Factory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)) { spec ->
            val uri = spec.uri.toString()
            if (uri.startsWith(base)) spec.withUri(Uri.parse(client.signed(uri.removePrefix(base)))) else spec
        }
        player = ExoPlayer.Builder(this, renderers).setMediaSourceFactory(DefaultMediaSourceFactory(signing)).build()
        player.setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(), true)
        player.setMediaItems(tracks.map { item(it) }, queue.start.coerceIn(0, tracks.lastIndex), 0L)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { showTrack() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { keepAwake() }
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) failures = 0; keepAwake() }
            override fun onPlayerError(error: PlaybackException) { skipBroken(error) }
        })
        player.prepare(); player.play()
        showTrack()
        ui.post(tick)
    }

    private fun item(t: MusicTrack): MediaItem = MediaItem.Builder()
        .setUri(client.config.baseUrl + client.streamPath(t.id)).setMediaId(t.id)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build())
        .build()

    // --- Layout ---------------------------------------------------------------------------------------

    private fun px(mock: Int) = Cinema.px(this, mock)

    private fun buildViews() {
        val root = FrameLayout(this).apply { setBackgroundColor(Cinema.BG); clipChildren = false }
        bars = SpectrumView(this, BANDS)
        root.addView(bars, FrameLayout.LayoutParams(-1, px(360), Gravity.BOTTOM).apply { setMargins(px(160), 0, px(160), px(70)) })

        stage = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        art = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.06f))
            outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 20)) } }
            clipToOutline = true
        }
        words = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        eyebrow = Cinema.text(this, "", 17, Cinema.SAFFRON, weight = 700, tracking = 0.2f, caps = true)
        title = Cinema.text(this, "", 64, Cinema.CREAM, serif = true, weight = 500).apply { maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        byline = Cinema.text(this, "", 28, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        progress = Progress(this)
        times = Cinema.text(this, "", 20, Cinema.DIM)
        upNext = Cinema.text(this, "", 20, Cinema.DIM).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
        words.addView(eyebrow)
        words.addView(title, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(18) })
        words.addView(byline, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        words.addView(progress, LinearLayout.LayoutParams(-1, px(6)).apply { topMargin = px(44) })
        words.addView(times, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(12) })
        words.addView(upNext, LinearLayout.LayoutParams(-1, -2).apply { topMargin = px(28) })
        root.addView(stage, FrameLayout.LayoutParams(-1, -2, Gravity.TOP).apply { setMargins(px(200), px(150), px(200), 0) })
        setContentView(root)
    }

    /** Art and words, with the art on the side [BurnIn.artOnLeft] picks for this track. */
    private fun arrange(index: Int) {
        stage.removeAllViews()
        val artParams = LinearLayout.LayoutParams(px(500), px(500))
        val wordParams = LinearLayout.LayoutParams(0, -2, 1f)
        if (BurnIn.artOnLeft(index)) {
            stage.addView(art, artParams); stage.addView(words, wordParams.apply { marginStart = px(90) })
            words.gravity = Gravity.START
        } else {
            stage.addView(words, wordParams.apply { marginEnd = px(90) }); stage.addView(art, artParams)
            words.gravity = Gravity.END
        }
        listOf(eyebrow, title, byline, times, upNext).forEach { it.gravity = if (BurnIn.artOnLeft(index)) Gravity.START else Gravity.END }
    }

    private fun showTrack() {
        val i = player.currentMediaItemIndex
        val t = tracks.getOrNull(i) ?: return
        if (i != shownIndex) {
            shownIndex = i; arrange(i)
            synchronized(spectrum) { spectrum.reset() }
            lastInput = SystemClock.elapsedRealtime()
            art.setImageDrawable(null)
            Music.cover(t.coverArt, 800)?.let { key -> images.load(key, maxWidth = px(500)) { b -> if (shownIndex == i) art.setImageBitmap(b) } }
            worker.execute { client.scrobble(t.id, submission = false) }
        }
        eyebrow.text = if (tracks.size > 1) "Now playing  \u00b7  ${i + 1} of ${tracks.size}" else "Now playing"
        title.text = t.title
        byline.text = listOfNotNull(t.artist, t.album).joinToString("  \u00b7  ")
        upNext.text = tracks.getOrNull(i + 1)?.let { "Next: ${it.title}" + (it.artist?.let { a -> " \u00b7 $a" } ?: "") }.orEmpty()
    }

    // --- The loop -------------------------------------------------------------------------------------

    private val tick = object : Runnable {
        override fun run() {
            val now = SystemClock.elapsedRealtime()
            synchronized(spectrum) {
                if (!player.isPlaying) spectrum.decay()
                System.arraycopy(spectrum.levels, 0, levels, 0, BANDS)
            }
            bars.show(levels)
            val (dx, dy) = BurnIn.drift(now - opened, px(DRIFT).toFloat())
            stage.translationX = dx; stage.translationY = dy
            bars.translationX = -dx; bars.translationY = dy
            val chrome = BurnIn.chromeAlpha(now - lastInput)
            words.alpha = chrome
            art.alpha = 0.4f + 0.6f * chrome
            updateProgress()
            ui.postDelayed(this, FRAME_MS)
        }
    }

    private fun updateProgress() {
        val pos = player.currentPosition.coerceAtLeast(0)
        val len = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: (tracks.getOrNull(shownIndex)?.durationSec?.times(1000L) ?: 0L)
        progress.fraction = if (len > 0) pos.toFloat() / len else 0f
        times.text = if (len > 0) "${clock(pos)}  /  ${clock(len)}" else clock(pos)
        // Counts as played at half the track or four minutes, the usual scrobble rule.
        if (len > 0 && scrobbled != shownIndex && (pos > len / 2 || pos > 4 * 60_000)) {
            scrobbled = shownIndex
            tracks.getOrNull(shownIndex)?.let { t -> worker.execute { client.scrobble(t.id, submission = true) } }
        }
    }

    private fun clock(ms: Long): String { val s = ms / 1000; return "%d:%02d".format(s / 60, s % 60) }

    private fun keepAwake() {
        if (player.playWhenReady && player.playbackState != Player.STATE_ENDED) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** A track that will not play is skipped; three in a row stops trying. */
    private fun skipBroken(error: PlaybackException) {
        android.util.Log.w(TAG, "track failed: ${error.errorCodeName}")
        failures++
        if (failures < 3 && player.hasNextMediaItem()) { player.seekToNextMediaItem(); player.prepare(); player.play() }
        else { upNext.text = "Couldn't play this track."; lastInput = SystemClock.elapsedRealtime(); keepAwake() }
    }

    // --- Remote ---------------------------------------------------------------------------------------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) lastInput = SystemClock.elapsedRealtime()
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE ->
                if (event.repeatCount == 0) { if (player.playbackState == Player.STATE_ENDED) { player.seekTo(0, 0L); player.play() } else if (player.isPlaying) player.pause() else player.play() }
            KeyEvent.KEYCODE_MEDIA_PLAY -> player.play()
            KeyEvent.KEYCODE_MEDIA_PAUSE -> player.pause()
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_NEXT -> if (event.repeatCount == 0 && player.hasNextMediaItem()) player.seekToNextMediaItem()
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (event.repeatCount == 0) player.seekToPrevious()
            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> player.seekTo(player.currentPosition + SEEK_MS)
            KeyEvent.KEYCODE_MEDIA_REWIND -> player.seekTo((player.currentPosition - SEEK_MS).coerceAtLeast(0))
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> Unit
            else -> return super.onKeyDown(keyCode, event)
        }
        return true
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) player.pause()
    }

    override fun onDestroy() {
        ui.removeCallbacks(tick)
        if (::player.isInitialized) player.release()
        worker.shutdown()
        super.onDestroy()
    }

    /** Decoded audio on its way out, mixed to mono for [spectrum]. Runs on the playback thread. */
    private inner class Tap : TeeAudioProcessor.AudioBufferSink {
        private var rate = 0
        private var channels = 0
        private var encoding = C.ENCODING_INVALID
        private var mono = FloatArray(4096)

        override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
            rate = sampleRateHz; channels = channelCount; this.encoding = encoding
            synchronized(spectrum) { spectrum.reset() }
        }

        override fun handleBuffer(buffer: ByteBuffer) {
            val ch = channels.takeIf { it > 0 } ?: return
            val b = buffer.duplicate().order(ByteOrder.nativeOrder())
            val bytes = when (encoding) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_FLOAT -> 4; else -> return }
            val frames = b.remaining() / (bytes * ch)
            if (frames == 0) return
            if (mono.size < frames) mono = FloatArray(frames)
            for (f in 0 until frames) {
                var sum = 0f
                for (c in 0 until ch) sum += if (bytes == 2) b.short / 32768f else b.float
                mono[f] = sum / ch
            }
            synchronized(spectrum) { spectrum.feed(mono, frames, rate) }
        }
    }

    /** A thin rounded bar: the played part in cream, the rest faint. */
    private class Progress(context: Context) : View(context) {
        var fraction = 0f
            set(v) { val c = v.coerceIn(0f, 1f); if (c != field) { field = c; invalidate() } }
        private val rest = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cinema.alpha(Cinema.CREAM, 0.14f) }
        private val done = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Cinema.alpha(Cinema.CREAM, 0.7f) }

        override fun onDraw(canvas: Canvas) {
            val r = height / 2f
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, rest)
            if (fraction > 0f) canvas.drawRoundRect(0f, 0f, width * fraction, height.toFloat(), r, r, done)
        }
    }

    companion object {
        private const val TAG = "CinemaMusic"
        private const val BANDS = 48
        private const val FRAME_MS = 33L
        private const val DRIFT = 28
        private const val SEEK_MS = 10_000L

        fun launch(activity: Activity, tracks: List<MusicTrack>, start: Int) {
            if (tracks.isEmpty() || Music.client == null) return
            Music.queued = Music.Queue(tracks, start)
            activity.startActivity(Intent(activity, MusicPlayerActivity::class.java))
        }
    }
}
