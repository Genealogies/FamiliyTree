package com.hannes.familytree.members

import kotlin.test.*

class MemberDataTest {
    private val members = MemberData.initialMembers()

    @Test
    fun allPluginEditorFieldsRoundTripWithoutLoss() {
        val member = Member(
            id = 1,
            name = MemberName("Dr. Mary Doe Jr.", "Dr.", "Jr.", "Mary", "M", "van", "Doe"),
            gender = "F",
            births = listOf(MemberEvent("BIRT", true, "BIRT", "ABT 1950", "Berlin")),
            deaths = listOf(MemberEvent("DEAT", false, "DEAT", "2 MAY 2020", "Paris")),
            events = listOf(MemberEvent("BAPM", true, "Baptism", "1951", "London")),
            parents = listOf(ParentFamily(2, 3, "ADOPTED", true, "1952", "Temple")),
            spouses = listOf(SpouseFamily(4, "1",
                listOf(MemberEvent("MARR", true, "Civil", "1970", "Rome")))),
            phones = listOf("+49 1234", "+49 5678"),
            emails = listOf("mary@example.com", "mary@example.org"),
            addresses = listOf("First line\nSecond line"),
            notes = listOf(MemberNote("Multiline\nnote", true), MemberNote("Other note")),
            fields = listOf(MemberField("Occupation", "Teacher")),
            additionalInfo = "Biography with <strong>markup</strong> & punctuation",
            gallery = listOf(MemberImage("Portrait", "aGVsbG8=")),
            familyGroupIds = listOf(1, 3)
        )
        assertEquals(member, MemberData.decode(MemberData.encode(listOf(member))).single())
    }

    @Test
    fun displayNameUsesFullNameThenStructuredNameThenPlaceholder() {
        assertEquals("Member #1", Member(1).displayName)
        assertEquals("Jane Doe", Member(1, name = MemberName(givenName = "Jane", surname = "Doe")).displayName)
        assertEquals("Jane", Member(1, name = MemberName(fullName = "Jane", surname = "Doe")).displayName)
    }

    @Test
    fun savedEditsSurviveRepositoryReload() {
        val storage = MemoryStorage()
        val updated = storage.saveMember(storage.loadMembers(),
            members.first().copy(name = MemberName(fullName = "Jane Doe")))
        assertEquals("Jane Doe", updated.first().displayName)
        assertEquals(updated, storage.loadMembers())
    }

    @Test
    fun failedSaveDoesNotOverwritePreviousData() {
        val storage = MemoryStorage(MemberData.encode(members))
        val before = storage.content
        storage.failWrites = true
        assertFailsWith<IllegalStateException> {
            storage.saveMember(members, members.first().copy(gender = "F"))
        }
        assertEquals(before, storage.content)
        assertEquals(members, storage.loadMembers())
    }

    @Test
    fun corruptDataIsReportedInsteadOfReplacedByPlaceholders() {
        assertFails { MemoryStorage("broken json").loadMembers() }
        assertFails { MemberData.decode(MemberData.encode(listOf(Member(1), Member(1)))) }
    }

    @Test
    fun spouseRelationsAndEventsAreReciprocalAndRemovable() {
        val family = SpouseFamily(2, "3", listOf(MemberEvent("MARR", date = "1970")))
        val edited = members.first().copy(spouses = listOf(family))
        val updated = MemberData.update(members, edited)
        assertEquals(listOf(SpouseFamily(1, "0", family.events)), updated[1].spouses)
        val removed = MemberData.update(updated, edited.copy(spouses = emptyList()))
        assertTrue(removed[1].spouses.isEmpty())
        assertTrue(removed.first().spouses.isEmpty())
    }

    @Test
    fun parentLinksDetermineChildrenAndSiblings() {
        val parents = ParentFamily(motherId = 1, fatherId = 2)
        val children = members.map {
            if (it.id in listOf(3, 4)) it.copy(parents = listOf(parents)) else it
        }
        assertEquals(listOf(3, 4), MemberData.children(children, 1, 2).map { it.id })
        assertEquals(listOf(4), MemberData.siblings(children, 3, parents).map { it.id })
        assertTrue(MemberData.children(children, 1, 5).isEmpty())
    }

    @Test
    fun preventsSelfReferencesMissingMembersAndDuplicateSpouses() {
        val invalid = listOf(
            Member(1, parents = listOf(ParentFamily(motherId = 1))),
            Member(1, parents = listOf(ParentFamily(motherId = 99))),
            Member(1, parents = listOf(ParentFamily(motherId = 2, fatherId = 2))),
            Member(1, spouses = listOf(SpouseFamily(1))),
            Member(1, spouses = listOf(SpouseFamily(2), SpouseFamily(2))),
            Member(1, spouses = listOf(SpouseFamily(2, "-1"))),
            Member(1, spouses = listOf(SpouseFamily(2, "abc"))),
            Member(1, parents = listOf(ParentFamily(2, sealed = true), ParentFamily(3, sealed = true))),
            Member(1, familyGroupIds = listOf(-1))
        )
        invalid.forEach { member -> assertFailsWith<IllegalArgumentException> {
            MemberData.update(members, member)
        } }
    }

    @Test
    fun preventsAncestryCyclesButAllowsSharedAncestors() {
        val updated = MemberData.update(members,
            members.first().copy(parents = listOf(ParentFamily(motherId = 2))))
        assertFailsWith<IllegalArgumentException> {
            MemberData.update(updated, members[1].copy(parents = listOf(ParentFamily(fatherId = 1))))
        }
        val shared = MemberData.update(updated, members[2].copy(
            parents = listOf(ParentFamily(motherId = 2))))
        assertEquals(20, MemberData.update(shared, members[3].copy(
            parents = listOf(ParentFamily(motherId = 1, fatherId = 3)))).size)
    }
}

private class MemoryStorage(var content: String? = null) : MemberStorage {
    var failWrites = false
    override fun read() = content
    override fun write(content: String) {
        check(!failWrites) { "Disk full" }
        this.content = content
    }
}
