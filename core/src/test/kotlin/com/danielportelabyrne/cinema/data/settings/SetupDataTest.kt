package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinDiscovery
import com.danielportelabyrne.cinema.data.letterboxd.LetterboxdPages
import com.danielportelabyrne.cinema.data.movies.ArrSetup
import org.junit.Assert.*
import org.junit.Test

class SetupDataTest {
    @Test fun addresses_are_tidied_and_blank_means_none() {
        assertEquals("http://192.168.1.5:8096", CinemaSettings.cleanUrl(" 192.168.1.5:8096/ "))
        assertEquals("https://jf.example.org", CinemaSettings.cleanUrl("https://jf.example.org/"))
        assertNull(CinemaSettings.cleanUrl("   "))
        assertNull(CinemaSettings.cleanUrl(null))
    }

    @Test fun reads_a_jellyfin_discovery_answer() {
        val found = JellyfinDiscovery.parse("""{"Address":"http://10.0.0.4:8096/","Id":"x","Name":"Lounge"}""")!!
        assertEquals("Lounge", found.name); assertEquals("http://10.0.0.4:8096", found.address)
        assertNull(JellyfinDiscovery.parse("""{"Name":"No address"}"""))
        assertNull(JellyfinDiscovery.parse("junk"))
    }

    @Test fun manual_sonarr_takes_its_first_profile_and_root_folder() {
        assertEquals(4, ArrSetup.firstProfileId("""[{"id":4,"name":"HD-1080p"},{"id":6}]"""))
        assertNull(ArrSetup.firstProfileId("[]"))
        assertEquals("/tv", ArrSetup.firstRootFolder("""[{"id":1,"path":"/tv"}]"""))
        assertEquals("Radarr", ArrSetup.appName("""{"appName":"Radarr","version":"5"}"""))
    }

    @Test fun nobody_is_followed_on_letterboxd_out_of_the_box() {
        assertTrue(LetterboxdPages.DEFAULT.isEmpty())
    }
}
