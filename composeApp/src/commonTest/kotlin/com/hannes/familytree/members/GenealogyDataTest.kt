package com.hannes.familytree.members

import androidx.compose.ui.unit.Density
import kotlin.test.*

class GenealogyDataTest {
    private val group = FamilyGroup(1, "Doe Family", "doe-family")
    private val mother = Member(1, name = MemberName(fullName = "Jane Doe"), gender = "F",
        familyGroupIds = listOf(1))
    private val father = Member(2, name = MemberName(fullName = "John Doe"), gender = "M",
        familyGroupIds = listOf(1))
    private val child = Member(3, name = MemberName(fullName = "Child Doe"),
        parents = listOf(ParentFamily(1, 2)), familyGroupIds = listOf(1))
    private val tree = Tree(1, "Doe Tree", 1, 2)
    private val data = GenealogyData(listOf(mother, father, child), listOf(group), listOf(tree))

    @Test
    fun savesAndReloadsAllThreeTabsTogether() {
        val storage = TestStorage()
        val withDefault = data.saveGroup(group.copy(defaultTreeId = 1))
        storage.saveGenealogy(withDefault)
        assertEquals(withDefault, storage.loadGenealogy())
    }

    @Test
    fun newInstallStartsWithEmptyListsNotFakeRecords() {
        assertEquals(GenealogyData(), TestStorage().loadGenealogy())
    }

    @Test
    fun migratesExistingMemberOnlyDataAndPreservesGroupLinks() {
        val migrated = GenealogyData.decode(MemberData.encode(listOf(mother, father)))
        assertEquals(listOf(mother, father), migrated.members)
        assertEquals(listOf(1), migrated.groups.map { it.id })
        assertTrue(migrated.trees.isEmpty())
    }

    @Test
    fun createsEditsAndDeletesMembersWithoutDanglingReferences() {
        val linked = data.saveMember(mother.copy(spouses = listOf(SpouseFamily(2))))
        val deleted = linked.deleteMember(2)
        deleted.validate()
        assertNull(deleted.members.first { it.id == 3 }.parents.first().fatherId)
        assertTrue(deleted.members.first { it.id == 1 }.spouses.isEmpty())
        assertNull(deleted.trees.first().rootId)
        val created = deleted.saveMember(Member(4, name = MemberName(fullName = "New member")))
        assertEquals("New member", created.members.first { it.id == 4 }.displayName)
    }

    @Test
    fun groupSlugIsGeneratedAndMustBeUnique() {
        val updated = data.saveGroup(FamilyGroup(2, "Another Family"))
        assertEquals("another-family", updated.groups.first { it.id == 2 }.slug)
        assertFailsWith<IllegalArgumentException> { data.saveGroup(FamilyGroup(2, "Other", "DOE-FAMILY")) }
        assertFailsWith<IllegalArgumentException> { data.saveGroup(FamilyGroup(2, "")) }
    }

    @Test
    fun deletingGroupRemovesMembershipAndTreeReferencesNotMembers() {
        val deleted = data.deleteGroup(1)
        deleted.validate()
        assertEquals(3, deleted.members.size)
        assertTrue(deleted.members.all { it.familyGroupIds.isEmpty() })
        assertNull(deleted.trees.first().familyGroupId)
        assertNull(deleted.trees.first().rootId)
    }

    @Test
    fun deletingTreeClearsDefaultTree() {
        val deleted = data.saveGroup(group.copy(defaultTreeId = 1)).deleteTree(1)
        deleted.validate()
        assertNull(deleted.groups.first().defaultTreeId)
        assertTrue(deleted.trees.isEmpty())
    }

    @Test
    fun defaultTreeMustBelongToItsGroup() {
        val updated = data.saveGroup(FamilyGroup(2, "Other", "other"))
            .saveTree(Tree(2, "Other Tree", 2))
        assertFailsWith<IllegalArgumentException> {
            updated.saveGroup(group.copy(defaultTreeId = 2))
        }
        assertFailsWith<IllegalArgumentException> {
            updated.saveTree(tree.copy(familyGroupId = 2))
        }
    }

    @Test
    fun rootMustBeExistingMemberOfSelectedFamily() {
        val updated = data.saveMember(Member(4, name = MemberName(fullName = "Other")))
        assertFailsWith<IllegalArgumentException> { updated.saveTree(tree.copy(rootId = 4)) }
        assertFailsWith<IllegalArgumentException> { updated.saveTree(tree.copy(rootId = 99)) }
        assertFailsWith<IllegalArgumentException> { updated.saveTree(tree.copy(title = "")) }
        assertFailsWith<IllegalArgumentException> {
            updated.saveMember(father.copy(familyGroupIds = emptyList()))
        }
    }

    @Test
    fun generatesDefaultTreeAndFindsRootsFromParentLinks() {
        assertEquals(listOf(1, 2), data.possibleRoots(1).map { it.id })
        val updated = data.copy(trees = emptyList()).generateDefaultTree(1)
        val generated = updated.trees.single()
        assertEquals(1, generated.familyGroupId)
        assertEquals(1, generated.rootId)
        assertEquals(generated.id, updated.groups.single().defaultTreeId)
    }

    @Test
    fun treeSettingsCoverPluginSectionsAndRoundTrip() {
        assertEquals(8, TreeSettings.sections.size)
        val settings = TreeSettings.sections.values.flatten().associate { it.key to it.default }
        TreeSettings.validate(settings)
        val updated = data.saveTree(tree.copy(settings = settings))
        assertEquals(updated, GenealogyData.decode(GenealogyData.encode(updated)))
        assertFailsWith<IllegalArgumentException> { TreeSettings.validate(mapOf("generation_number" to "-2")) }
        assertFailsWith<IllegalArgumentException> { TreeSettings.validate(mapOf("layout" to "diagonal")) }
        assertFailsWith<IllegalArgumentException> { TreeSettings.validate(mapOf("background.color" to "bad")) }
    }

    @Test
    fun failedWritesKeepPreviousDocumentUnchanged() {
        val storage = TestStorage(GenealogyData.encode(data))
        storage.failWrites = true
        assertFailsWith<IllegalStateException> {
            storage.saveGenealogy(data.deleteGroup(1))
        }
        assertEquals(data, storage.loadGenealogy())
    }

    @Test
    fun invalidSavedReferencesAreNotSilentlyDropped() {
        assertFailsWith<IllegalArgumentException> {
            GenealogyData.decode(GenealogyData.encode(data.copy(groups = emptyList())))
        }
        assertFails { TestStorage("broken json").loadGenealogy() }
    }

    @Test
    fun previewUsesActualMembersAndParentRelationships() {
        val graph = data.treeGraph(tree, Density(1f))
        assertEquals(setOf("1", "2", "3"), graph.persons.keys)
        assertTrue(graph.persons.getValue("1").name.startsWith("Jane Doe"))
        assertTrue(graph.unions.any { "3" in it.childrenIds && "1" in it.spouseIds && "2" in it.spouseIds })
        assertTrue(graph.width > 0f && graph.height > 0f)
    }

    @Test
    fun previewRespectsGroupRootAndGenerationLimits() {
        val limited = tree.copy(settings = mapOf("generation_number" to "0", "ancestor" to ""))
        assertEquals(setOf("2"), data.treeGraph(limited, Density(1f)).persons.keys)
        val unrelated = data.saveMember(Member(4, name = MemberName(fullName = "Unrelated")))
        assertFalse("4" in unrelated.treeGraph(tree, Density(1f)).persons)
    }

    @Test
    fun previewHidesBirthDateOfLivingMembersWhenRequested() {
        val born = father.copy(births = listOf(MemberEvent("BIRT", date = "12 JAN 1950")))
        val updated = data.saveMember(born)
        val visible = updated.treeGraph(tree, Density(1f)).persons.getValue("2")
        assertTrue(visible.name.contains("1950"))
        val hidden = updated.treeGraph(
            tree.copy(settings = mapOf("birt_hide_alive" to "on")), Density(1f)
        ).persons.getValue("2")
        assertFalse(hidden.name.contains("1950"))
    }
}

private class TestStorage(var content: String? = null) : MemberStorage {
    var failWrites = false
    override fun read(): String? = content
    override fun write(content: String) {
        check(!failWrites) { "Disk full" }
        this.content = content
    }
}
