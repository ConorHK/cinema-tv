package com.danielportelabyrne.cinema.data.movies

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinApi
import org.junit.Assert.*
import org.junit.Test

class DownloadsTest {
    private fun request(id: Int, type: String, status: Int, mediaStatus: Int, queue: String = "[]", tmdb: Long = 1119449) =
        """{"id":$id,"status":$status,"createdAt":"2026-10-20T14:21:50.000Z","type":"$type","media":{"downloadStatus":$queue,"mediaType":"$type","tmdbId":$tmdb,"status":$mediaStatus}}"""

    private fun list(vararg r: String) = """{"pageInfo":{"results":${r.size}},"results":[${r.joinToString(",")}]}"""

    @Test fun a_fresh_request_with_nothing_downloading_is_searching() {
        // exactly what Seerr returned for the first real request: approved, media processing, empty queue
        val r = SeerrRequests.parse(list(request(1, "movie", 2, 3))).single()
        assertEquals(Stage.SEARCHING, r.stage); assertEquals(Kind.MOVIE, r.kind); assertEquals(1119449L, r.tmdbId)
        assertNull(r.progress()); assertTrue(r.requestedAtMs!! > 0)
    }

    @Test fun progress_is_what_has_arrived_of_everything_being_fetched() {
        val queue = """[{"size":8000000000,"sizeLeft":6000000000,"estimatedCompletionTime":"2026-10-20T15:00:00.000Z"},
                       {"size":2000000000,"sizeLeft":0}]"""
        val r = SeerrRequests.parse(list(request(2, "tv", 2, 3, queue))).single()
        assertEquals(Stage.DOWNLOADING, r.stage); assertEquals(Kind.SHOW, r.kind)
        assertEquals(10_000_000_000L, r.totalBytes); assertEquals(6_000_000_000L, r.leftBytes)
        assertEquals(0.4f, r.progress()!!, 0.001f); assertNotNull(r.etaMs())
    }

    @Test fun reads_every_stage() {
        val stages = SeerrRequests.parse(list(request(1, "movie", 1, 2), request(2, "movie", 3, 1), request(3, "movie", 4, 1), request(4, "movie", 2, 5))).map { it.stage }
        assertEquals(listOf(Stage.WAITING_APPROVAL, Stage.DECLINED, Stage.FAILED, Stage.READY), stages)
        assertEquals(1f, SeerrRequests.parse(list(request(4, "movie", 2, 5))).single().progress()!!, 0f)
    }

    @Test fun ignores_junk_and_requests_without_a_title_id() {
        assertTrue(SeerrRequests.parse("nope").isEmpty())
        assertTrue(SeerrRequests.parse(list("""{"id":1,"status":2,"type":"movie","media":{"status":3}}""")).isEmpty())
    }

    @Test fun reads_free_space_and_title_info() {
        assertEquals(601_417_859_072L, SeerrRequests.parseFreeSpace("""{"server":{},"rootFolders":[{"id":1,"freeSpace":601417859072,"path":"/media/Movies"}]}"""))
        assertNull(SeerrRequests.parseFreeSpace("""{"rootFolders":[]}"""))
        val t = SeerrRequests.parseTitle("""{"title":"Good Luck, Have Fun, Don't Die","posterPath":"/abc.jpg"}""")!!
        assertEquals("Good Luck, Have Fun, Don't Die", t.first); assertEquals("https://image.tmdb.org/t/p/w185/abc.jpg", t.second)
        assertEquals("A Show", SeerrRequests.parseTitle("""{"name":"A Show"}""")!!.first)
    }

    @Test fun formats_sizes_short() {
        assertEquals("10.8 GB", ByteSize.format(10_760_000_000L)); assertEquals("601 GB", ByteSize.format(601_417_859_072L))
        assertEquals("1.2 TB", ByteSize.format(1_200_000_000_000L)); assertEquals("850 MB", ByteSize.format(850_000_000L))
    }

    @Test fun adds_up_the_library() {
        val json = """{"Items":[{"MediaSources":[{"Size":5000000000},{"Size":9}]},{"MediaSources":[{"Size":1500000000}]},{"Name":"no files"}]}"""
        assertEquals(6_500_000_000L, JellyfinApi.parseTotalBytes(json))
        assertEquals(0L, JellyfinApi.parseTotalBytes("junk"))
        assertTrue(JellyfinApi.sizesUrl("http://d", "u").contains("Movie,Episode"))
    }
}
