package com.hannes.familytree.ui.navigation

import androidx.compose.runtime.Composable
import com.hannes.familytree.ui.screens.MembersScreen
import com.hannes.familytree.ui.screens.FamilyTreeScreen
import com.hannes.familytree.ui.screens.SettingsScreen
import com.hannes.familytree.ui.tree.FamilyGraph
import com.hannes.familytree.members.*
import com.hannes.familytree.ui.screens.MemberEditScreen
import com.hannes.familytree.ui.screens.FamilyGroupEditScreen
import com.hannes.familytree.ui.screens.TreeEditScreen
import com.hannes.familytree.ui.screens.TreePreviewScreen
import androidx.compose.runtime.key

@Composable
fun AppRouter(
    route: Route,
    onNavigate: (Route) -> Unit,
    onLogout: () -> Unit,
    data: GenealogyData,
    onSaveData: (GenealogyData) -> Unit,
    importedGraph: FamilyGraph? = null,
    onGraphImported: (FamilyGraph) -> Unit = {},
    returnToListing: Route.Listing = Route.Members
) {
    when (route) {
        is Route.Listing -> MembersScreen(
            listing = route,
            data = data,
            onDelete = { id ->
                onSaveData(when (route) {
                    Route.Members -> data.deleteMember(id)
                    Route.FamilyGroup -> data.deleteGroup(id)
                    Route.Trees -> data.deleteTree(id)
                })
            },
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

        is Route.Detail -> if (route.listing == Route.Members) {
            key(route.itemId) {
                val member = data.members.firstOrNull { it.id == route.itemId }
                    ?: Member(if (route.itemId == 0) (data.members.maxOfOrNull { it.id } ?: 0) + 1
                        else route.itemId)
                MemberEditScreen(
                    member = member,
                    members = data.members,
                    groups = data.groups,
                    onSave = {
                        onSaveData(data.saveMember(it))
                        onNavigate(Route.Detail(it.id))
                    },
                    onBack = { onNavigate(Route.Members) },
                    onOpenMember = { onNavigate(Route.Detail(it)) }
                )
            }
        } else if (route.listing == Route.FamilyGroup) {
            key(route.itemId) {
                val group = data.groups.firstOrNull { it.id == route.itemId }
                    ?: FamilyGroup(if (route.itemId == 0) (data.groups.maxOfOrNull { it.id } ?: 0) + 1
                        else route.itemId)
                FamilyGroupEditScreen(
                    group, data,
                    onSave = {
                        onSaveData(data.saveGroup(it))
                        onNavigate(Route.Detail(it.id, Route.FamilyGroup))
                    },
                    onGenerateTree = {
                        val updated = data.saveGroup(it).generateDefaultTree(it.id)
                        onSaveData(updated)
                        onNavigate(Route.Detail(updated.groups.first { g -> g.id == it.id }
                            .defaultTreeId!!, Route.Trees))
                    },
                    onOpenTree = { onNavigate(Route.Detail(it, Route.Trees)) },
                    onViewTree = { onNavigate(Route.TreePreview(it)) },
                    onMembers = { onNavigate(Route.GroupMembers(group.id)) },
                    onBack = { onNavigate(Route.FamilyGroup) }
                )
            }
        } else {
            key(route.itemId) {
                val tree = data.trees.firstOrNull { it.id == route.itemId }
                    ?: Tree(if (route.itemId == 0) (data.trees.maxOfOrNull { it.id } ?: 0) + 1
                        else route.itemId)
                TreeEditScreen(
                    tree, data,
                    onSave = {
                        onSaveData(data.saveTree(it))
                        onNavigate(Route.Detail(it.id, Route.Trees))
                    },
                    onView = { onNavigate(Route.TreePreview(tree.id)) },
                    onBack = { onNavigate(Route.Trees) }
                )
            }
        }

        is Route.GroupMembers -> MembersScreen(
            listing = Route.Members,
            data = data,
            groupFilter = route.groupId,
            onDelete = { onSaveData(data.deleteMember(it)) },
            onSelectListing = onNavigate,
            onOpenDetail = { onNavigate(Route.Detail(it)) },
            onOpenSettings = { onNavigate(Route.Settings) },
            onOpenFamilyTree = { onNavigate(Route.FamilyTree) },
            onLogout = onLogout,
            onGraphImported = {
                onGraphImported(it)
                onNavigate(Route.FamilyTree)
            }
        )

        is Route.TreePreview -> TreePreviewScreen(
            tree = data.trees.first { it.id == route.treeId },
            data = data,
            onBack = { onNavigate(Route.Detail(route.treeId, Route.Trees)) }
        )

        is Route.Login -> Unit
    }
}
