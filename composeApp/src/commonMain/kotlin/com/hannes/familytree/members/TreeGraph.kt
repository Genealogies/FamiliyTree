package com.hannes.familytree.members

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hannes.familytree.ui.tree.FamilyGraph
import com.hannes.familytree.ui.tree.LayoutEngine
import com.hannes.familytree.ui.tree.Person
import com.hannes.familytree.ui.tree.Union

fun GenealogyData.treeGraph(tree: Tree, density: Density): FamilyGraph {
    val candidates = members.filter {
        tree.familyGroupId == null || tree.familyGroupId in it.familyGroupIds
    }.associateBy { it.id }
    val selected = mutableSetOf<Int>()
    val root = tree.rootId
    if (root == null) {
        selected.addAll(candidates.keys)
    } else {
        fun walk(start: Int, limit: Int, ancestors: Boolean) {
            var frontier = setOf(start)
            var level = 0
            val visited = mutableSetOf<Int>()
            while (frontier.isNotEmpty() && (limit == -1 || level <= limit)) {
                selected.addAll(frontier)
                visited.addAll(frontier)
                frontier = frontier.flatMap { id ->
                    if (ancestors) candidates[id]?.parents.orEmpty()
                        .flatMap { listOfNotNull(it.motherId, it.fatherId) }
                    else candidates.values.filter { child ->
                        child.parents.any { it.motherId == id || it.fatherId == id }
                    }.map { it.id }.filter {
                        TreeSettings.value(tree, "female_tree") != "on" || candidates[id]?.gender != "F"
                    }
                }.filter { it in candidates && it !in visited }.toSet()
                level++
            }
        }
        walk(root, TreeSettings.value(tree, "generation_number").toInt(), ancestors = false)
        if (TreeSettings.value(tree, "ancestor") == "on") {
            walk(root, TreeSettings.value(tree, "generation_number_ancestor").toInt(), ancestors = true)
        }
        if (TreeSettings.value(tree, "hide_spouse") != "on") {
            val coParents = selected.toList().flatMap { id ->
                candidates[id]?.parents.orEmpty().flatMap { family ->
                    val parents = listOfNotNull(family.motherId, family.fatherId)
                    if (parents.any { it in selected }) parents else emptyList()
                }
            }
            selected.addAll(selected.toList().flatMap { id ->
                candidates[id]?.spouses.orEmpty().mapNotNull { it.spouseId }
            }.plus(coParents).filter { it in candidates })
        }
    }
    fun dateLabel(events: List<MemberEvent>, mode: String): String {
        val date = events.firstOrNull { it.date.isNotBlank() }?.date.orEmpty()
        return when (mode) {
            "none" -> ""
            "year" -> Regex("\\d{4}").find(date)?.value.orEmpty()
            else -> date
        }
    }
    val persons = selected.mapNotNull { id ->
        candidates[id]?.let { member ->
            val name = when (TreeSettings.value(tree, "name")) {
                "first" -> member.name.givenName.ifBlank { member.displayName.substringBefore(" ") }
                else -> member.displayName
            }
            val deceased = member.deaths.any { it.asserted || it.date.isNotBlank() || it.place.isNotBlank() }
            val birth = if (!deceased && TreeSettings.value(tree, "birt_hide_alive") == "on") ""
                else dateLabel(member.births, TreeSettings.value(tree, "birt"))
            val death = dateLabel(member.deaths, TreeSettings.value(tree, "deat"))
            val gender = when (TreeSettings.value(tree, "gender")) {
                "none" -> ""
                "full" -> when (member.gender) { "M" -> "Male"; "F" -> "Female"; else -> "" }
                else -> member.gender
            }
            val label = listOf(name, gender, birth.takeIf { it.isNotBlank() }?.let { "b. $it" },
                death.takeIf { it.isNotBlank() }?.let { "d. $it" }).filterNotNull()
                .filter { it.isNotBlank() }.joinToString("\n")
            id.toString() to Person(id.toString(), label)
        }
    }.toMap()
    val families = linkedMapOf<Pair<Int?, Int?>, MutableList<Int>>()
    candidates.values.filter { it.id in selected }.forEach { child ->
        child.parents.forEach { parents ->
            val mother = parents.motherId?.takeIf { it in selected }
            val father = parents.fatherId?.takeIf { it in selected }
            if (mother != null || father != null) families.getOrPut(mother to father) { mutableListOf() }
                .add(child.id)
        }
    }
    val unions = families.entries.mapIndexed { index, (parents, children) ->
        val spouses = listOfNotNull(parents.first, parents.second).map { it.toString() }
        val childIds = children.distinct().map { it.toString() }
        spouses.forEach { id ->
            persons.getValue(id).childrenIds.addAll(childIds)
            persons.getValue(id).spouseIds.addAll(spouses.filter { it != id })
        }
        Union("F$index", spouses, childIds)
    }.toMutableList()
    val paired = mutableSetOf<Set<String>>()
    candidates.values.filter { it.id in selected }.forEach { member ->
        member.spouses.forEach { spouse ->
            val ids = listOf(member.id.toString(), spouse.spouseId?.toString())
                .filterNotNull().filter { it in persons }
            if (ids.size == 2 && paired.add(ids.toSet()) &&
                unions.none { it.spouseIds.toSet() == ids.toSet() }) {
                unions.add(Union("S${unions.size}", ids))
                ids.forEach { persons.getValue(it).spouseIds.addAll(ids.filter { id -> id != it }) }
            }
        }
    }
    val graph = FamilyGraph(persons, unions)
    LayoutEngine.calculateLayout(graph, with(density) { 150.dp.toPx() }, with(density) { 160.dp.toPx() })
    if (TreeSettings.value(tree, "layout") == "hr") {
        graph.persons.values.forEach { person ->
            val x = person.x
            person.x = person.y
            person.y = x
        }
        graph.unions.forEach { union ->
            val x = union.x
            union.x = union.y
            union.y = x
        }
        val width = graph.width
        graph.width = graph.height
        graph.height = width
    }
    return graph
}
