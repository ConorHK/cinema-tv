package com.danielportelabyrne.cinema.data.music

/**
 * Keeps a long-running screen from marking an OLED panel: the text and progress dim when nobody is using the
 * remote. Pure, so the timings can be tested.
 */
object BurnIn {
    const val IDLE_MS = 30_000L
    const val FADE_MS = 4_000L
    const val DIM = 0.6f

    /** How visible the text and progress are, [idleMs] after the last key press or track change. */
    fun chromeAlpha(idleMs: Long): Float = when {
        idleMs <= IDLE_MS -> 1f
        idleMs >= IDLE_MS + FADE_MS -> DIM
        else -> 1f - (1f - DIM) * (idleMs - IDLE_MS) / FADE_MS
    }
}
