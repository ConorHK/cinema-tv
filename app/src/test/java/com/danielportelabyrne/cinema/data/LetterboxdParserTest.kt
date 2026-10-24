package com.danielportelabyrne.cinema.data

import org.junit.Assert.*
import org.junit.Test

class LetterboxdParserTest {
    @Test fun movie_request_id_comes_only_from_the_films_own_footer() {
        val page = """<a href="https://www.themoviedb.org/movie/999">Review link</a>
            <p class="text-link text-footer">More at <a href="https://www.themoviedb.org/movie/496243-parasite/">TMDB</a></p>"""
        assertEquals(496243L, LetterboxdParser.parse(page).tmdbId)
        assertNull(LetterboxdParser.movieId("""<a href="https://www.themoviedb.org/movie/999">Related movie</a>"""))
        assertNull(LetterboxdParser.movieId("""<p class="text-footer"><a href="https://www.themoviedb.org/tv/496243/">TMDB</a></p>"""))
    }

    @Test fun exact_movie_request_id_survives_cached_page_round_trip() {
        val page = FilmPage(tmdbId = 496243L)
        assertEquals(496243L, FilmPage.fromJson(page.toJson()).tmdbId)
        assertNull(FilmPage.fromJson(org.json.JSONObject()).tmdbId)
    }

    // A trimmed copy of the real markup of a Letterboxd film page.
    private val html = """
        <meta property="og:title" content="Pat and Mike (1952)">
        <meta name="twitter:label1" content="Directed by"><meta name="twitter:data1" content="George Cukor">
        <h4 class="tagline">Together again - and it&#039;s no fib</h4>
        <div class="truncate" data-truncate="450"> <p>Pat is a brilliant athlete.</p><p>Second &amp; last.</p> </div>
        <p class="text-link text-footer"> 95&nbsp;mins &nbsp;More at <a href="x">IMDb</a></p>
        <div class="cast-list text-sluglist">
          <a title="Mike Conovan" href="/actor/spencer-tracy/" class="text-slug tooltip">Spencer Tracy</a>
          <a title="Patricia "Pat" Pemberton" href="/actor/katharine-hepburn/" class="text-slug tooltip">Katharine Hepburn</a>
        </div>
        <div id="tab-panel-crew"><h3><span class="crewrole -full">Director</span><span class="crewrole -short">Director</span></h3>
          <div class="text-sluglist"><a href="/director/george-cukor/" class="text-slug">George Cukor</a></div>
          <h3><span class="crewrole -full">Writers</span></h3>
          <div class="text-sluglist"><a href="/w/a/" class="text-slug">Garson Kanin</a> <a href="/w/b/" class="text-slug">Ruth Gordon</a></div></div>
        <div id="tab-panel-details"><h3><span>Studio</span></h3><div class="text-sluglist"><a href="/s/" class="text-slug">Metro-Goldwyn-Mayer</a></div>
          <h3><span>Country</span></h3><div class="text-sluglist"><a href="/c/" class="text-slug"> USA </a></div>
          <h3><span>Language</span></h3><div class="text-sluglist"><a href="/l/" class="text-slug">English</a></div></div>
        <div id="tab-panel-genres"><h3><span>Genres</span></h3><div class="text-sluglist capitalize"><a href="/g/" class="text-slug">Romance</a><a href="/g2/" class="text-slug">Comedy</a></div></div>
    """.trimIndent()

    @Test fun parses_every_section() {
        val p = LetterboxdParser.parse(html)
        assertEquals(1952, p.year)
        assertEquals(95, p.runtimeMin)
        assertEquals("George Cukor", p.director)
        assertEquals("Together again - and it's no fib", p.tagline)
        assertEquals("Pat is a brilliant athlete.\n\nSecond & last.", p.synopsis)
        assertEquals(listOf("Spencer Tracy", "Katharine Hepburn"), p.cast.map { it.name })
        assertEquals("Mike Conovan", p.cast[0].role)
        assertEquals(listOf("Director", "Writers"), p.crew.map { it.role })
        assertEquals(listOf("Garson Kanin", "Ruth Gordon"), p.crew[1].names)
        assertEquals(listOf("Metro-Goldwyn-Mayer"), p.studios)
        assertEquals(listOf("USA"), p.countries)
        assertEquals(listOf("English"), p.languages)
        assertEquals(listOf("Romance", "Comedy"), p.genres)
        assertEquals("1952 · 95 min · Directed by George Cukor", p.headline())
    }

    @Test fun plural_headings_are_read_too() {
        val plural = """<div id="tab-panel-details"><h3><span>Studios</span></h3><div class="text-sluglist"><a href="/s/" class="text-slug">A Films</a><a href="/s2/" class="text-slug">B Films</a></div>
            <h3><span>Countries</span></h3><div class="text-sluglist"><a href="/c/" class="text-slug"> Australia </a><a href="/c2/" class="text-slug"> USA </a></div>
            <h3><span>Languages</span></h3><div class="text-sluglist"><a href="/l/" class="text-slug">English</a><a href="/l2/" class="text-slug">French</a></div></div>"""
        val p = LetterboxdParser.parse(plural)
        assertEquals(listOf("A Films", "B Films"), p.studios)
        assertEquals(listOf("Australia", "USA"), p.countries)
        assertEquals(listOf("English", "French"), p.languages)
    }

    @Test fun em_dashes_are_removed_from_film_text() {
        assertEquals("STEM, skills he uses", "STEM \u2014 skills he uses".withoutEmDashes())
        assertEquals("a, b", "a\u2014b".withoutEmDashes())
        val p = LetterboxdParser.parse("<h4 class=\"tagline\">Not man \u2014 not machine</h4>")
        assertEquals("Not man, not machine", p.tagline)
    }

    @Test fun empty_or_garbage_html_gives_an_empty_page_without_throwing() {
        val p = LetterboxdParser.parse("<html>nothing here</html>")
        assertNull(p.synopsis); assertTrue(p.cast.isEmpty()); assertEquals("", p.headline())
        LetterboxdParser.parse("")
    }

    @Test fun json_round_trip() {
        val p = LetterboxdParser.parse(html)
        assertEquals(p, FilmPage.fromJson(p.toJson()))
    }
}
