package com.hannes.familytree

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.hannes.familytree.ui.navigation.AppRouter
import com.hannes.familytree.ui.navigation.Route
import com.hannes.familytree.ui.screens.LoginScreen
import com.hannes.familytree.ui.tree.FamilyGraph

@Composable
fun App(initialRoute: Route = Route.Login) {
    MaterialTheme {
        var isLoggedIn by remember { mutableStateOf(initialRoute != Route.Login) }
        var importedGraph by remember { mutableStateOf<FamilyGraph?>(null) }
        var currentRoute by remember { mutableStateOf(initialRoute) }

        if (!isLoggedIn) {
            LoginScreen(
                onLogin = { username, password ->
                    if (username.isNotBlank() && password.isNotBlank()) {
                        isLoggedIn = true
                        currentRoute = Route.Dashboard
                    }
                }
            )
        } else {
            AppRouter(
                route = currentRoute,
                onNavigate = { currentRoute = it },
                importedGraph = importedGraph,
                onGraphImported = { importedGraph = it },
                onLogout = {
                    isLoggedIn = false
                    currentRoute = Route.Login
                }
            )
        }
    }
}
