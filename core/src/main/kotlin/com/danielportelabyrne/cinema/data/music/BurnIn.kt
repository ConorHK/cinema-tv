package com.danielportelabyrne.cinema.data.music

import kotlin.math.PI
import kotlin.math.sin

/**
 * Keeps a long-running screen from marking an OLED panel: the whole layout wanders a few pixels on a slow loop, and
 * the text and progress dim when nobody is using the remote. Pure, so the timings can be tested.
 */
object BurnIn {
    const val IDLE_MS = 30_000L
    const val FADE_MS = 4_000L
    const val DIM = 0.6f
    private const val PERIOD_X_MS = 7 * 60_000.0
    private const val PERIOD_Y_MS = 11 * 60_000.0

    /** Where the layout sits at [elapsedMs], within [range] pixels of centre on each axis. */
    fun drift(elapsedMs: Long, range: Float): Pair<Float, Float> =
        (range * sin(2 * PI * elapsedMs / PERIOD_X_MS)).toFloat() to (range * sin(2 * PI * elapsedMs / PERIOD_Y_MS)).toFloat()

    /** How visible the text and progress are, [idleMs] after the last key press or track change. */
    fun chromeAlpha(idleMs: Long): Float = when {
        idleMs <= IDLE_MS -> 1f
        idleMs >= IDLE_MS + FADE_MS -> DIM
        else -> 1f - (1f - DIM) * (idleMs - IDLE_MS) / FADE_MS
    }

    /** The art swaps sides each track, so no edge stays lit. */
    fun artOnLeft(trackIndex: Int): Boolean = trackIndex % 2 == 0
}
