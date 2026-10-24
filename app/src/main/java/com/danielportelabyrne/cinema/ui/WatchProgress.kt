package com.danielportelabyrne.cinema.ui

/** How far the viewer has got with a film or an episode, from Jellyfin's user data. */
sealed class WatchState {
    object NotStarted : WatchState()
    /** [fraction] is 0..1 when the running time is known; [minutesLeft] likewise. */
    data class InProgress(val positionMs: Long, val fraction: Float?, val minutesLeft: Int?) : WatchState()
    object Watched : WatchState()
}

/** A button on a library film's page or the featured title, in order. */
enum class FilmAction { WATCH, CONTINUE, RESTART }

/** Pure rules for watch progress, kept apart from the views so they can be tested. */
object WatchProgress {
    fun of(played: Boolean, resumeMs: Long, runtimeMin: Int?): WatchState {
        if (played) return WatchState.Watched
        if (resumeMs <= 0) return WatchState.NotStarted
        val total = (runtimeMin ?: 0) * 60_000L
        if (total <= 0) return WatchState.InProgress(resumeMs, null, null)
        if (resumeMs >= total) return WatchState.Watched
        return WatchState.InProgress(resumeMs, resumeMs.toFloat() / total, Math.round((total - resumeMs) / 60_000.0).toInt().coerceAtLeast(1))
    }

    /** Not started (or finished): Watch now. Part-watched: Continue, then Restart. */
    fun filmActions(state: WatchState): List<FilmAction> =
        if (state is WatchState.InProgress) listOf(FilmAction.CONTINUE, FilmAction.RESTART) else listOf(FilmAction.WATCH)

    fun label(action: FilmAction, state: WatchState): String = when (action) {
        FilmAction.WATCH -> "Watch now"
        FilmAction.RESTART -> "Restart"
        FilmAction.CONTINUE -> (state as? WatchState.InProgress)?.minutesLeft?.let { "Continue · ${clock(it)} left" } ?: "Continue watching"
    }

    /** "42 min", or "1 h 05 min" past an hour. */
    fun clock(minutes: Int): String = if (minutes < 60) "$minutes min" else "${minutes / 60} h ${"%02d".format(minutes % 60)} min"

    /** An episode card's play disc: a tick once it has been watched (pressing it still plays it again). */
    fun episodeShowsTick(state: WatchState): Boolean = state == WatchState.Watched

    /** The bar along an episode still's foot: only while part-watched and its length is known. */
    fun episodeBar(state: WatchState): Float? = (state as? WatchState.InProgress)?.fraction?.takeIf { it > 0f }
}
