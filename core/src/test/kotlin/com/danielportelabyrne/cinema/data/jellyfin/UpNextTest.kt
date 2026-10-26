package com.danielportelabyrne.cinema.data.jellyfin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpNextTest {
    private val day = 24 * 3_600_000L
    private val now = 100 * day

    private fun film(id: String, played: Long? = null, added: Long? = null, resume: Long = 0) =
        JellyfinItem(id, id, 2000, "Movie", null, resumeMs = resume, playedMs = played, addedMs = added)

    private fun ep(id: String, series: String, s: Int, e: Int, added: Long) =
        JellyfinItem(id, id, null, "Episode", null, season = s, episode = e, seriesId = series, addedMs = added)

    @Test fun newDownloadsAndPartWatchedMixNewestFirst() {
        val out = UpNext.merge(listOf(film("a", played = now - 3 * day, resume = 60_000)), listOf(film("b", added = now - day)), now)
        assertEquals(listOf("b", "a"), out.map { it.item.id })
        assertEquals(listOf(true, false), out.map { it.isNew })
    }

    @Test fun oldDownloadsAreLeftOut() {
        assertEquals(emptyList<String>(), UpNext.merge(emptyList(), listOf(film("b", added = now - 30 * day)), now).map { it.item.id })
    }

    @Test fun aNewSeasonShowsOnceFromItsFirstEpisode() {
        val added = listOf(ep("e3", "s", 2, 3, now - day), ep("e1", "s", 2, 1, now - 2 * day), ep("e2", "s", 2, 2, now - day))
        val out = UpNext.merge(emptyList(), added, now)
        assertEquals(listOf("e1"), out.map { it.item.id })
        assertEquals(now - day, out.single().atMs)
    }

    @Test fun aSeriesBeingWatchedKeepsItsResumeCardOnly() {
        val going = ep("e1", "s", 1, 1, now - 9 * day).copy(resumeMs = 60_000, playedMs = now - 5 * day)
        val out = UpNext.merge(listOf(going), listOf(ep("e2", "s", 1, 2, now - day)), now)
        assertEquals(listOf("e1"), out.map { it.item.id })
    }

    @Test fun aFinishedEpisodeBringsTheNextOne() {
        val done = ep("e1", "s", 1, 1, now - 9 * day).copy(played = true, playedMs = now - 2 * day)
        val out = UpNext.merge(emptyList(), listOf(ep("e2", "s", 1, 2, now - day)), now, nextUp = listOf(ep("e2", "s", 1, 2, now - 9 * day)), finished = listOf(done))
        assertEquals(listOf("e2"), out.map { it.item.id })
        assertEquals(true, out.single().isNext)
        assertEquals(now - 2 * day, out.single().atMs)
    }

    @Test fun aRemovedEpisodeKeepsItsSeriesOff() {
        val going = ep("e1", "s", 1, 1, now - 9 * day).copy(resumeMs = 60_000, playedMs = now - day)
        val key = UpNext.merge(listOf(going), emptyList(), now).single().hideKey
        val done = ep("e0", "s", 1, 0, now - 9 * day).copy(played = true, playedMs = now - 3 * day)
        val out = UpNext.merge(listOf(going), listOf(ep("e2", "s", 1, 2, now - day)), now, setOf(key),
            nextUp = listOf(ep("e2", "s", 1, 2, now - day)), finished = listOf(done))
        assertEquals(emptyList<String>(), out.map { it.item.id })
    }

    @Test fun hiddenStaysHiddenUntilItChanges() {
        val a = film("a", played = now - day, resume = 60_000)
        val key = UpNext.merge(listOf(a), emptyList(), now).single().hideKey
        assertEquals(0, UpNext.merge(listOf(a), emptyList(), now, setOf(key)).size)
        assertEquals(1, UpNext.merge(listOf(a.copy(playedMs = now)), emptyList(), now, setOf(key)).size)
    }

    @Test fun qualityReadsWidthAndRange() {
        fun source(w: Int, h: Int, range: String) = JSONObject("""{"MediaStreams":[{"Type":"Audio"},{"Type":"Video","Width":$w,"Height":$h,"VideoRangeType":"$range"}]}""")
        assertEquals("4K · Dolby Vision", JellyfinApi.videoQuality(source(3840, 1600, "DOVIWithHDR10")))
        assertEquals("1080p", JellyfinApi.videoQuality(source(1920, 800, "SDR")))
        assertEquals("720p · HDR10", JellyfinApi.videoQuality(source(1280, 720, "HDR10")))
        assertNull(JellyfinApi.videoQuality(JSONObject("{}")))
    }
}
