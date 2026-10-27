package com.hannes.familytree.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RouteTest {
    @Test
    fun dashboardDetailPreservesItemId() {
        assertEquals(20, Route.DashboardDetail(20).itemId)
    }

    @Test
    fun dashboardDetailsWithTheSameItemAreEqual() {
        assertEquals(Route.DashboardDetail(1), Route.DashboardDetail(1))
    }

    @Test
    fun dashboardDetailsWithDifferentItemsAreDistinct() {
        assertNotEquals(Route.DashboardDetail(1), Route.DashboardDetail(2))
    }
}
