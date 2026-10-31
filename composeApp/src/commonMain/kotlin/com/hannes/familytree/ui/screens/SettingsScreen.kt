package com.hannes.familytree.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import com.hannes.familytree.gedcom.GedcomImporter
import com.hannes.familytree.gedcom.rememberFilePicker
import com.hannes.familytree.ui.tree.FamilyGraph
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onGraphImported: (FamilyGraph) -> Unit = {}
) {
    val density = LocalDensity.current
    var importError by remember { mutableStateOf<String?>(null) }
    val filePicker = rememberFilePicker { content ->
        if (content != null) {
            val graph = try {
                GedcomImporter.import(content, density)
            } catch (e: Exception) {
                null
            }
            if (graph != null) {
                importError = null
                onGraphImported(graph)
            } else {
                importError = "Could not import GEDCOM file"
            }
        }
    }
    var notificationsEnabled by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings")

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable notifications")
                Switch(
                    checked = notificationsEnabled,
                    onCheckedChange = { notificationsEnabled = it }
                )
            }

            Button(onClick = { filePicker.launch() }) {
                Text("Import GEDCOM")
            }
            importError?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = onBack) {
                Text("Back")
            }
        }
    }
}
