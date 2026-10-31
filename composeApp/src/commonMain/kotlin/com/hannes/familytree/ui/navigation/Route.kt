package com.hannes.familytree.ui.navigation

sealed class Route {
    data object Login : Route()
    sealed class Listing(val title: String, val itemLabel: String) : Route()
    data object Members : Listing("Members", "Member")
    data object FamilyGroup : Listing("Family Group", "Family Group")
    data object Trees : Listing("Trees", "Tree")
    data object Settings : Route()
    data object FamilyTree : Route()
    data class Detail(val itemId: Int, val listing: Listing = Members) : Route()
}
