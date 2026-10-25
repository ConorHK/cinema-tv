package com.danielportelabyrne.cinema.desktop.ui

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem

/** Which picture each card uses. An episode borrows its series' poster and backdrop. */
class Art(private val server: () -> String) {
    private val base get() = JellyfinApi.cleanBase(server())

    fun poster(item: JellyfinItem): String = JellyfinApi.posterUrl(base, if (item.isEpisode) item.seriesId ?: item.id else item.id, 450)

    fun wide(item: JellyfinItem): String = when {
        item.isEpisode && item.hasPrimaryImage -> JellyfinApi.episodeImageUrl(base, item.id, 640)
        else -> JellyfinApi.backdropUrl(base, item.seriesId ?: item.id, 640)
    }

    fun backdrop(item: JellyfinItem): String = JellyfinApi.backdropUrl(base, item.seriesId ?: item.id, 1280)
}
