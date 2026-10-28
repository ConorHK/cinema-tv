package com.danielportelabyrne.cinema.data.settings

/**
 * An optional service Cinema can add to the menu and the title pages, switched on or off under Settings,
 * Integrations. Add one here and it gets its row on that page; each app decides what it shows while it is on.
 */
enum class Integration(val label: String, val summary: String, val onByDefault: Boolean) {
    LETTERBOXD("Letterboxd", "Friends' diaries, lists and stars on film pages", onByDefault = true);

    /** The preferences key; stable, so a rename of the entry does not lose the setting. */
    val key: String get() = "integration_" + name.lowercase()
}
