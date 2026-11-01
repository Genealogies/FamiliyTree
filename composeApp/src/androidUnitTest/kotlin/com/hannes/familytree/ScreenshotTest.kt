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
import com.hannes.familytree.members.*
import com.hannes.familytree.ui.screens.MemberEditScreen
import com.hannes.familytree.ui.screens.TreeEditScreen

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
    fun members() = snapshot(Route.Members)

    @Test
    fun familyGroup() = snapshot(Route.FamilyGroup)

    @Test
    fun trees() = snapshot(Route.Trees)

    @Test
    fun settings() = snapshot(Route.Settings)

    @Test
    fun detail() = snapshot(Route.Detail(1))

    @Test
    fun familyGroupDetail() = snapshot(Route.Detail(1, Route.FamilyGroup))

    @Test
    fun treeDetail() = snapshot(Route.Detail(1, Route.Trees))

    @Test
    fun familyTree() = snapshot(Route.FamilyTree)

    @Test
    fun memberBirth() = memberSection("Birth")

    @Test
    fun memberGender() = memberSection("Gender")

    @Test
    fun memberDeath() = memberSection("Death")

    @Test
    fun memberEvents() = memberSection("Events")

    @Test
    fun memberParents() = memberSection("Families (Parents)")

    @Test
    fun memberSpouses() = memberSection("Families (Spouses)")

    @Test
    fun memberContacts() = memberSection("Contact Information")

    @Test
    fun memberNotes() = memberSection("Member Notes")

    @Test
    fun memberFields() = memberSection("Additional Member Fields")

    @Test
    fun memberAdditionalInfo() = memberSection("Additional Member Info")

    @Test
    fun memberGallery() = memberSection("Member Gallery")

    @Test
    fun memberFamilyGroup() = memberSection("Family Group")

    @Test
    fun treeVisibility() = treeSection("Visibility Setting")

    @Test
    fun treeContainer() = treeSection("Container Setting")

    @Test
    fun treeBox() = treeSection("Box Setting")

    @Test
    fun treeLine() = treeSection("Line Setting")

    @Test
    fun treeImage() = treeSection("Image Setting")

    @Test
    fun treeNameText() = treeSection("Name Text Setting")

    @Test
    fun treeOtherText() = treeSection("Other Text Setting")

    private fun treeSection(section: String) {
        val data = GenealogyData.screenshotData()
        paparazzi.snapshot {
            androidx.compose.material3.MaterialTheme {
                TreeEditScreen(data.trees.single(), data, {}, {}, {}, section)
            }
        }
    }

    private fun memberSection(section: String) {
        val members = MemberData.initialMembers()
        val member = members.first().copy(
            name = MemberName(fullName = "Jane Doe"),
            parents = listOf(ParentFamily(motherId = 2, fatherId = 3)),
            spouses = listOf(SpouseFamily(spouseId = 4)),
            events = listOf(MemberEvent("BAPM", date = "1951", place = "Berlin")),
            phones = listOf("+49 1234"),
            emails = listOf("jane@example.com"),
            addresses = listOf("Berlin"),
            notes = listOf(MemberNote("Family history", true)),
            fields = listOf(MemberField("Occupation", "Teacher")),
            additionalInfo = "Biography"
        )
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                androidx.compose.material3.MaterialTheme {
                    MemberEditScreen(member, members, {}, {}, {}, section,
                        groups = GenealogyData.screenshotData().groups)
                }
            }
        }
    }

    private fun snapshot(route: Route) {
        paparazzi.snapshot {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides registryOwner) {
                App(initialRoute = route, persistMembers = false)
            }
        }
    }
}
