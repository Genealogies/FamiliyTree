package com.hannes.familytree.ui.navigation

import androidx.compose.runtime.Composable
import com.hannes.familytree.ui.screens.DashboardDetailScreen
import com.hannes.familytree.ui.screens.DashboardScreen
import com.hannes.familytree.ui.screens.FamilyTreeScreen
import com.hannes.familytree.ui.screens.SettingsScreen

@Composable
fun AppRouter(
    route: Route,
    onNavigate: (Route) -> Unit,
    onLogout: () -> Unit
) {
    when (route) {
        is Route.Dashboard -> DashboardScreen(
            onOpenDetail = { itemId -> onNavigate(Route.DashboardDetail(itemId)) },
            onOpenSettings = { onNavigate(Route.Settings) },
            onOpenFamilyTree = { onNavigate(Route.FamilyTree) },
            onLogout = onLogout
        )

        is Route.Settings -> SettingsScreen(
            onBack = { onNavigate(Route.Dashboard) }
        )

        is Route.FamilyTree -> FamilyTreeScreen(
            onBack = { onNavigate(Route.Dashboard) }
        )

        is Route.DashboardDetail -> DashboardDetailScreen(
            itemId = route.itemId,
            onBack = { onNavigate(Route.Dashboard) }
        )

        is Route.Login -> Unit
    }
}
