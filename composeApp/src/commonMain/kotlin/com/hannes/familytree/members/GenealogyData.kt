package com.hannes.familytree.members

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class FamilyGroup(
    val id: Int,
    val name: String = "",
    val slug: String = "",
    val description: String = "",
    val defaultTreeId: Int? = null
)

@Serializable
data class Tree(
    val id: Int,
    val title: String = "",
    val familyGroupId: Int? = null,
    val rootId: Int? = null,
    val settings: Map<String, String> = emptyMap()
)

@Serializable
data class GenealogyData(
    val members: List<Member> = emptyList(),
    val groups: List<FamilyGroup> = emptyList(),
    val trees: List<Tree> = emptyList()
) {
    fun saveMember(member: Member): GenealogyData {
        require(member.familyGroupIds.all { id -> groups.any { it.id == id } }) {
            "Select an existing family group"
        }
        val source = if (members.any { it.id == member.id }) members else members + Member(member.id)
        val updated = copy(members = MemberData.update(source, member))
        updated.validate()
        return updated
    }

    fun saveGroup(group: FamilyGroup): GenealogyData {
        require(group.name.isNotBlank()) { "Name is required" }
        val slug = group.slug.ifBlank { slugFor(group.name) }
        require(slug.isNotBlank() && slug.none { it.isWhitespace() || it == '/' }) {
            "Slug must not be empty or contain spaces or slashes"
        }
        require(groups.none { it.id != group.id && it.slug.equals(slug, true) }) {
            "A family group with this slug already exists"
        }
        val updated = copy(groups = groups.filterNot { it.id == group.id } + group.copy(slug = slug))
        updated.validate()
        return updated
    }

    fun saveTree(tree: Tree): GenealogyData {
        require(tree.title.isNotBlank()) { "Title is required" }
        TreeSettings.validate(tree.settings)
        val updated = copy(trees = trees.filterNot { it.id == tree.id } + tree)
        updated.validate()
        return updated
    }

    fun deleteMember(id: Int): GenealogyData = copy(
        members = members.filterNot { it.id == id }.map { member ->
            member.copy(
                parents = member.parents.map { it.copy(
                    motherId = it.motherId.takeUnless { parent -> parent == id },
                    fatherId = it.fatherId.takeUnless { parent -> parent == id }
                ) },
                spouses = member.spouses.filterNot { it.spouseId == id }
            )
        },
        trees = trees.map { it.copy(rootId = it.rootId.takeUnless { root -> root == id }) }
    )

    fun deleteGroup(id: Int): GenealogyData = copy(
        groups = groups.filterNot { it.id == id },
        members = members.map { it.copy(familyGroupIds = it.familyGroupIds - id) },
        trees = trees.map { tree ->
            if (tree.familyGroupId == id) tree.copy(familyGroupId = null, rootId = null) else tree
        }
    )

    fun deleteTree(id: Int): GenealogyData = copy(
        trees = trees.filterNot { it.id == id },
        groups = groups.map { it.copy(defaultTreeId = it.defaultTreeId.takeUnless { tree -> tree == id }) }
    )

    fun possibleRoots(groupId: Int): List<Member> = members.filter { member ->
        groupId in member.familyGroupIds && member.parents.all {
            it.motherId == null && it.fatherId == null
        }
    }

    fun generateDefaultTree(groupId: Int): GenealogyData {
        val group = groups.first { it.id == groupId }
        val tree = Tree(
            id = (trees.maxOfOrNull { it.id } ?: 0) + 1,
            title = "${group.name} Tree",
            familyGroupId = groupId,
            rootId = possibleRoots(groupId).firstOrNull()?.id
        )
        return saveTree(tree).saveGroup(group.copy(defaultTreeId = tree.id))
    }

    fun validate() {
        require(members.all { it.id > 0 } && groups.all { it.id > 0 } && trees.all { it.id > 0 }) {
            "Record IDs must be positive"
        }
        require(members.map { it.id }.distinct().size == members.size) { "Duplicate member IDs" }
        require(groups.map { it.id }.distinct().size == groups.size) { "Duplicate group IDs" }
        require(trees.map { it.id }.distinct().size == trees.size) { "Duplicate tree IDs" }
        members.forEach { member ->
            require(member.familyGroupIds.all { id -> groups.any { it.id == id } }) {
                "Member references a missing family group"
            }
        }
        trees.forEach { tree ->
            TreeSettings.validate(tree.settings)
            val groupId = tree.familyGroupId
            require(groupId == null || groups.any { it.id == groupId }) { "Select a valid family" }
            require(tree.rootId == null || members.any {
                it.id == tree.rootId && (groupId == null || groupId in it.familyGroupIds)
            }) { "Root must be a member of the selected family" }
        }
        groups.forEach { group ->
            require(group.defaultTreeId == null || trees.any {
                it.id == group.defaultTreeId && it.familyGroupId == group.id
            }) { "Default tree must belong to this family group" }
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun encode(data: GenealogyData): String = json.encodeToString(data)

        fun decode(content: String): GenealogyData {
            val data = if (content.trimStart().startsWith("[")) {
                val members = MemberData.decode(content)
                val groups = members.flatMap { it.familyGroupIds }.distinct().map {
                    FamilyGroup(it, "Family Group #$it", "family-group-$it")
                }
                GenealogyData(members, groups)
            } else json.decodeFromString<GenealogyData>(content)
            data.validate()
            return data
        }

        fun screenshotData() = GenealogyData(
            members = MemberData.initialMembers(),
            groups = listOf(FamilyGroup(1, "Family Group #1", "family-group-1")),
            trees = listOf(Tree(1, "Tree #1", familyGroupId = 1))
        )

        private fun slugFor(name: String): String =
            name.trim().lowercase().map { if (it.isLetterOrDigit()) it else '-' }
                .joinToString("").replace(Regex("-+"), "-").trim('-')
    }
}

fun MemberStorage.loadGenealogy(): GenealogyData = read()?.let(GenealogyData::decode) ?: GenealogyData()

fun MemberStorage.saveGenealogy(data: GenealogyData) {
    data.validate()
    write(GenealogyData.encode(data))
}
