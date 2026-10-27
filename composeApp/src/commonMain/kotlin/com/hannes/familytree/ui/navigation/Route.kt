package com.hannes.familytree.ui.navigation

sealed class Route {
    data object Login : Route()
    data object Dashboard : Route()
    data object Settings : Route()
    data class DashboardDetail(val itemId: Int) : Route()
}
