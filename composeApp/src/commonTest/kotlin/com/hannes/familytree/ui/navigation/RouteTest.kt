package com.hannes.familytree.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RouteTest {
    @Test
    fun detailPreservesItemId() {
        assertEquals(20, Route.Detail(20).itemId)
    }

    @Test
    fun detailsWithTheSameItemAreEqual() {
        assertEquals(Route.Detail(1), Route.Detail(1))
    }

    @Test
    fun detailsWithDifferentItemsAreDistinct() {
        assertNotEquals(Route.Detail(1), Route.Detail(2))
    }

    @Test
    fun detailDefaultsToMembers() {
        assertEquals(Route.Members, Route.Detail(1).listing)
    }

    @Test
    fun detailsPreserveTheirOriginatingListing() {
        for (listing in listOf(Route.Members, Route.FamilyGroup, Route.Trees)) {
            assertEquals(listing, Route.Detail(1, listing).listing)
        }
        assertNotEquals(Route.Detail(1, Route.Members), Route.Detail(1, Route.Trees))
    }
}
