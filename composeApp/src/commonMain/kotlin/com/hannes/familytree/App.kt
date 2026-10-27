package com.hannes.familytree

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import com.hannes.familytree.ui.navigation.AppRouter
import com.hannes.familytree.ui.navigation.Route
import com.hannes.familytree.ui.screens.LoginScreen

@Composable
fun App() {
    MaterialTheme {
        var isLoggedIn by remember { mutableStateOf(false) }
        var currentRoute by remember { mutableStateOf<Route>(Route.Login) }

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
                onLogout = {
                    isLoggedIn = false
                    currentRoute = Route.Login
                }
            )
        }
    }
}
