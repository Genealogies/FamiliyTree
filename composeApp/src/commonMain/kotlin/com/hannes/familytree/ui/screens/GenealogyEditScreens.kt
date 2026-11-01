package com.hannes.familytree.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hannes.familytree.members.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditPage(
    title: String,
    dirty: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    content: @Composable ColumnScope.((() -> Unit) -> Unit) -> Unit
) {
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val navigate: (() -> Unit) -> Unit = { if (dirty) pending = it else it() }
    MemberEditorBackHandler { navigate(onBack) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title) },
            navigationIcon = { TextButton(onClick = { navigate(onBack) }) { Text("Back") } },
            actions = {
                TextButton(onClick = {
                    try {
                        onSave()
                        saved = true
                        error = null
                    } catch (e: Exception) {
                        error = "Could not save: ${e.message ?: "unknown error"}"
                    }
                }) { Text("Save") }
            }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Saved on this device only", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved && !dirty) Text("Saved", color = MaterialTheme.colorScheme.primary)
            content(navigate)
        }
    }
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Unsaved changes") },
            text = { Text("Discard your changes?") },
            confirmButton = {
                TextButton(onClick = {
                    val action = pending
                    pending = null
                    action?.invoke()
                }) { Text("Discard") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Keep editing") } }
        )
    }
}

@Composable
fun FamilyGroupEditScreen(
    group: FamilyGroup,
    data: GenealogyData,
    onSave: (FamilyGroup) -> Unit,
    onGenerateTree: (FamilyGroup) -> Unit,
    onOpenTree: (Int) -> Unit,
    onViewTree: (Int) -> Unit,
    onMembers: () -> Unit,
    onBack: () -> Unit
) {
    var draft by remember(group.id) { mutableStateOf(group) }
    LaunchedEffect(group) { draft = group }
    var operationError by remember { mutableStateOf<String?>(null) }
    EditPage("Edit Family Group", draft != group, onBack, { onSave(draft) }) { navigate ->
        EditField("Name", draft.name) { draft = draft.copy(name = it) }
        EditField("Slug", draft.slug) { draft = draft.copy(slug = it) }
        Text("The slug is the URL-friendly name. Leave blank to generate it from the name.",
            style = MaterialTheme.typography.bodySmall)
        EditField("Description", draft.description, multiline = true) { draft = draft.copy(description = it) }
        Text("Default Tree", style = MaterialTheme.typography.titleMedium)
        ChoiceField("Default Tree", draft.defaultTreeId?.toString().orEmpty(),
            listOf("" to "Select Default Tree") + data.trees.filter {
                it.familyGroupId == group.id
            }.map { it.id.toString() to it.title }
        ) { draft = draft.copy(defaultTreeId = it.toIntOrNull()) }
        operationError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (data.trees.none { it.familyGroupId == group.id }) {
            Button(onClick = {
                try {
                    onGenerateTree(draft)
                } catch (e: Exception) {
                    operationError = "Could not generate tree: ${e.message}"
                }
            }) { Text("Generate Default Tree") }
        }
        draft.defaultTreeId?.let { id ->
            Row {
                TextButton(onClick = { navigate { onOpenTree(id) } }) { Text("Edit Tree") }
                TextButton(onClick = { navigate { onViewTree(id) } }) { Text("View Tree") }
            }
        }
        Text("Possible Roots", style = MaterialTheme.typography.titleMedium)
        val roots = data.possibleRoots(group.id)
        if (roots.isEmpty()) Text("No members without parents in this group")
        roots.forEach { Text("${it.displayName} (${it.referenceId})") }
        Text("Members Page", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { navigate(onMembers) }, enabled = data.groups.any { it.id == group.id }) {
            Text("View Members Page")
        }
        Text("${data.members.count { group.id in it.familyGroupIds }} members in this group")
    }
}

@Composable
fun TreeEditScreen(
    tree: Tree,
    data: GenealogyData,
    onSave: (Tree) -> Unit,
    onView: () -> Unit,
    onBack: () -> Unit,
    initialSection: String = "General Setting"
) {
    var draft by remember(tree.id) { mutableStateOf(tree) }
    var section by remember { mutableStateOf(initialSection) }
    EditPage("Edit Tree", draft != tree, onBack, { onSave(draft) }) { navigate ->
        EditField("Title", draft.title) { draft = draft.copy(title = it) }
        ChoiceField("Section", section, TreeSettings.sections.keys.map { it to it }) { section = it }
        Text(section, style = MaterialTheme.typography.titleLarge)
        if (section == "General Setting") {
            ChoiceField("Select Family", draft.familyGroupId?.toString().orEmpty(),
                listOf("" to "Select Family") + data.groups.map { it.id.toString() to it.name }
            ) {
                draft = draft.copy(familyGroupId = it.toIntOrNull(), rootId = null)
            }
            val candidates = data.members.filter {
                draft.familyGroupId == null || draft.familyGroupId in it.familyGroupIds
            }
            ChoiceField("Select Root", draft.rootId?.toString().orEmpty(),
                listOf("" to "Select Root") + candidates.map { it.id.toString() to it.displayName }
            ) { draft = draft.copy(rootId = it.toIntOrNull()) }
        }
        TreeSettings.sections.getValue(section).forEach { field ->
            val value = TreeSettings.value(draft, field.key)
            val update: (String) -> Unit = {
                draft = draft.copy(settings = draft.settings + (field.key to it))
            }
            when {
                field.toggle -> CheckField(field.label, value == "on") { update(if (it) "on" else "") }
                field.choices.isNotEmpty() -> ChoiceField(
                    field.label, value, field.choices.map {
                        it to when (it) { "vr" -> "Vertical"; "hr" -> "Horizontal"; else -> it }
                    }, update
                )
                else -> EditField(field.label, value, onChange = update)
            }
        }
        if (section == "Visibility Setting") Text("\"-1\" is for unlimited generations.")
        if (section == "General Setting") {
            Text("Style 1, Style 4 and Style 5 support separate children for each spouse.",
                style = MaterialTheme.typography.bodySmall)
        }
        Text("Settings are stored using the plugin's field names. The native preview uses " +
            "its own layout, not the WordPress HTML/CSS renderer. Web-only options " +
            "(Ajax, GT Frontend, CSS backgrounds, fonts and style variants) are saved " +
            "but are not rendered by the native preview.",
            style = MaterialTheme.typography.bodySmall)
        Button(onClick = { navigate(onView) }, enabled = data.trees.any { it.id == tree.id }) {
            Text("View Tree")
        }
    }
}
