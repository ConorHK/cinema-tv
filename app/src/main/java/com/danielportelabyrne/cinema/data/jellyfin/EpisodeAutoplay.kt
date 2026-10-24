package com.danielportelabyrne.cinema.data.jellyfin

/** Monotonic countdown: cancellation survives a late lookup and each episode advances at most once. */
class EpisodeAutoplay(private val durationMs: Long = 3_000L) {
    var nextId: String? = null
        private set
    var ended = false
        private set
    var cancelled = false
        private set
    private var deadline: Long? = null
    private var consumed = false

    fun ready(id: String, now: Long) {
        if (consumed) return
        nextId = id
        if (ended && !cancelled && deadline == null) deadline = now + durationMs
    }

    fun end(now: Long) {
        ended = true
        if (nextId != null && !cancelled && !consumed && deadline == null) deadline = now + durationMs
    }

    fun cancel() { cancelled = true; deadline = null }

    fun remainingMs(now: Long): Long? = deadline?.let { (it - now).coerceIn(0L, durationMs) }
    fun fraction(now: Long): Float = remainingMs(now)?.let { it.toFloat() / durationMs } ?: 0f

    fun takeNext(now: Long, playNow: Boolean = false): String? {
        val id = nextId ?: return null
        if (!ended || consumed || (!playNow && (cancelled || remainingMs(now) != 0L))) return null
        consumed = true
        deadline = null
        return id
    }
}
