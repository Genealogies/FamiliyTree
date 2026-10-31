package com.hannes.familytree.gedcom

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.hannes.familytree.ui.tree.FamilyGraph
import com.hannes.familytree.ui.tree.LayoutEngine
import com.hannes.familytree.ui.tree.Person
import com.hannes.familytree.ui.tree.Union
import org.folg.gedcom.model.Gedcom
import org.folg.gedcom.parser.ModelParser

object GedcomImporter {
    /** Parses GEDCOM [content] into a laid-out [FamilyGraph], or null if it has no data. */
    fun import(content: String, density: Density): FamilyGraph? {
        val gedcom = ModelParser().parseGedcom(content.lineSequence()) ?: return null
        val graph = mapToFamilyGraph(gedcom)
        if (graph.persons.isEmpty()) return null
        LayoutEngine.calculateLayout(graph, with(density) { 150.dp.toPx() }, with(density) { 160.dp.toPx() })
        return graph
    }

    fun mapToFamilyGraph(gedcom: Gedcom): FamilyGraph {
        val persons = mutableMapOf<String, Person>()
        val unions = mutableListOf<Union>()

        gedcom.getPeople().forEach { person ->
            val id = person.id ?: return@forEach
            val name = person.getNames().firstOrNull()
                ?.let {
                    val parts = "${it.given ?: ""} ${it.surname ?: ""}".trim()
                    parts.ifEmpty { it.value.orEmpty().replace("/", " ").trim().replace(Regex("\\s+"), " ") }
                }
                ?.takeIf { it.isNotEmpty() } ?: "Unknown"
            val birthYear = person.getEventsFacts().find { it.tag == "BIRT" }?.date
                ?.let { Regex("\\d{4}").find(it)?.value?.toInt() }
            persons[id] = Person(id = id, name = name, birthYear = birthYear)
        }

        gedcom.getFamilies().forEach { family ->
            val id = family.id ?: return@forEach
            val spouseIds = (family._husbandRefs.orEmpty() + family._wifeRefs.orEmpty())
                .mapNotNull { it?.ref }.filter { it in persons }
            val childrenIds = family._childRefs.orEmpty().mapNotNull { it?.ref }.filter { it in persons }
            unions.add(Union(id = id, spouseIds = spouseIds, childrenIds = childrenIds))

            spouseIds.forEach { spouseId ->
                val person = persons.getValue(spouseId)
                spouseIds.filter { it != spouseId && it !in person.spouseIds }.forEach { person.spouseIds.add(it) }
                childrenIds.filter { it !in person.childrenIds }.forEach { person.childrenIds.add(it) }
            }
        }
        return FamilyGraph(persons, unions)
    }
}
