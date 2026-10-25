package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryOrderTest {
    private val day = 86_400_000L
    private val items = listOf(
        JellyfinItem("old", "The Old Film", null, "Movie", null, addedMs = 1 * day, playedMs = 4 * day),
        JellyfinItem("show", "show", null, "Series", null, addedMs = 2 * day, playedMs = 9 * day),
        JellyfinItem("new", "New Film", null, "Movie", null, addedMs = 5 * day),
        JellyfinItem("undated", "Brick", null, "Movie", null))

    private fun ids(order: LibraryOrder) = order.sort(items).map { it.id }

    @Test fun recent_takes_the_later_of_added_and_watched() = assertEquals(listOf("show", "new", "old", "undated"), ids(LibraryOrder.RECENT))
    @Test fun added_ignores_watching() = assertEquals(listOf("new", "show", "old", "undated"), ids(LibraryOrder.ADDED))
    @Test fun watched_puts_unwatched_after_by_date_added() = assertEquals(listOf("show", "old", "new", "undated"), ids(LibraryOrder.WATCHED))
    @Test fun name_skips_leading_articles_and_case() = assertEquals(listOf("undated", "new", "old", "show"), ids(LibraryOrder.NAME))

    @Test fun unknown_saved_value_falls_back_to_recent() {
        assertEquals(LibraryOrder.RECENT, LibraryOrder.of(null))
        assertEquals(LibraryOrder.RECENT, LibraryOrder.of("SHUFFLE"))
        assertEquals(LibraryOrder.NAME, LibraryOrder.of("NAME"))
    }
}
