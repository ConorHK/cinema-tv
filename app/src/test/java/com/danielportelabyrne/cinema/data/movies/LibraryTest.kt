package com.danielportelabyrne.cinema.data.movies

import org.junit.Assert.*
import org.junit.Test

class LibraryTest {
    private val list = """[{"id":3,"tmdbId":438631,"imdbId":"tt1160419","title":"Dune","year":2021},{"id":9,"tmdbId":1402,"imdbId":"tt1520211","title":"The Walking Dead","year":2010}]"""

    @Test fun finds_a_title_by_tmdb_id_first() {
        assertEquals(9, LibraryApi.findId(list, LibraryRef("Something else", null, 1402L, null)))
    }

    @Test fun falls_back_to_imdb_id_then_to_name_and_year() {
        assertEquals(3, LibraryApi.findId(list, LibraryRef("Dune", 2021, null, "TT1160419")))
        assertEquals(9, LibraryApi.findId(list, LibraryRef("the walking dead", 2010, null, null)))
        assertNull(LibraryApi.findId(list, LibraryRef("the walking dead", 1999, null, null)))   // same name, different year: not the same title
    }

    @Test fun finds_nothing_when_nothing_matches_or_the_list_is_junk() {
        assertNull(LibraryApi.findId(list, LibraryRef("Unknown", null, 7L, null)))
        assertNull(LibraryApi.findId("junk", LibraryRef("Dune", 2021, 1402L, null)))
    }

    @Test fun deleting_removes_files_and_the_entry_so_nothing_is_fetched_again() {
        assertEquals("/api/v3/series/12?deleteFiles=true&addImportListExclusion=false", LibraryApi.deletePath(Kind.SHOW, 12))
        assertEquals("/api/v3/movie/5?deleteFiles=true&addImportExclusion=false", LibraryApi.deletePath(Kind.MOVIE, 5))
    }
}
