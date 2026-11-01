package com.hannes.familytree.ui.navigation

import androidx.compose.runtime.Composable
import com.hannes.familytree.ui.screens.DetailScreen
import com.hannes.familytree.ui.screens.MembersScreen
import com.hannes.familytree.ui.screens.FamilyTreeScreen
import com.hannes.familytree.ui.screens.SettingsScreen
import com.hannes.familytree.ui.tree.FamilyGraph

@Composable
fun AppRouter(
    route: Route,
    onNavigate: (Route) -> Unit,
    onLogout: () -> Unit,
    importedGraph: FamilyGraph? = null,
    onGraphImported: (FamilyGraph) -> Unit = {},
    returnToListing: Route.Listing = Route.Members
) {
    when (route) {
        is Route.Listing -> MembersScreen(
            listing = route,
            onSelectListing = onNavigate,
            onOpenDetail = { itemId -> onNavigate(Route.Detail(itemId, route)) },
            onOpenSettings = { onNavigate(Route.Settings) },
            onOpenFamilyTree = { onNavigate(Route.FamilyTree) },
            onLogout = onLogout,
            onGraphImported = {
                onGraphImported(it)
                onNavigate(Route.FamilyTree)
            }
        )

        is Route.Settings -> SettingsScreen(
            onBack = { onNavigate(returnToListing) },
            onGraphImported = {
                onGraphImported(it)
                onNavigate(Route.FamilyTree)
            }
        )

        is Route.FamilyTree -> FamilyTreeScreen(
            onBack = { onNavigate(returnToListing) },
            importedGraph = importedGraph
        )

        is Route.Detail -> DetailScreen(
            itemId = route.itemId,
            listing = route.listing,
            onBack = { onNavigate(route.listing) }
        )

        is Route.Login -> Unit
    }
}
