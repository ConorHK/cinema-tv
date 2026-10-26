package com.danielportelabyrne.cinema.data.jellyfin

/** Continue watching: part-watched titles, next episodes and new downloads together, newest first. */
object UpNext {
    const val NEW_FOR_MS = 14L * 24 * 3_600_000
    const val NEXT_FOR_MS = 60L * 24 * 3_600_000

    /** One card's item, and when it last mattered: played for a part-watched or next one, added for a new one. */
    data class Entry(val item: JellyfinItem, val atMs: Long, val isNew: Boolean, val isNext: Boolean = false) {
        /** Hiding lasts until the title is played again or more of it arrives. */
        val hideKey: String get() = "${item.seriesId ?: item.id}@$atMs"
    }

    /**
     * New downloads count for [NEW_FOR_MS]. A series already part-watched shows only that episode, even when its card
     * is hidden, so removing it keeps the series off the row. A series whose last episode was finished shows [nextUp]'s
     * episode, dated by that finish; otherwise its earliest new episode stands for a new batch.
     */
    fun merge(
        resume: List<JellyfinItem>, added: List<JellyfinItem>, nowMs: Long, hidden: Set<String> = emptySet(), limit: Int = 12,
        nextUp: List<JellyfinItem> = emptyList(), finished: List<JellyfinItem> = emptyList()
    ): List<Entry> {
        val going = resume.map { Entry(it, it.playedMs ?: 0L, isNew = false) }
        val busy = resume.map { it.seriesId ?: it.id }.toMutableSet()
        val lastFinished = finished.filter { it.seriesId != null && it.playedMs != null }
            .groupBy { it.seriesId!! }.mapValues { (_, eps) -> eps.maxOf { it.playedMs!! } }
        val next = nextUp
            .filter { !it.missing && !it.played && it.seriesId != null && it.seriesId !in busy }
            .distinctBy { it.seriesId }
            .mapNotNull { ep -> lastFinished[ep.seriesId]?.takeIf { it > nowMs - NEXT_FOR_MS }?.let { Entry(ep, it, isNew = false, isNext = true) } }
        busy += next.map { it.item.seriesId!! }
        val fresh = added
            .filter { !it.played && it.resumeMs == 0L && (it.addedMs ?: 0L) > nowMs - NEW_FOR_MS && (it.seriesId ?: it.id) !in busy }
            .groupBy { it.seriesId ?: it.id }
            .map { (_, items) ->
                val first = items.minWith(compareBy<JellyfinItem>({ it.season ?: 0 }, { it.episode ?: 0 }))
                Entry(first, items.maxOf { it.addedMs ?: 0L }, isNew = true)
            }
        return (going + next + fresh).filter { it.hideKey !in hidden }.sortedByDescending { it.atMs }.take(limit)
    }
}
