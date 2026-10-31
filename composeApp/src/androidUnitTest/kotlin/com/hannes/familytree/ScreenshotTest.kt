package com.hannes.familytree

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
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

    // Paparazzi has no Activity, which the file picker needs.
    private val registryOwner = object : ActivityResultRegistryOwner {
        override val activityResultRegistry = object : ActivityResultRegistry() {
            override fun <I, O> onLaunch(
                requestCode: Int,
                contract: ActivityResultContract<I, O>,
                input: I,
                options: ActivityOptionsCompat?
            ) = Unit
        }
    }

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
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                App(initialRoute = route)
            }
        }
    }
}
