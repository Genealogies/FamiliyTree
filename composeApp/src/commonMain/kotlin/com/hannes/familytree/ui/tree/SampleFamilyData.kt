package com.hannes.familytree.ui.tree

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

/**
 * Builds a small demonstration [FamilyGraph] showcasing the rendering
 * conventions: a married couple with children, an adoptive child (dashed
 * connector), and an incestuous union (red connector).
 */
fun sampleFamilyGraph(density: Density): FamilyGraph {
    val p1 = Person("1", "John", 1950)
    val p2 = Person("2", "Mary", 1952)
    val p3 = Person("3", "Child1", 1980)
    val p4 = Person("4", "Child2", 1985)
    val aunt = Person("5", "Aunt", 1982)
    val nephew = Person("6", "Nephew", 2005)
    val adoptedChild = Person("7", "Adopted", 2010)

    p1.spouseIds.add(p2.id)
    p2.spouseIds.add(p1.id)
    p1.childrenIds.addAll(listOf(p3.id, p4.id, aunt.id))
    p2.childrenIds.addAll(listOf(p3.id, p4.id, aunt.id))

    p3.childrenIds.add(nephew.id)

    // Aunt marries Nephew (incestuous union)
    aunt.spouseIds.add(nephew.id)
    nephew.spouseIds.add(aunt.id)

    // Adoption
    p4.adoptiveChildrenIds.add(adoptedChild.id)

    val u1 = Union("u1", listOf(p1.id, p2.id), listOf(p3.id, p4.id, aunt.id))
    val u2 = Union("u2", listOf(aunt.id, nephew.id), emptyList(), isIncestuous = true)
    val u3 = Union("u3", listOf(p4.id), listOf(adoptedChild.id))
    val u4 = Union("u4", listOf(p3.id), listOf(nephew.id))

    val persons = listOf(p1, p2, p3, p4, aunt, nephew, adoptedChild).associateBy { it.id }
    val graph = FamilyGraph(persons, listOf(u1, u2, u3, u4))

    val xSpacing = with(density) { 150.dp.toPx() }
    val ySpacing = with(density) { 160.dp.toPx() }
    LayoutEngine.calculateLayout(graph, xSpacing, ySpacing)

    return graph
}
