package com.hannes.familytree.ui.tree

import kotlin.math.max

/**
 * Computes [Person.x]/[Person.y]/[Person.generation] and [Union.x]/[Union.y]
 * for a [FamilyGraph] so it can be rendered, and sets [FamilyGraph.width]/
 * [FamilyGraph.height] to the resulting bounds.
 *
 * Ported from the equivalent engine in the FamilyTreeApp project.
 */
object LayoutEngine {

    // Helper class to store layout data without modifying the model directly until the end
    private class LayoutNode(
        val personId: String,
        val spouseId: String?,
        val children: MutableList<LayoutNode> = mutableListOf()
    ) {
        var width: Float = 0f         // The total width of this entire family branch
        var x: Float = 0f             // The final X position (top-left of the parent block)
        var y: Float = 0f
        var generation: Int = 0
    }

    fun calculateLayout(
        graph: FamilyGraph,
        xSpacing: Float, // Horizontal space between siblings/cousins
        ySpacing: Float, // Vertical space between generations
        nodeWidth: Float = 300f,
        nodeHeight: Float = 150f
    ) {
        if (graph.persons.isEmpty()) return

        // 1. Identify Roots (Persons with no parents in the graph)
        val childrenIds = graph.unions.flatMap { it.childrenIds }.toSet()
        val allIds = graph.persons.keys
        // Roots are those not found in any children list.
        val potentialRoots = allIds.filter { it !in childrenIds }

        val visited = mutableSetOf<String>()
        val roots = mutableListOf<LayoutNode>()

        // Sort roots by birth year to keep older branches on the left
        val sortedRootIds = potentialRoots.sortedBy { graph.persons[it]?.birthYear ?: 0 }

        sortedRootIds.forEach { id ->
            if (id !in visited) {
                // Check if this is a spouse of an already processed node
                val person = graph.persons[id]
                val spouseId = person?.spouseIds?.firstOrNull()

                if (spouseId == null || spouseId !in visited) {
                    val rootNode = buildTree(id, graph, visited)
                    roots.add(rootNode)
                }
            }
        }

        // 2. Measure Subtrees (Bottom-Up)
        roots.forEach { root ->
            measureSubtree(root, nodeWidth, xSpacing)
        }

        // 3. Assign Positions (Top-Down)
        var currentX = 0f
        roots.forEach { root ->
            layoutSubtree(root, currentX, 0f, xSpacing, ySpacing)
            currentX += root.width + xSpacing
        }

        // 4. Update the Graph Objects
        updateGraphCoordinates(roots, graph, nodeWidth)
        updateUnionPositions(graph)

        // 5. Final Graph Normalization (Padding & Centering)
        normalizeGraph(graph, nodeWidth, nodeHeight)
    }

    // --- Step 1: Build the Tree Structure ---
    private fun buildTree(
        personId: String,
        graph: FamilyGraph,
        visited: MutableSet<String>
    ): LayoutNode {
        visited.add(personId)
        val person = graph.persons[personId]!!

        // Identify Spouse (Primary spouse for layout purposes)
        val spouseId = person.spouseIds.firstOrNull()
        if (spouseId != null) visited.add(spouseId)

        val node = LayoutNode(personId, spouseId)

        // Find Children: unions where this person (or their spouse) is a parent
        val relevantUnions = graph.unions.filter {
            it.spouseIds.contains(personId) || (spouseId != null && it.spouseIds.contains(spouseId))
        }

        val childrenIds = relevantUnions.flatMap { it.childrenIds }
            .distinct()
            .filter { it !in visited } // Prevent cycles or double-placement
            .sortedBy { graph.persons[it]?.birthYear ?: 0 }

        childrenIds.forEach { childId ->
            node.children.add(buildTree(childId, graph, visited))
        }

        return node
    }

    // --- Step 2: Measure Widths ---
    private fun measureSubtree(node: LayoutNode, nodeWidth: Float, xSpacing: Float) {
        var childrenTotalWidth = 0f
        if (node.children.isNotEmpty()) {
            node.children.forEach { child ->
                measureSubtree(child, nodeWidth, xSpacing)
                childrenTotalWidth += child.width
            }
            childrenTotalWidth += (node.children.size - 1) * xSpacing
        }

        // Single person = nodeWidth. Couple = nodeWidth * 2 + small gap.
        val spouseGap = xSpacing / 2f
        val parentsWidth = if (node.spouseId != null) (nodeWidth * 2) + spouseGap else nodeWidth

        // Node Width is the MAX of parents or children
        node.width = max(parentsWidth, childrenTotalWidth)
    }

    // --- Step 3: Layout (Coordinate Assignment) ---
    private fun layoutSubtree(
        node: LayoutNode,
        x: Float,
        y: Float,
        xSpacing: Float,
        ySpacing: Float
    ) {
        node.x = x
        node.y = y

        if (node.children.isNotEmpty()) {
            // Center the children block relative to the parents block
            var childrenBlockWidth = 0f
            node.children.forEach { childrenBlockWidth += it.width }
            childrenBlockWidth += (node.children.size - 1) * xSpacing

            var childX = x
            if (node.width > childrenBlockWidth) {
                childX += (node.width - childrenBlockWidth) / 2
            }

            node.children.forEach { child ->
                layoutSubtree(child, childX, y + ySpacing, xSpacing, ySpacing)
                childX += child.width + xSpacing
            }
        }
    }

    // --- Step 4: Map back to Graph ---
    private fun updateGraphCoordinates(roots: List<LayoutNode>, graph: FamilyGraph, nodeWidth: Float) {
        fun visit(node: LayoutNode) {
            val person = graph.persons[node.personId]
            val spouse = if (node.spouseId != null) graph.persons[node.spouseId] else null

            val spouseGap = nodeWidth / 6f
            val parentsWidth = if (spouse != null) nodeWidth * 2 + spouseGap else nodeWidth

            // Center the Parent Unit within the total Node Width
            val centerOffset = (node.width - parentsWidth) / 2
            val startX = node.x + centerOffset

            if (person != null) {
                person.x = startX
                person.y = node.y
            }
            if (spouse != null) {
                spouse.x = startX + nodeWidth + spouseGap
                spouse.y = node.y
            }

            node.children.forEach { visit(it) }
        }

        roots.forEach { visit(it) }
    }

    private fun updateUnionPositions(graph: FamilyGraph) {
        graph.unions.forEach { union ->
            val spouses = union.spouseIds.mapNotNull { graph.persons[it] }
            if (spouses.isNotEmpty()) {
                val minX = spouses.minOf { it.x }
                val maxX = spouses.maxOf { it.x }
                union.x = minX + (maxX - minX) / 2 // Exact center
                union.y = spouses.first().y // Same level
            }
        }
    }

    private fun normalizeGraph(graph: FamilyGraph, nodeWidth: Float, nodeHeight: Float) {
        if (graph.persons.isEmpty()) return
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        graph.persons.values.forEach {
            minX = minOf(minX, it.x)
            minY = minOf(minY, it.y)
            maxX = maxOf(maxX, it.x + nodeWidth)
            maxY = maxOf(maxY, it.y + nodeHeight)
        }
        val padding = 100f
        val shiftX = -minX + padding
        val shiftY = -minY + padding

        graph.persons.values.forEach { it.x += shiftX; it.y += shiftY }
        graph.unions.forEach { it.x += shiftX; it.y += shiftY }

        graph.width = (maxX - minX) + 2 * padding
        graph.height = (maxY - minY) + 2 * padding
    }
}
