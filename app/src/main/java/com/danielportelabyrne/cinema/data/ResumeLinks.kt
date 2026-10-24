package com.danielportelabyrne.cinema.data

import java.net.URLEncoder

/** Where a Continue watching card should take you: an address, and the app that should open it. */
data class ResumeLink(val uri: String, val packageName: String?)

/**
 * Turns what we know about a card into a deep link that opens the actual video or film, not just the app.
 * The identifiers are written into [ResumeItem.mediaId] as `youtube:<video id>` or `imdb:<film id>`.
 */
object ResumeLinks {
    private const val STREMIO = "com.stremio.one"

    fun youtubeId(mediaId: String?): String? = token(mediaId, "youtube:")
    fun imdbId(mediaId: String?): String? = token(mediaId, "imdb:")

    private fun token(mediaId: String?, prefix: String): String? =
        mediaId?.lines()?.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix)?.trim()?.takeIf { it.isNotEmpty() }

    fun target(item: ResumeItem): ResumeLink? {
        youtubeId(item.mediaId)?.let { id ->
            val seconds = item.positionMs / 1000
            // Resume where you left off, unless you were right at the start or had all but finished.
            val at = if (seconds >= 30 && (item.durationMs <= 0 || item.positionMs < item.durationMs * 0.97)) "&t=${seconds}s" else ""
            return ResumeLink("https://www.youtube.com/watch?v=$id$at", item.packageName)
        }
        if (item.packageName == STREMIO) {
            imdbId(item.mediaId)?.let { return ResumeLink("stremio:///detail/movie/$it/$it", STREMIO) }
            return ResumeLink("stremio:///search?search=" + URLEncoder.encode(item.title, "UTF-8"), STREMIO)
        }
        return null
    }

    /** Keeps an id we worked out earlier when a later update from the app carries none. */
    fun mergeIds(fresh: String?, existing: String?): String? {
        val known = existing?.lines()?.firstOrNull { it.startsWith("youtube:") || it.startsWith("imdb:") }
        val freshHasId = fresh?.lines()?.any { it.startsWith("youtube:") || it.startsWith("imdb:") } == true
        return if (freshHasId || known == null) fresh else listOfNotNull(known, fresh).joinToString("\n")
    }
}
