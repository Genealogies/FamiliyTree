package com.hannes.familytree.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import com.hannes.familytree.ui.tree.LazyFamilyTree
import com.hannes.familytree.ui.tree.sampleFamilyGraph

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyTreeScreen(
    onBack: () -> Unit
) {
    val density = LocalDensity.current
    val graph = remember { sampleFamilyGraph(density) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Family Tree") },
                actions = {
                    TextButton(onClick = onBack) { Text("Back") }
                }
            )
        }
    ) { padding ->
        LazyFamilyTree(
            graph = graph,
            modifier = Modifier.padding(padding)
        )
    }
}
