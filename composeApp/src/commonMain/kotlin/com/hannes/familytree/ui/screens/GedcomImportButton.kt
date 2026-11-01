package com.hannes.familytree.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import com.hannes.familytree.gedcom.GedcomImporter
import com.hannes.familytree.gedcom.rememberFilePicker
import com.hannes.familytree.ui.tree.FamilyGraph

@Composable
fun GedcomImportButton(onGraphImported: (FamilyGraph) -> Unit) {
    val density = LocalDensity.current
    var importError by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberFilePicker { result ->
        if (result != null) {
            try {
                val content = result.getOrThrow()
                val graph = GedcomImporter.import(content, density)
                if (graph == null) {
                    importError = "Could not import GEDCOM file: no members found"
                } else {
                    importError = null
                    onGraphImported(graph)
                }
            } catch (e: Exception) {
                importError = "Could not import GEDCOM file: ${e.message ?: "invalid data"}"
            }
        }
    }

    Column {
        Button(onClick = { filePicker.launch() }) {
            Text("Import GED")
        }
        importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}
