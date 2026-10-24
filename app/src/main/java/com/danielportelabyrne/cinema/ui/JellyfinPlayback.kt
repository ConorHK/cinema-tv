package com.danielportelabyrne.cinema.ui

import android.app.Activity
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinClient
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import com.danielportelabyrne.cinema.data.jellyfin.JellyfinReport
import com.danielportelabyrne.cinema.data.stream.RankedStream
import com.danielportelabyrne.cinema.data.stream.StreamCandidate
import com.danielportelabyrne.cinema.data.stream.StreamMetadata
import com.danielportelabyrne.cinema.data.stream.Verdict

/** Plays something from the owner's Jellyfin in the app's own player, and reports how far the viewer gets. */
object JellyfinPlayback {
    data class Plan(val streams: List<RankedStream>, val title: String, val resumeKey: String, val report: JellyfinReport)
    /**
     * The file as stored first, then a copy Jellyfin re-encodes for this TV, which the player falls back to on its
     * own. [resumeKey] keeps a separate "where I got to" for each film or episode. Playback starts where Jellyfin says
     * the viewer left off, and its progress is sent back so the server's "continue watching" stays right.
     */
    fun play(activity: Activity, client: JellyfinClient, item: JellyfinItem, title: String, resumeKey: String, fromStart: Boolean = false) {
        Perf.mark("library.play")
        val plan = prepare(client, item, title, resumeKey)
        // The viewer's place will have moved by the time they come back, so the cached library must be asked again.
        client.invalidateLibrary()
        // Restart: from 0:00, whatever the server or this TV remembered.
        val report = if (fromStart) plan.report.copy(startMs = 0L) else plan.report
        PlayerActivity.launch(activity, plan.streams, plan.title, plan.resumeKey, report, fromStart = fromStart)
    }

    /** Builds the next playback locally, so the countdown never waits on an extra item lookup. */
    fun prepare(client: JellyfinClient, item: JellyfinItem, title: String, resumeKey: String): Plan {
        val headers = client.playbackHeaders()
        fun ranked(label: String, url: String) = RankedStream(
            StreamCandidate("jellyfin:${item.id}:$label", "Jellyfin", label, url, null, StreamMetadata(), headers = headers),
            100, 0L, false, Verdict.RECOMMENDED, null, emptyList()
        )
        val report = JellyfinReport(JellyfinApi.cleanBase(client.server), item.id, item.sourceId, if (item.played) 0L else item.resumeMs)
        return Plan(listOf(ranked("Original file", client.streamUrl(item)), ranked("Converted for this TV", client.transcodeUrl(item))), title, resumeKey, report)
    }
}
