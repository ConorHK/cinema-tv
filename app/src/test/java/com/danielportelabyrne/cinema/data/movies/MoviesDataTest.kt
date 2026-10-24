package com.danielportelabyrne.cinema.data.movies

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class MoviesDataTest {
    private val answer = """{"data":{"popularTitles":{"edges":[
      {"node":{"id":"tm1","objectType":"MOVIE","content":{"title":"Doctor Sleep","originalReleaseYear":2019,"shortDescription":"Danny.","runtime":152,
        "genres":[{"translation":"Horror"},{"translation":"Drama"}],"ageCertification":"15","posterUrl":"/poster/1/{profile}/doctor-sleep.{format}",
        "backdrops":[{"backdropUrl":"/backdrop/2/s1440/doctor-sleep.jpg"}],"externalIds":{"imdbId":"tt5606664","tmdbId":"501170"},"scoring":{"imdbScore":7.3}}}},
      {"node":{"id":"ts2","objectType":"SHOW","content":{"title":"TYSON","originalReleaseYear":2026,"runtime":66,"genres":[],"ageCertification":"",
        "posterUrl":"","backdrops":[],"externalIds":{"imdbId":"","tmdbId":"335536"}}}},
      {"node":{"id":"tm1b","objectType":"MOVIE","content":{"title":"Doctor Sleep","originalReleaseYear":2019,"externalIds":{"imdbId":"tt5606664","tmdbId":"501170"}}}},
      {"node":{"id":"bad","objectType":"MOVIE","content":{"title":""}}}
    ]}}}"""

    @Test fun reads_titles_with_ids_images_and_meta() {
        val t = Catalog.parse(answer)
        assertEquals(3, t.size)   // the nameless one is dropped
        with(t[0]) {
            assertEquals(Kind.MOVIE, kind); assertEquals("tt5606664", imdbId); assertEquals("501170", tmdbId)
            assertEquals("https://images.justwatch.com/poster/1/s332/doctor-sleep.jpg", posterUrl)
            assertEquals("https://images.justwatch.com/backdrop/2/s1440/doctor-sleep.jpg", backdropUrl)
            assertEquals(7.3, imdbScore!!, 0.001)
            assertEquals("2019  ·  15  ·  2h 32m  ·  Horror, Drama", metaLine())
        }
        assertEquals(Kind.SHOW, t[1].kind); assertNull(t[1].imdbId); assertNull(t[1].posterUrl); assertNull(t[1].age)
    }

    @Test fun one_film_on_two_services_appears_once() {
        assertEquals(2, Catalog.dedupe(Catalog.parse(answer)).size)
    }

    @Test fun bad_answers_are_an_empty_shelf() {
        assertTrue(Catalog.parse("nope").isEmpty())
        assertTrue(Catalog.parse("""{"data":{}}""").isEmpty())
    }

    @Test fun builds_a_request_for_a_shelf() {
        Catalog.country = "GB"
        val docs = Catalog.documentariesPage()[1]
        val body = JSONObject(Catalog.requestBody(docs))
        assertTrue(body.getString("query").contains("sortBy: POPULAR"))
        val f = body.getJSONObject("variables").getJSONObject("f")
        assertEquals("doc", f.getJSONArray("genres").getString(0))
        assertEquals(6, f.getJSONArray("packages").length())
        assertEquals("MOVIE", f.getJSONArray("objectTypes").getString(0))
        assertEquals("GB", body.getJSONObject("variables").getString("c"))
        Catalog.country = "DE"
        assertEquals("DE", JSONObject(Catalog.requestBody(docs)).getJSONObject("variables").getString("c"))
        assertTrue(JSONObject(Catalog.requestBody(Catalog.homePage()[0])).getString("query").contains("sortBy: TRENDING"))
    }

    @Test fun search_asks_for_anything_not_just_what_streams() {
        val f = JSONObject(Catalog.requestBody(Catalog.searchSpecs("sleep")[0])).getJSONObject("variables").getJSONObject("f")
        assertEquals("sleep", f.getString("searchQuery")); assertFalse(f.has("packages"))
        assertEquals(listOf("Films", "Series"), Catalog.searchSpecs("x").map { it.heading })
    }

    @Test fun covers_the_big_western_services_and_every_page_is_distinct() {
        val all = Catalog.homePage().flatMap { it.packages }.toSet()
        listOf("Netflix", "HBO Max", "Paramount+", "Prime Video", "Disney+", "Apple TV+").forEach { n -> assertTrue(n, Catalog.SERVICES.any { it.label == n && it.code in all }) }
        // the same heading can recur on different pages, so each shelf must be told apart by what it asks for
        val bodies = Catalog.servicePage(Catalog.NETFLIX).map { Catalog.requestBody(it) } + Catalog.servicePage(Catalog.DISNEY).map { Catalog.requestBody(it) }
        assertEquals(bodies.size, bodies.toSet().size)
        assertTrue(Catalog.homePage().first().ranked); assertTrue(Catalog.genrePage(Catalog.GENRES[0]).size >= 9)
    }

    @Test fun reads_where_a_title_streams() {
        val json = """{"data":{"popularTitles":{"edges":[{"node":{"id":"x","objectType":"MOVIE","content":{"title":"T"},
          "offers":[{"monetizationType":"FLATRATE","package":{"clearName":"Netflix"}},{"monetizationType":"RENT","package":{"clearName":"Apple TV Store"}},
          {"monetizationType":"FLATRATE","package":{"clearName":"Disney Plus"}},{"monetizationType":"ADS","package":{"clearName":"Netflix"}}]}}]}}}"""
        val t = Catalog.parse(json).single()
        assertEquals(listOf("Netflix", "Disney+"), t.services)
        assertEquals("Streaming on Netflix and Disney+", t.streamingLine())
        assertNull(Catalog.parse(answer)[0].streamingLine())
    }

    @Test fun seerr_request_bodies() {
        val movie = JSONObject(SeerrApi.requestBody(Kind.MOVIE, 501170))
        assertEquals("movie", movie.getString("mediaType")); assertEquals(501170L, movie.getLong("mediaId")); assertFalse(movie.has("seasons"))
        val show = JSONObject(SeerrApi.requestBody(Kind.SHOW, 335536))
        assertEquals("tv", show.getString("mediaType")); assertEquals("all", show.getString("seasons"))
        assertEquals("/api/v1/tv/9", SeerrApi.path(Kind.SHOW, 9))
    }

    @Test fun seerr_status_and_outcomes() {
        assertEquals(Availability.AVAILABLE, SeerrApi.parseAvailability("""{"mediaInfo":{"status":5}}"""))
        assertEquals(Availability.REQUESTED, SeerrApi.parseAvailability("""{"mediaInfo":{"status":2}}"""))
        // Seerr says "processing" as soon as Radarr or Sonarr has the request; that is not the same as bytes arriving
        assertEquals(Availability.PROCESSING, SeerrApi.parseAvailability("""{"mediaInfo":{"status":3,"downloadStatus":[]}}"""))
        assertEquals(Availability.PROCESSING, SeerrApi.parseAvailability("""{"mediaInfo":{"status":3}}"""))
        assertEquals(Availability.DOWNLOADING, SeerrApi.parseAvailability("""{"mediaInfo":{"status":3,"downloadStatus":[{"size":10,"sizeLeft":5}]}}"""))
        assertEquals(Availability.NONE, SeerrApi.parseAvailability("""{"title":"no media info yet"}"""))
        assertEquals(Availability.NONE, SeerrApi.parseAvailability("junk"))
        assertEquals(RequestOutcome.QUEUED, SeerrApi.outcomeFor(201)); assertEquals(RequestOutcome.ALREADY, SeerrApi.outcomeFor(409))
        assertEquals(RequestOutcome.NEEDS_SIGN_IN, SeerrApi.outcomeFor(401)); assertEquals(RequestOutcome.FAILED, SeerrApi.outcomeFor(500))
    }

    @Test fun finds_set_cookie_whatever_its_casing() {
        val headers: Map<String?, List<String>> = mapOf(null to listOf("HTTP/1.1 200 OK"), "set-cookie" to listOf("connect.sid=s%3Axyz; Path=/; HttpOnly"))
        assertEquals("connect.sid=s%3Axyz", SeerrApi.sessionCookie(SeerrApi.setCookies(headers)))
        assertTrue(SeerrApi.setCookies(null).isEmpty())
    }

    @Test fun picks_the_session_cookie() {
        assertEquals("connect.sid=s%3Aabc", SeerrApi.sessionCookie(listOf("other=1; Path=/", "connect.sid=s%3Aabc; Path=/; HttpOnly")))
        assertNull(SeerrApi.sessionCookie(listOf("other=1"))); assertNull(SeerrApi.sessionCookie(null))
        assertFalse(SeerrApi.signInBody("daniel", "pw").contains("\"mediaType\""))
    }
}
