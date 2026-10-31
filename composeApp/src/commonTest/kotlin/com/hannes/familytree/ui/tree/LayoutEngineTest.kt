package com.hannes.familytree.ui.tree

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LayoutEngineTest {
    @Test
    fun placesChildBelowParentsAndCentersUnion() {
        val father = Person("father", "Father", 1950)
        val mother = Person("mother", "Mother", 1952)
        val child = Person("child", "Child", 1980)
        father.spouseIds.add(mother.id)
        mother.spouseIds.add(father.id)
        father.childrenIds.add(child.id)
        mother.childrenIds.add(child.id)

        val union = Union("u1", listOf(father.id, mother.id), listOf(child.id))
        val graph = FamilyGraph(
            mapOf(father.id to father, mother.id to mother, child.id to child),
            listOf(union)
        )

        LayoutEngine.calculateLayout(graph, xSpacing = 50f, ySpacing = 160f, nodeWidth = 300f, nodeHeight = 150f)

        // Parents sit on the same generation row, child one generation below.
        assertEquals(father.y, mother.y)
        assertTrue(child.y > father.y)

        // The union sits centered horizontally between the two spouses.
        assertEquals((father.x + mother.x) / 2, union.x)

        // The graph bounds grow to cover every placed node.
        assertTrue(graph.width > 0f)
        assertTrue(graph.height > 0f)
    }

    @Test
    fun emptyGraphIsLeftUntouched() {
        val graph = FamilyGraph(emptyMap(), emptyList())
        LayoutEngine.calculateLayout(graph, xSpacing = 50f, ySpacing = 160f)
        assertEquals(0f, graph.width)
        assertEquals(0f, graph.height)
    }
}
