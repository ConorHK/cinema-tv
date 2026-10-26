package com.danielportelabyrne.cinema.data.jellyfin

/** Continue watching: part-watched titles and new downloads together, newest first. */
object UpNext {
    const val NEW_FOR_MS = 14L * 24 * 3_600_000

    /** One card's item, and when it last mattered: played for a part-watched one, added for a new one. */
    data class Entry(val item: JellyfinItem, val atMs: Long, val isNew: Boolean) {
        /** Hiding lasts until the title is played again or more of it arrives. */
        val hideKey: String get() = "${item.seriesId ?: item.id}@$atMs"
    }

    /**
     * New downloads count for [NEW_FOR_MS]. A series already part-watched shows only that episode; otherwise its
     * earliest new episode stands for the batch.
     */
    fun merge(resume: List<JellyfinItem>, added: List<JellyfinItem>, nowMs: Long, hidden: Set<String> = emptySet(), limit: Int = 12): List<Entry> {
        val going = resume.map { Entry(it, it.playedMs ?: 0L, isNew = false) }
        val busy = resume.map { it.seriesId ?: it.id }.toSet()
        val fresh = added
            .filter { !it.played && it.resumeMs == 0L && (it.addedMs ?: 0L) > nowMs - NEW_FOR_MS && (it.seriesId ?: it.id) !in busy }
            .groupBy { it.seriesId ?: it.id }
            .map { (_, items) ->
                val first = items.minWith(compareBy<JellyfinItem>({ it.season ?: 0 }, { it.episode ?: 0 }))
                Entry(first, items.maxOf { it.addedMs ?: 0L }, isNew = true)
            }
        return (going + fresh).filter { it.hideKey !in hidden }.sortedByDescending { it.atMs }.take(limit)
    }
}
