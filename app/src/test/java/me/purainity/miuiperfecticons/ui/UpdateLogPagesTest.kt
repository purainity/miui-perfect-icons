package me.purainity.miuiperfecticons.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateLogPagesTest {
    @Test fun loadsEachPageWithoutDuplicatesAndStopsAtTheEnd() {
        val pages = UpdateLogPages<Int>(25)
        pages.replace((0 until 63).toList())
        assertEquals(25, pages.visibleCount)
        assertEquals(0, pages.item(0))
        assertEquals(24, pages.item(24))
        assertEquals(25, pages.loadNext())
        assertEquals(13, pages.loadNext())
        assertEquals(0, pages.loadNext())
        assertEquals((0 until 63).toList(), (0 until pages.visibleCount).map(pages::item))
    }

    @Test fun refreshResetsPaginationAndHandlesShortLists() {
        val pages = UpdateLogPages<Int>(2)
        pages.replace(listOf(1, 2, 3, 4, 5))
        assertEquals(2, pages.loadNext())
        pages.replace(listOf(7))
        assertEquals(1, pages.visibleCount)
        assertEquals(7, pages.item(0))
        assertEquals(0, pages.loadNext())
    }
}
