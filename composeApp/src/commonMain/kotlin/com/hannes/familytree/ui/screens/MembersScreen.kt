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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    listing: Route.Listing,
    onSelectListing: (Route.Listing) -> Unit,
    onOpenDetail: (Int) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFamilyTree: () -> Unit,
    onLogout: () -> Unit,
    onGraphImported: (FamilyGraph) -> Unit
) {
    val items = List(20) { index -> index + 1 }
    val listings = listOf(Route.Members, Route.FamilyGroup, Route.Trees)
    var menuExpanded by remember { mutableStateOf(false) }

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
                                text = { Text("Family Tree") },
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
                    GedcomImportButton(onGraphImported = onGraphImported)
                }
                items(items) { id ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOpenDetail(id) }
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "${listing.itemLabel} #$id",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Text("Tap to open details", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}
