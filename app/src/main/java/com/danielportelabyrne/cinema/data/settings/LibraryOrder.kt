package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.data.jellyfin.JellyfinItem

/** How My library lists the owner's films and series. */
enum class LibraryOrder(val label: String, val detail: String) {
    RECENT("Up next", "New downloads and what you watched, newest first"),
    ADDED("Recently added", "Newest downloads first"),
    WATCHED("Recently watched", "Last watched first, then newest downloads"),
    NAME("A to Z", "By title");

    fun sort(items: List<JellyfinItem>): List<JellyfinItem> = when (this) {
        RECENT -> items.sortedByDescending { it.recentMs }
        ADDED -> items.sortedByDescending { it.addedMs ?: 0L }
        WATCHED -> items.sortedWith(compareByDescending<JellyfinItem> { it.playedMs ?: 0L }.thenByDescending { it.addedMs ?: 0L })
        NAME -> items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name.removePrefix("The ").removePrefix("A ") })
    }

    companion object {
        fun of(name: String?): LibraryOrder = entries.firstOrNull { it.name == name } ?: RECENT
    }
}
