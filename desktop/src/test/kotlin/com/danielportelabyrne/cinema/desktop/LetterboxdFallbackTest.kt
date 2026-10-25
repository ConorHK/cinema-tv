package com.danielportelabyrne.cinema.data.letterboxd

import org.junit.Assert.*
import org.junit.Test

class LetterboxdFallbackTest {
    @Test fun oldDiaryAndReviewLinksUseTheCurrentPublicRoutes() {
        assertEquals("/someone/diary/", LetterboxdWeb.canonicalPath("/someone/films/diary/"))
        assertEquals("/someone/reviews/", LetterboxdWeb.canonicalPath("/someone/films/reviews/"))
        assertEquals("/someone/list/favourites/", LetterboxdWeb.canonicalPath("/someone/list/favourites/"))
        assertEquals("/someone/films/", LetterboxdWeb.publicFallback("/someone/"))
        assertEquals("/someone/rss/", LetterboxdWeb.publicFallback("/someone/diary/"))
        assertNull(LetterboxdWeb.publicFallback("/search/lists/drama/"))
    }

    @Test fun thePublicFilmPageHeaderCanIdentifyAMember() {
        val html = """<section class="profile-header -is-mini-nav"><div class="profile-mini-person">
            <a class="avatar"><img src="https://example.org/avatar.jpg?a=1&amp;b=2" /></a>
            <h1 class="title-3"><a href="/someone/">Some One</a></h1></div></section>"""
        val p = DesktopLetterboxd.profile(html, "someone")!!
        assertEquals("Some One", p.name)
        assertEquals("https://example.org/avatar.jpg?a=1&b=2", p.avatar)
        assertTrue(p.favourites.isEmpty())
        assertNull(DesktopLetterboxd.profile("<title>Just a moment...</title>", "someone"))
    }

    @Test fun rssPreservesFilmNamesRatingsDatesAndRewatches() {
        val rss = """<?xml version="1.0"?><rss xmlns:letterboxd="https://letterboxd.com"><channel><item>
            <link>https://letterboxd.com/someone/film/example-film/2/</link>
            <letterboxd:filmTitle>A Film &amp; Its Sequel</letterboxd:filmTitle>
            <letterboxd:filmYear>2024</letterboxd:filmYear><letterboxd:memberRating>3.5</letterboxd:memberRating>
            <letterboxd:watchedDate>2026-10-20</letterboxd:watchedDate><letterboxd:memberLike>Yes</letterboxd:memberLike>
            <letterboxd:rewatch>Yes</letterboxd:rewatch></item><item><title>Not a film</title></item></channel></rss>"""
        val entry = DesktopLetterboxd.diary(rss).single()
        assertEquals("example-film", entry.film.slug)
        assertEquals("A Film & Its Sequel", entry.film.name)
        assertEquals(7, entry.rating)
        assertEquals("2026-10-20", entry.date)
        assertTrue(entry.liked && entry.rewatch)
    }
}
