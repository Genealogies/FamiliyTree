package com.hannes.familytree.ui.tree

/**
 * A single person rendered as a node in the family tree.
 *
 * [x]/[y] and [generation] are layout output, computed by [LayoutEngine] and
 * not meant to be set manually.
 */
data class Person(
    val id: String,
    val name: String,
    val birthYear: Int? = null,
    val spouseIds: MutableList<String> = mutableListOf(),
    val childrenIds: MutableList<String> = mutableListOf(),
    val adoptiveChildrenIds: MutableList<String> = mutableListOf(),
    var generation: Int = 0,
    var x: Float = 0f,
    var y: Float = 0f
)

/**
 * A couple (or single parent) and the children produced by that union.
 *
 * [x]/[y] are layout output, computed by [LayoutEngine] and not meant to be
 * set manually.
 */
data class Union(
    val id: String,
    val spouseIds: List<String>,
    val childrenIds: List<String> = emptyList(),
    val isIncestuous: Boolean = false,
    var x: Float = 0f,
    var y: Float = 0f
)

/**
 * The full set of persons and unions to render. [width]/[height] are the
 * computed bounds of the laid-out graph, set by [LayoutEngine.calculateLayout].
 */
class FamilyGraph(
    val persons: Map<String, Person>,
    val unions: List<Union>
) {
    var width: Float = 0f
    var height: Float = 0f
}
