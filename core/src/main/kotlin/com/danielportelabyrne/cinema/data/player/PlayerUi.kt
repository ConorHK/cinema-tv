package com.danielportelabyrne.cinema.data.player

/**
 * What the player's chrome shows, as a small state machine driven by the remote. Hidden: just the picture.
 * Controls (`31`): transport, wavy scrubber and chips. Scrubbing (`32`): a frame preview moving with left/right,
 * committed with OK, abandoned with Back. Paused (`33`): the rich information screen.
 */
sealed class PlayerUi {
    object Hidden : PlayerUi()
    object Controls : PlayerUi()
    /** [targetMs] is where OK would seek to; [returnTo] is where Back or OK leaves you. */
    data class Scrubbing(val targetMs: Long, val returnTo: PlayerUi) : PlayerUi()
    object Paused : PlayerUi()

    enum class Key { OK, LEFT, RIGHT, UP, DOWN, BACK, PLAY_PAUSE, PLAY, PAUSE }

    sealed class Action {
        object None : Action()
        object Play : Action()
        object Pause : Action()
        data class Seek(val ms: Long) : Action()
    }

    /** The next state, what the player should do, and whether the key was used (false lets Back leave the player, or lets focus move). */
    data class Step(val state: PlayerUi, val action: Action = Action.None, val consumed: Boolean = true)

    /**
     * [onScrubber] says focus sits on the scrubber (left/right there start scrubbing; elsewhere they move focus).
     * [repeat] is the key's repeat count, so holding left/right speeds the scrub up.
     */
    fun onKey(key: Key, repeat: Int, positionMs: Long, durationMs: Long, playing: Boolean, onScrubber: Boolean): Step {
        fun scrub(from: Long, dir: Int, back: PlayerUi) = Step(Scrubbing((from + dir * scrubStep(repeat)).coerceIn(0, durationMs.coerceAtLeast(0)), back))
        fun toggle(here: PlayerUi) = if (playing) Step(Paused, Action.Pause) else Step(if (here == Paused) Hidden else here, Action.Play)
        return when (this) {
            Hidden -> when (key) {
                Key.OK, Key.UP, Key.DOWN -> Step(Controls)
                Key.LEFT -> if (durationMs > 0) scrub(positionMs, -1, Hidden) else Step(Controls)
                Key.RIGHT -> if (durationMs > 0) scrub(positionMs, 1, Hidden) else Step(Controls)
                Key.BACK -> Step(Hidden, consumed = false)
                Key.PLAY_PAUSE -> toggle(Hidden)
                Key.PLAY -> Step(Hidden, Action.Play)
                Key.PAUSE -> Step(Paused, Action.Pause)
            }
            Controls -> when (key) {
                Key.LEFT -> if (onScrubber && durationMs > 0) scrub(positionMs, -1, Controls) else Step(Controls, consumed = false)
                Key.RIGHT -> if (onScrubber && durationMs > 0) scrub(positionMs, 1, Controls) else Step(Controls, consumed = false)
                Key.OK, Key.UP, Key.DOWN -> Step(Controls, consumed = false)
                Key.BACK -> Step(Hidden)
                Key.PLAY_PAUSE -> toggle(Controls)
                Key.PLAY -> Step(Controls, Action.Play)
                Key.PAUSE -> Step(Paused, Action.Pause)
            }
            is Scrubbing -> when (key) {
                Key.LEFT -> scrub(targetMs, -1, returnTo)
                Key.RIGHT -> scrub(targetMs, 1, returnTo)
                Key.OK, Key.PLAY_PAUSE, Key.PLAY -> Step(if (returnTo == Hidden) Hidden else Controls, Action.Seek(targetMs))
                Key.BACK -> Step(returnTo)
                Key.UP, Key.DOWN, Key.PAUSE -> Step(this)
            }
            Paused -> when (key) {
                Key.BACK -> Step(Controls)
                Key.PLAY_PAUSE, Key.PLAY -> Step(Hidden, Action.Play)
                Key.PAUSE -> Step(Paused)
                Key.OK, Key.LEFT, Key.RIGHT, Key.UP, Key.DOWN -> Step(Paused, consumed = false)
            }
        }
    }

    companion object {
        /** 10 s a press; held, 30 s and then 60 s a step. */
        fun scrubStep(repeat: Int): Long = when { repeat < 5 -> 10_000L; repeat < 15 -> 30_000L; else -> 60_000L }
    }
}
