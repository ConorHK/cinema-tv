package com.danielportelabyrne.cinema.data.settings

/** A page in the menu down the left edge. The owner sets their order; Settings always sits at the foot. */
enum class MenuItem(val label: String) {
    SEARCH("Search"),
    HOME("Home"),
    FILMS("Films"),
    SERIES("Series"),
    DOCS("Documentaries"),
    LETTERBOXD("Letterboxd"),
    MUSIC("Music"),
    LIBRARY("My library"),
    DOWNLOADS("Downloads");

    companion object {
        /** A saved order, with unknown names dropped and items it lacks added at the end in their usual place. */
        fun order(saved: String?): List<MenuItem> {
            val named = saved.orEmpty().split(',').mapNotNull { n -> entries.firstOrNull { it.name == n } }.distinct()
            return named + entries.filterNot { it in named }
        }

        fun save(order: List<MenuItem>): String = order.joinToString(",") { it.name }

        /** [order] with [item] moved [by] places, stopping at either end. */
        fun move(order: List<MenuItem>, item: MenuItem, by: Int): List<MenuItem> {
            val from = order.indexOf(item)
            if (from < 0) return order
            val to = (from + by).coerceIn(0, order.size - 1)
            return order.toMutableList().apply { removeAt(from); add(to, item) }
        }
    }
}
