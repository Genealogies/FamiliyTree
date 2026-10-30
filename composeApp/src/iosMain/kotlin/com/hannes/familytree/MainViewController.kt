package com.hannes.familytree

import androidx.compose.ui.window.ComposeUIViewController
import com.hannes.familytree.ui.navigation.Route
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform
import platform.Foundation.NSProcessInfo

@OptIn(ExperimentalNativeApi::class)
fun MainViewController() = ComposeUIViewController {
    val route = if (Platform.isDebugBinary) {
        when (NSProcessInfo.processInfo.environment["SCREENSHOT_ROUTE"]) {
            "dashboard" -> Route.Dashboard
            "settings" -> Route.Settings
            "detail" -> Route.DashboardDetail(1)
            else -> Route.Login
        }
    } else {
        Route.Login
    }
    App(initialRoute = route)
}
