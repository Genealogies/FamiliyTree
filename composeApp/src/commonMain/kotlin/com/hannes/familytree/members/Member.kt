package com.hannes.familytree.members

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class MemberName(
    val fullName: String = "",
    val prefix: String = "",
    val suffix: String = "",
    val givenName: String = "",
    val nickname: String = "",
    val surnamePrefix: String = "",
    val surname: String = ""
)

@Serializable
data class MemberEvent(
    val tag: String = "",
    val asserted: Boolean = false,
    val type: String = "",
    val date: String = "",
    val place: String = ""
)

@Serializable
data class ParentFamily(
    val motherId: Int? = null,
    val fatherId: Int? = null,
    val pedigree: String = "",
    val sealed: Boolean = false,
    val sealingDate: String = "",
    val temple: String = ""
)

@Serializable
data class SpouseFamily(
    val spouseId: Int? = null,
    val order: String = "0",
    val events: List<MemberEvent> = emptyList()
)

@Serializable
data class MemberNote(val text: String = "", val isReference: Boolean = false)

@Serializable
data class MemberField(val name: String = "", val value: String = "")

@Serializable
data class MemberImage(val name: String, val base64: String)

@Serializable
data class Member(
    val id: Int,
    val referenceId: String = "I$id",
    val name: MemberName = MemberName(),
    val gender: String = "",
    val births: List<MemberEvent> = listOf(MemberEvent(tag = "BIRT", type = "BIRT")),
    val deaths: List<MemberEvent> = listOf(MemberEvent(tag = "DEAT", type = "DEAT")),
    val events: List<MemberEvent> = emptyList(),
    val parents: List<ParentFamily> = emptyList(),
    val spouses: List<SpouseFamily> = emptyList(),
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val addresses: List<String> = emptyList(),
    val notes: List<MemberNote> = emptyList(),
    val fields: List<MemberField> = emptyList(),
    val additionalInfo: String = "",
    val gallery: List<MemberImage> = emptyList(),
    val familyGroupIds: List<Int> = emptyList()
) {
    val displayName: String
        get() = name.fullName.ifBlank {
            listOf(name.prefix, name.givenName, name.surnamePrefix, name.surname, name.suffix)
                .filter { it.isNotBlank() }.joinToString(" ")
        }.ifBlank { "Member #$id" }
}

object MemberData {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun initialMembers(): List<Member> = List(20) { Member(id = it + 1) }

    fun encode(members: List<Member>): String = json.encodeToString(members)

    fun decode(content: String): List<Member> =
        json.decodeFromString<List<Member>>(content).also { members ->
            require(members.map { it.id }.distinct().size == members.size) {
                "Duplicate member IDs in saved data"
            }
        }

    fun update(members: List<Member>, member: Member): List<Member> {
        require(members.any { it.id == member.id }) { "Member not found" }
        val validIds = members.map { it.id }.toSet() - member.id
        member.parents.forEach {
            require(it.motherId == null || it.motherId in validIds) { "Select a valid mother" }
            require(it.fatherId == null || it.fatherId in validIds) { "Select a valid father" }
            require(it.motherId == null || it.motherId != it.fatherId) {
                "Mother and father must be different members"
            }
        }
        require(member.parents.count { it.sealed } <= 1) { "Select only one LDS sealing family" }
        require(member.familyGroupIds.all { it > 0 }) { "Select a valid family group" }
        val spouseIds = member.spouses.mapNotNull { it.spouseId }
        require(spouseIds.all { it in validIds }) { "Select a valid spouse" }
        require(spouseIds.distinct().size == spouseIds.size) { "A spouse can only be selected once" }
        require(member.spouses.all { it.order.toIntOrNull()?.let { order -> order >= 0 } == true }) {
            "Spouse order must be a non-negative whole number"
        }
        val previous = members.first { it.id == member.id }
        val removedSpouses = previous.spouses.mapNotNull { it.spouseId }.toSet() - spouseIds.toSet()
        val updated = members.map { other ->
            when {
                other.id == member.id -> member
                other.id in spouseIds -> {
                    val family = member.spouses.first { it.spouseId == other.id }
                    val reciprocal = other.spouses.firstOrNull { it.spouseId == member.id }
                    other.copy(spouses = other.spouses.filterNot { it.spouseId == member.id } +
                        SpouseFamily(member.id, reciprocal?.order ?: "0", family.events))
                }
                other.id in removedSpouses -> other.copy(
                    spouses = other.spouses.filterNot { it.spouseId == member.id }
                )
                else -> other
            }
        }
        val byId = updated.associateBy { it.id }
        fun hasCycle(id: Int, path: Set<Int>): Boolean {
            if (id in path) return true
            return byId[id]?.parents.orEmpty()
                .flatMap { listOfNotNull(it.motherId, it.fatherId) }
                .any { hasCycle(it, path + id) }
        }
        require(!hasCycle(member.id, emptySet())) { "Parents cannot form an ancestry cycle" }
        return updated
    }

    fun children(members: List<Member>, memberId: Int, spouseId: Int?): List<Member> =
        members.filter { child ->
            child.parents.any { family ->
                val ids = listOfNotNull(family.motherId, family.fatherId)
                memberId in ids && (spouseId == null || spouseId in ids)
            }
        }

    fun siblings(members: List<Member>, memberId: Int, family: ParentFamily): List<Member> =
        members.filter { other ->
            other.id != memberId && other.parents.any {
                (family.motherId != null && family.motherId == it.motherId) ||
                    (family.fatherId != null && family.fatherId == it.fatherId)
            }
        }
}
