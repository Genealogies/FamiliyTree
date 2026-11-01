package com.hannes.familytree.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.hannes.familytree.members.*
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.jetbrains.compose.resources.decodeToImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private val individualEvents = listOf(
    "ADOP" to "Adoption", "BAPM" to "Baptism", "BARM" to "Bar Mitzvah",
    "BASM" to "Bas Mitzvah", "BIRT" to "Birth", "BLES" to "Blessing",
    "BURI" to "Burial", "CENS" to "Census", "CHR" to "Christening",
    "CHRA" to "Adult Christening", "CONF" to "Confirmation", "CREM" to "Cremation",
    "DEAT" to "Death", "EMIG" to "Emigration", "FCOM" to "First Communion",
    "GRAD" to "Graduation", "IMMI" to "Immigration", "NATU" to "Naturalization",
    "ORDN" to "Ordination", "PROB" to "Probate", "RETI" to "Retirement",
    "WILL" to "Will", "EVEN" to "Generic Event"
)
private val familyEvents = listOf(
    "ANUL" to "Annulment", "CENS" to "Census", "DIV" to "Divorce",
    "DIVF" to "Divorce Filed", "ENGA" to "Engagement", "MARB" to "Marriage Bann",
    "MARC" to "Marriage Contract", "MARL" to "Marriage License",
    "MARR" to "Marriage", "MARS" to "Marriage Settlement", "EVEN" to "Generic Event"
)
private val editorSections = listOf(
    "Name", "Gender", "Birth", "Death", "Events", "Families (Parents)",
    "Families (Spouses)", "Contact Information", "Member Notes",
    "Additional Member Fields", "Additional Member Info", "Member Gallery", "Family Group"
)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberEditScreen(
    member: Member,
    members: List<Member>,
    onSave: (Member) -> Unit,
    onBack: () -> Unit,
    onOpenMember: (Int) -> Unit,
    initialSection: String = "Name",
    groups: List<FamilyGroup> = emptyList()
) {
    var draft by remember(member.id) { mutableStateOf(member) }
    var section by rememberSaveable(member.id) { mutableStateOf(initialSection) }
    var error by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(false) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    val scrollState = remember(section) { ScrollState(0) }
    fun change(value: Member) {
        draft = value
        error = null
        saved = false
    }
    fun navigate(action: () -> Unit) {
        if (draft != member) pendingNavigation = action else action()
    }
    MemberEditorBackHandler { navigate(onBack) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Member") },
                navigationIcon = {
                    TextButton(onClick = { navigate(onBack) }) { Text("Back") }
                },
                actions = {
                    TextButton(onClick = {
                        try {
                            onSave(draft)
                            saved = true
                            error = null
                        } catch (e: Exception) {
                            error = "Could not save member: ${e.message ?: "unknown error"}"
                        }
                    }) { Text("Save") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Member Info", style = MaterialTheme.typography.titleLarge)
            Text("ID: ${member.id}    REF ID: ${member.referenceId}")
            Text("Saved on this device only", style = MaterialTheme.typography.bodySmall)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (saved) Text("Member saved", color = MaterialTheme.colorScheme.primary)
            ChoiceField("Section", section, editorSections.map { it to it }) { section = it }

            EditorSection("Name", section) {
                val name = draft.name
                EditField("Full Name", name.fullName) { change(draft.copy(name = name.copy(fullName = it))) }
                EditField("Name Prefix", name.prefix) { change(draft.copy(name = name.copy(prefix = it))) }
                EditField("Name Suffix", name.suffix) { change(draft.copy(name = name.copy(suffix = it))) }
                EditField("Given Name", name.givenName) { change(draft.copy(name = name.copy(givenName = it))) }
                EditField("Nickname", name.nickname) { change(draft.copy(name = name.copy(nickname = it))) }
                EditField("Surname Prefix", name.surnamePrefix) { change(draft.copy(name = name.copy(surnamePrefix = it))) }
                EditField("Surname", name.surname) { change(draft.copy(name = name.copy(surname = it))) }
            }
            EditorSection("Gender", section) {
                ChoiceField(
                    "Gender", draft.gender,
                    listOf("" to "Select Gender", "M" to "Male", "F" to "Female")
                ) { change(draft.copy(gender = it)) }
            }
            EditorSection("Birth", section) {
                EventList(draft.births, "Birth", "BIRT", onChange = { change(draft.copy(births = it)) })
            }
            EditorSection("Death", section) {
                EventList(draft.deaths, "Death", "DEAT", onChange = { change(draft.copy(deaths = it)) })
            }
            EditorSection("Events", section) {
                EventList(draft.events, "Event", onChange = { change(draft.copy(events = it)) })
            }
            EditorSection("Families (Parents)", section) {
                RepeatedEditor(
                    "Parent Family", draft.parents, { ParentFamily() },
                    { change(draft.copy(parents = it)) }
                ) { family, update ->
                    MemberChoice("Mother", family.motherId, members, member.id,
                        { update(family.copy(motherId = it)) },
                        { id -> navigate { onOpenMember(id) } })
                    MemberChoice("Father", family.fatherId, members, member.id,
                        { update(family.copy(fatherId = it)) },
                        { id -> navigate { onOpenMember(id) } })
                    ChoiceField("Pedigree", family.pedigree, listOf(
                        "" to "Relation By", "BIRTH" to "BIRTH", "ADOPTED" to "ADOPTED",
                        "FOSTER" to "FOSTER", "SEALING" to "SEALING", "OTHER" to "OTHER"
                    )) { update(family.copy(pedigree = it)) }
                    CheckField("Sealed P (LDS)", family.sealed) {
                        if (it) {
                            change(draft.copy(parents = draft.parents.map { existing ->
                                existing.copy(sealed = existing == family)
                            }))
                        } else {
                            update(family.copy(sealed = false))
                        }
                    }
                    EditField("Date", family.sealingDate) { update(family.copy(sealingDate = it)) }
                    EditField("Place (Temple)", family.temple) { update(family.copy(temple = it)) }
                    Text("Siblings", style = MaterialTheme.typography.titleSmall)
                    val siblings = MemberData.siblings(members, member.id, family)
                    if (siblings.isEmpty()) Text("No siblings")
                    siblings.forEach { sibling ->
                        TextButton(onClick = { navigate { onOpenMember(sibling.id) } }) {
                            Text(sibling.displayName)
                        }
                    }
                }
            }
            EditorSection("Families (Spouses)", section) {
                RepeatedEditor(
                    "Spouse Family", draft.spouses, { SpouseFamily() },
                    { change(draft.copy(spouses = it)) }
                ) { family, update ->
                    EditField("Order", family.order) { update(family.copy(order = it)) }
                    MemberChoice("Spouse", family.spouseId, members, member.id,
                        { update(family.copy(spouseId = it)) },
                        { id -> navigate { onOpenMember(id) } })
                    EventList(family.events, "Family Event", family = true) {
                        update(family.copy(events = it))
                    }
                    Text("Children", style = MaterialTheme.typography.titleSmall)
                    Text("Children are linked through their Families (Parents).",
                        style = MaterialTheme.typography.bodySmall)
                    val children = MemberData.children(members, member.id, family.spouseId)
                    if (children.isEmpty()) Text("No children")
                    children.forEach { child ->
                        TextButton(onClick = { navigate { onOpenMember(child.id) } }) {
                            Text(child.displayName)
                        }
                    }
                }
            }
            EditorSection("Contact Information", section) {
                RepeatedText("Phone", draft.phones) { change(draft.copy(phones = it)) }
                RepeatedText("Email", draft.emails) { change(draft.copy(emails = it)) }
                RepeatedText("Address", draft.addresses) { change(draft.copy(addresses = it)) }
            }
            EditorSection("Member Notes", section) {
                RepeatedEditor("Note", draft.notes, { MemberNote() },
                    { change(draft.copy(notes = it)) }
                ) { note, update ->
                    EditField("Note", note.text, multiline = true) { update(note.copy(text = it)) }
                    CheckField("Reference", note.isReference) { update(note.copy(isReference = it)) }
                }
            }
            EditorSection("Additional Member Fields", section) {
                RepeatedEditor("Field", draft.fields, { MemberField() },
                    { change(draft.copy(fields = it)) }
                ) { field, update ->
                    EditField("Field Name", field.name) { update(field.copy(name = it)) }
                    EditField("Field Value", field.value) { update(field.copy(value = it)) }
                }
            }
            EditorSection("Additional Member Info", section) {
                EditField("Additional Member Info", draft.additionalInfo, multiline = true) {
                    change(draft.copy(additionalInfo = it))
                }
            }
            EditorSection("Member Gallery", section) {
                MemberGallery(draft.gallery) { change(draft.copy(gallery = it)) }
            }
            EditorSection("Family Group", section) {
                if (groups.isEmpty()) Text("Create a Family Group first to assign this member.")
                for (group in groups) {
                    val id = group.id
                    CheckField(group.name, id in draft.familyGroupIds) { checked ->
                        change(draft.copy(familyGroupIds = if (checked) {
                            (draft.familyGroupIds + id).distinct()
                        } else draft.familyGroupIds - id))
                    }
                }
            }
        }
    }
    if (pendingNavigation != null) {
        AlertDialog(
            onDismissRequest = { pendingNavigation = null },
            title = { Text("Unsaved changes") },
            text = { Text("Discard your changes to this member?") },
            confirmButton = {
                TextButton(onClick = {
                    val action = pendingNavigation
                    pendingNavigation = null
                    action?.invoke()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingNavigation = null }) { Text("Keep editing") }
            }
        )
    }
}

@Composable
private fun EditorSection(
    title: String,
    selected: String,
    content: @Composable ColumnScope.() -> Unit
) {
    if (selected != title) return
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
internal fun EditField(
    label: String,
    value: String,
    multiline: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth(), singleLine = !multiline,
        minLines = if (multiline) 4 else 1
    )
}

@Composable
internal fun CheckField(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row {
        Checkbox(checked, onCheckedChange = onChange)
        TextButton(onClick = { onChange(!checked) }) { Text(label) }
    }
}

@Composable
internal fun ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onChange: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.labelLarge)
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text(choices.firstOrNull { it.first == value }?.second ?: value)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onChange(id)
                })
            }
        }
    }
}

@Composable
private fun MemberChoice(
    label: String,
    selected: Int?,
    members: List<Member>,
    selfId: Int,
    onChange: (Int?) -> Unit,
    onEdit: (Int) -> Unit
) {
    var query by remember { mutableStateOf("") }
    EditField("Search $label", query) { query = it }
    val candidates = members.filter {
        it.id != selfId && (it.id == selected ||
            it.displayName.contains(query, ignoreCase = true) || it.referenceId.contains(query, true))
    }
    ChoiceField(
        label, selected?.toString() ?: "",
        listOf("" to "None") + candidates.map { it.id.toString() to "${it.displayName} (${it.referenceId})" }
    ) { onChange(it.toIntOrNull()) }
    if (selected != null) TextButton(onClick = { onEdit(selected) }) { Text("Edit $label") }
}

@Composable
private fun <T> RepeatedEditor(
    label: String,
    values: List<T>,
    create: () -> T,
    onChange: (List<T>) -> Unit,
    content: @Composable (T, (T) -> Unit) -> Unit
) {
    values.forEachIndexed { index, value ->
        Text("$label REF #${index + 1}", style = MaterialTheme.typography.titleSmall)
        content(value) { updated ->
            onChange(values.mapIndexed { i, existing -> if (i == index) updated else existing })
        }
        TextButton(onClick = {
            onChange(values.filterIndexed { i, _ -> i != index })
        }) { Text("Remove $label #${index + 1}") }
        HorizontalDivider()
    }
    OutlinedButton(onClick = { onChange(values + create()) }) { Text("Add $label") }
}

@Composable
private fun RepeatedText(label: String, values: List<String>, onChange: (List<String>) -> Unit) {
    RepeatedEditor(label, values, { "" }, onChange) { value, update ->
        EditField(label, value, onChange = update)
    }
}

@Composable
private fun EventList(
    events: List<MemberEvent>,
    label: String,
    fixedTag: String? = null,
    family: Boolean = false,
    onChange: (List<MemberEvent>) -> Unit
) {
    RepeatedEditor(label, events, {
        MemberEvent(tag = fixedTag.orEmpty(), type = fixedTag.orEmpty())
    }, onChange) { event, update ->
        if (fixedTag == null) {
            ChoiceField("Tag", event.tag,
                listOf("" to "Select an Event") + if (family) familyEvents else individualEvents
            ) { update(event.copy(tag = it)) }
        } else {
            Text("Tag: $fixedTag")
        }
        CheckField("Tag asserted (Y)", event.asserted) { update(event.copy(asserted = it)) }
        EditField("Type", event.type) { update(event.copy(type = it)) }
        EditField("Date", event.date) { update(event.copy(date = it)) }
        EditField("Place", event.place) { update(event.copy(place = it)) }
    }
}

@OptIn(ExperimentalEncodingApi::class, ExperimentalResourceApi::class)
private fun decodeMemberImage(image: MemberImage): ImageBitmap =
    Base64.decode(image.base64).decodeToImageBitmap()

@Composable
private fun MemberGallery(images: List<MemberImage>, onChange: (List<MemberImage>) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberMemberImagePicker { result ->
        if (result != null) {
            try {
                val image = result.getOrThrow()
                decodeMemberImage(image)
                require(images.size < 10) { "A member can have up to 10 images" }
                onChange(images + image)
                error = null
            } catch (e: Exception) {
                error = "Could not add image: ${e.message ?: "unsupported image"}"
            }
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    images.forEachIndexed { index, image ->
        val decoded = remember(image) { runCatching { decodeMemberImage(image) } }
        decoded.getOrNull()?.let { bitmap ->
            Image(bitmap, image.name, Modifier.fillMaxWidth().height(180.dp),
                contentScale = ContentScale.Fit)
        } ?: Text("Could not display ${image.name}", color = MaterialTheme.colorScheme.error)
        EditField("Image title", image.name) { name ->
            onChange(images.mapIndexed { i, existing ->
                if (i == index) existing.copy(name = name) else existing
            })
        }
        TextButton(onClick = { onChange(images.filterIndexed { i, _ -> i != index }) }) {
            Text("Remove Image #${index + 1}")
        }
    }
    OutlinedButton(onClick = { picker.launch() }, enabled = images.size < 10) {
        Text("Add Images")
    }
    Text("Up to 10 images, 5 MB each. Images are stored on this device.",
        style = MaterialTheme.typography.bodySmall)
}
