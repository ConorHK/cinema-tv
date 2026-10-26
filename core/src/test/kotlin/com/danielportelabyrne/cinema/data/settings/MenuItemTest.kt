package com.danielportelabyrne.cinema.data.settings

import com.danielportelabyrne.cinema.platform.MemoryPlatform
import org.junit.Assert.assertEquals
import org.junit.Test

class MenuItemTest {
    @Test fun noSavedOrderIsTheUsualOne() {
        assertEquals(MenuItem.entries, MenuItem.order(null))
    }

    @Test fun unknownNamesDropAndMissingItemsJoinAtTheEnd() {
        val order = MenuItem.order("MUSIC,GONE,HOME,MUSIC")
        assertEquals(listOf(MenuItem.MUSIC, MenuItem.HOME, MenuItem.SEARCH), order.take(3))
        assertEquals(MenuItem.entries.toSet(), order.toSet())
        assertEquals(MenuItem.entries.size, order.size)
    }

    @Test fun movingStopsAtTheEnds() {
        val start = MenuItem.entries
        assertEquals(start, MenuItem.move(start, MenuItem.SEARCH, -1))
        assertEquals(listOf(MenuItem.HOME, MenuItem.SEARCH), MenuItem.move(start, MenuItem.SEARCH, 1).take(2))
        assertEquals(MenuItem.DOWNLOADS, MenuItem.move(start, MenuItem.DOWNLOADS, 1).last())
    }

    @Test fun theSettingRoundTrips() {
        val settings = CinemaSettings(MemoryPlatform())
        val order = MenuItem.move(MenuItem.entries, MenuItem.MUSIC, -6)
        settings.menuOrder = order
        assertEquals(order, settings.menuOrder)
    }
}
