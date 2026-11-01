package com.hannes.familytree.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hannes.familytree.ui.navigation.Route
import com.hannes.familytree.ui.tree.FamilyGraph
import com.hannes.familytree.members.GenealogyData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    listing: Route.Listing,
    data: GenealogyData,
    onDelete: (Int) -> Unit,
    onSelectListing: (Route.Listing) -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFamilyTree: () -> Unit,
    onLogout: () -> Unit,
    onGraphImported: (FamilyGraph) -> Unit,
    groupFilter: Int? = null
) {
    val listings = listOf(Route.Members, Route.FamilyGroup, Route.Trees)
    var menuExpanded by remember { mutableStateOf(false) }
    var query by remember(listing) { mutableStateOf("") }
    var filter by remember(listing, groupFilter) { mutableStateOf(groupFilter) }
    var sort by remember(listing) { mutableStateOf("Name") }
    var pendingDelete by remember { mutableStateOf<Int?>(null) }
    var deleteError by remember { mutableStateOf<String?>(null) }
    fun title(id: Int): String = when (listing) {
        Route.Members -> data.members.first { it.id == id }.displayName
        Route.FamilyGroup -> data.groups.first { it.id == id }.name
        Route.Trees -> data.trees.first { it.id == id }.title
    }
    val ids = when (listing) {
        Route.Members -> data.members.filter {
            filter == null || filter in it.familyGroupIds
        }.map { it.id }
        Route.FamilyGroup -> data.groups.map { it.id }
        Route.Trees -> data.trees.filter {
            filter == null || filter == it.familyGroupId
        }.map { it.id }
    }
    val items = ids.filter { title(it).contains(query, true) || it.toString() == query }
        .let { values ->
            when (sort) {
                "ID" -> values.sorted()
                "Born" -> values.sortedBy { id ->
                    data.members.first { it.id == id }.births.firstOrNull()?.date
                        ?.let { Regex("\\d{4}").find(it)?.value?.toIntOrNull() } ?: Int.MAX_VALUE
                }
                else -> values.sortedBy { title(it).lowercase() }
            }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(listing.title) },
                actions = {
                    Box {
                        TextButton(onClick = { menuExpanded = true }) { Text("Menu") }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Family Tree Test") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenFamilyTree()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuExpanded = false
                                    onOpenSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    menuExpanded = false
                                    onLogout()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = listings.indexOf(listing)) {
                listings.forEach { destination ->
                    Tab(
                        selected = listing == destination,
                        onClick = { onSelectListing(destination) },
                        text = { Text(destination.title) }
                    )
                }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onOpenDetail(0) }) {
                            Text("Add ${listing.itemLabel}")
                        }
                        GedcomImportButton(onGraphImported = onGraphImported)
                    }
                }
                item {
                    EditField("Search ${listing.title}", query) { query = it }
                    if (listing != Route.FamilyGroup) {
                        ChoiceField("Family Group", filter?.toString().orEmpty(),
                            listOf("" to "All Family Groups") +
                                data.groups.map { it.id.toString() to it.name }
                        ) { filter = it.toIntOrNull() }
                    }
                    ChoiceField("Sort", sort,
                        (if (listing == Route.Members) listOf("Name", "ID", "Born")
                            else listOf("Name", "ID")).map { it to it }
                    ) { sort = it }
                    Text("${items.size} ${listing.title}")
                    if (items.isEmpty()) Text("No ${listing.title.lowercase()} found. Add one to get started.")
                    deleteError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
                items(items) { id ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                title(id),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("ID: $id", style = MaterialTheme.typography.bodySmall)
                            when (listing) {
                                Route.Members -> {
                                    val member = data.members.first { it.id == id }
                                    val parentIds = member.parents.flatMap { listOfNotNull(it.motherId, it.fatherId) }
                                    Text("Born: ${member.births.firstOrNull()?.date.orEmpty().ifBlank { "-" }}")
                                    Text("Parents: " + data.members.filter { it.id in parentIds }
                                        .joinToString { it.displayName }.ifBlank { "-" })
                                    Text("Spouses: " + data.members.filter { person ->
                                        member.spouses.any { it.spouseId == person.id }
                                    }.joinToString { it.displayName }.ifBlank { "-" })
                                    Text("Family Groups: " + data.groups.filter { it.id in member.familyGroupIds }
                                        .joinToString { it.name }.ifBlank { "-" })
                                }
                                Route.FamilyGroup -> {
                                    val group = data.groups.first { it.id == id }
                                    Text("Slug: ${group.slug}")
                                    Text(group.description)
                                    Text("Count: ${data.members.count { id in it.familyGroupIds }}")
                                }
                                Route.Trees -> {
                                    val tree = data.trees.first { it.id == id }
                                    Text("Family: ${data.groups.firstOrNull { it.id == tree.familyGroupId }?.name ?: "-"}")
                                    Text("Root: ${data.members.firstOrNull { it.id == tree.rootId }?.displayName ?: "-"}")
                                    Text("Shortcode: [gt-tree id=\"$id\"]")
                                }
                            }
                            Row {
                                TextButton(onClick = { onOpenDetail(id) }) { Text("Edit") }
                                TextButton(onClick = { pendingDelete = id }) { Text("Delete") }
                            }
                        }
                    }
                    pendingDelete?.let { id ->
                        AlertDialog(
                            onDismissRequest = { pendingDelete = null },
                            title = { Text("Delete ${title(id)}?") },
                            text = { Text("This cannot be undone. Related references will be removed.") },
                            confirmButton = {
                                TextButton(onClick = {
                                    try {
                                        onDelete(id)
                                        pendingDelete = null
                                        deleteError = null
                                    } catch (e: Exception) {
                                        pendingDelete = null
                                        deleteError = "Could not delete: ${e.message}"
                                    }
                                }) { Text("Delete") }
                            },
                            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
                        )
                    }
                }
            }
        }
    }
}
