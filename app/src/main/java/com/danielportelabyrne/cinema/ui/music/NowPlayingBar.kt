package com.danielportelabyrne.cinema.ui.music

import android.app.Activity
import android.graphics.Outline
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import com.danielportelabyrne.cinema.R
import com.danielportelabyrne.cinema.data.music.MusicTrack
import com.danielportelabyrne.cinema.design.Cinema
import com.danielportelabyrne.cinema.ui.RemoteImages

/**
 * What is playing, at the top of the Music pages while the library is browsed. OK opens now playing.
 * Hidden while nothing plays.
 */
class NowPlayingBar(private val activity: Activity) : LinearLayout(activity) {
    private val images = RemoteImages.shared(activity)
    private val cover = ImageView(activity).apply {
        scaleType = ImageView.ScaleType.CENTER_CROP
        setBackgroundColor(Cinema.alpha(Cinema.CREAM, 0.06f))
        outlineProvider = object : ViewOutlineProvider() { override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, Cinema.pxf(context, 10)) } }
        clipToOutline = true
    }
    private val icon = Cinema.icon(activity, R.drawable.ic_cin_play, Cinema.SAFFRON)
    private val state = Cinema.text(activity, "", 17, Cinema.SAFFRON, weight = 700, tracking = 0.18f, caps = true)
    private val name = Cinema.text(activity, "", 28, Cinema.CREAM, weight = 600).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val by = Cinema.text(activity, "", 22, Cinema.MUTED).apply { maxLines = 1; ellipsize = TextUtils.TruncateAt.END }
    private val rest = Cinema.glass(activity, 24f, Cinema.alpha(Cinema.CREAM, 0.05f))
    private val lit = Cinema.glass(activity, 24f, Cinema.CREAM)
    private var shown: MusicTrack? = null
    private val refresh: () -> Unit = { update() }
    var onFocus: ((Boolean) -> Unit)? = null

    init {
        orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        isFocusable = true; isClickable = true
        val pad = Cinema.px(activity, 18)
        setPadding(pad, pad, Cinema.px(activity, 36), pad)
        background = rest
        addView(cover, LayoutParams(Cinema.px(activity, 96), Cinema.px(activity, 96)))
        val column = LinearLayout(activity).apply { orientation = VERTICAL }
        val top = LinearLayout(activity).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(icon, LayoutParams(Cinema.px(activity, 18), Cinema.px(activity, 18)).apply { marginEnd = Cinema.px(activity, 10) })
        top.addView(state)
        column.addView(top)
        column.addView(name, LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 6) })
        column.addView(by, LayoutParams(-1, -2).apply { topMargin = Cinema.px(activity, 2) })
        addView(column, LayoutParams(0, -2, 1f).apply { marginStart = Cinema.px(activity, 28) })
        setOnFocusChangeListener { _, f -> paint(f); onFocus?.invoke(f) }
        setOnClickListener { MusicPlayerActivity.open(activity) }
        paint(false)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        MusicSession.listen(refresh)
        update()
    }

    override fun onDetachedFromWindow() {
        MusicSession.unlisten(refresh)
        super.onDetachedFromWindow()
    }

    private fun paint(focused: Boolean) {
        background = if (focused) lit else rest
        name.setTextColor(if (focused) Cinema.INK else Cinema.CREAM)
        by.setTextColor(if (focused) Cinema.INK else Cinema.MUTED)
    }

    private fun update() {
        val t = MusicSession.current
        visibility = if (t == null) GONE else VISIBLE
        if (t == null) { shown = null; return }
        val playing = MusicSession.player?.playWhenReady == true
        state.text = if (playing) "Now playing" else "Paused"
        icon.setImageResource(if (playing) R.drawable.ic_cin_play else R.drawable.ic_cin_pause)
        if (t === shown) return
        shown = t
        name.text = t.title
        by.text = listOfNotNull(t.artist, t.album).joinToString(" · ")
        cover.setImageDrawable(null)
        val key = if (MusicSession.casting) MusicSession.castItems.getOrNull(MusicSession.index)?.artUri else Music.cover(t.coverArt, 300)
        key?.let { images.load(it, maxWidth = Cinema.px(activity, 96)) { b -> if (shown === t) cover.setImageBitmap(b) } }
    }
}
