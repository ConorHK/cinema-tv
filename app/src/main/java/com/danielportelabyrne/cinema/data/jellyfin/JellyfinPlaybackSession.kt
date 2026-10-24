package com.danielportelabyrne.cinema.data.jellyfin

/** Orders reports and opens a new session if a stopped player is brought back and played again. */
class JellyfinPlaybackSession(private val nextId: () -> String = { java.util.UUID.randomUUID().toString().replace("-", "") }) {
    var id: String = nextId()
        private set
    var stopped: Boolean = false
        private set
    private var started = false

    fun accept(kind: String): Boolean = when (kind) {
        "Playing" -> if (started && !stopped) false else {
            if (stopped) id = nextId()
            started = true
            stopped = false
            true
        }
        "Progress" -> started && !stopped
        "Stopped" -> if (!started || stopped) false else { stopped = true; true }
        else -> false
    }
}
