package com.hannes.familytree.ui.navigation

import androidx.compose.runtime.Composable
import com.hannes.familytree.ui.screens.DashboardDetailScreen
import com.hannes.familytree.ui.screens.DashboardScreen
import com.hannes.familytree.ui.screens.FamilyTreeScreen
import com.hannes.familytree.ui.screens.SettingsScreen
import com.hannes.familytree.ui.tree.FamilyGraph

@Composable
fun AppRouter(
    route: Route,
    onNavigate: (Route) -> Unit,
    onLogout: () -> Unit,
    importedGraph: FamilyGraph? = null,
    onGraphImported: (FamilyGraph) -> Unit = {}
) {
    when (route) {
        is Route.Dashboard -> DashboardScreen(
            onOpenDetail = { itemId -> onNavigate(Route.DashboardDetail(itemId)) },
            onOpenSettings = { onNavigate(Route.Settings) },
            onOpenFamilyTree = { onNavigate(Route.FamilyTree) },
            onLogout = onLogout
        )

        is Route.Settings -> SettingsScreen(
            onBack = { onNavigate(Route.Dashboard) },
            onGraphImported = {
                onGraphImported(it)
                onNavigate(Route.FamilyTree)
            }
        )

        is Route.FamilyTree -> FamilyTreeScreen(
            onBack = { onNavigate(Route.Dashboard) },
            importedGraph = importedGraph
        )

        is Route.DashboardDetail -> DashboardDetailScreen(
            itemId = route.itemId,
            onBack = { onNavigate(Route.Dashboard) }
        )

        is Route.Login -> Unit
    }
}
