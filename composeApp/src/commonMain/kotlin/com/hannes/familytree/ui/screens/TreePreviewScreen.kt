package com.hannes.familytree.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hannes.familytree.members.*
import com.hannes.familytree.ui.tree.LazyFamilyTree

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreePreviewScreen(tree: Tree, data: GenealogyData, onBack: () -> Unit) {
    val density = LocalDensity.current
    val graph = remember(tree, data, density) { data.treeGraph(tree, density) }
    MemberEditorBackHandler(onBack)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tree.title) },
            navigationIcon = { TextButton(onClick = onBack) { Text("Back") } }
        )
    }) { padding ->
        if (graph.persons.isEmpty()) {
            Text("No members in this tree. Assign members to its family group first.",
                Modifier.padding(padding).padding(16.dp))
        } else {
            LazyFamilyTree(graph = graph, modifier = Modifier.fillMaxSize().padding(padding))
        }
    }
}
