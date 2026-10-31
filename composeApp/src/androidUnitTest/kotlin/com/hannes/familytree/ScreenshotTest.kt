package com.hannes.familytree

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hannes.familytree.ui.navigation.Route
import org.junit.Rule
import org.junit.Test

class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5,
        theme = "android:Theme.Material.Light.NoActionBar",
        maxPercentDifference = 0.0
    )

    @Test
    fun login() = snapshot(Route.Login)

    @Test
    fun dashboard() = snapshot(Route.Dashboard)

    @Test
    fun settings() = snapshot(Route.Settings)

    @Test
    fun detail() = snapshot(Route.DashboardDetail(1))

    @Test
    fun familyTree() = snapshot(Route.FamilyTree)

    private fun snapshot(route: Route) {
        paparazzi.snapshot {
            App(initialRoute = route)
        }
    }
}
