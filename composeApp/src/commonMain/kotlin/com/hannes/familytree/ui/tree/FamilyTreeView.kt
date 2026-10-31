package com.hannes.familytree.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** Pan/zoom state for a [LazyFamilyTree]. */
@Stable
class FamilyTreeState(
    initialScale: Float = 1f,
    initialOffset: Offset = Offset.Zero
) {
    var scale by mutableStateOf(initialScale)
    var offset by mutableStateOf(initialOffset)

    fun onTransform(centroid: Offset, zoomChange: Float, panChange: Offset) {
        val newScale = (scale * zoomChange).coerceIn(0.1f, 3f)
        val scaleChange = newScale / scale
        offset = (offset - centroid) * scaleChange + centroid + panChange
        scale = newScale
    }
}

@Composable
fun rememberFamilyTreeState() = remember { FamilyTreeState() }

/**
 * Renders a [FamilyGraph] laid out by [LayoutEngine.calculateLayout] as a
 * pannable, pinch-zoomable canvas. Only persons within (an inflated) viewport
 * are subcomposed, so the tree scales to large families.
 */
@Composable
fun LazyFamilyTree(
    graph: FamilyGraph,
    modifier: Modifier = Modifier,
    nodeWidth: Dp = 120.dp,
    nodeHeight: Dp = 70.dp,
    state: FamilyTreeState = rememberFamilyTreeState(),
    nodeContent: @Composable (Person) -> Unit = { DefaultPersonCard(it) }
) {
    val density = LocalDensity.current
    val nodeWidthPx = with(density) { nodeWidth.toPx() }
    val nodeHeightPx = with(density) { nodeHeight.toPx() }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())

        // Visible area in graph coordinates: (viewport boundary - offset) / scale
        val visibleRect = Rect(
            left = -state.offset.x / state.scale,
            top = -state.offset.y / state.scale,
            right = (viewportSize.width - state.offset.x) / state.scale,
            bottom = (viewportSize.height - state.offset.y) / state.scale
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        state.onTransform(centroid, zoom, pan)
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = state.scale,
                        scaleY = state.scale,
                        translationX = state.offset.x,
                        translationY = state.offset.y,
                        transformOrigin = TransformOrigin(0f, 0f)
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawUnionLines(graph, nodeWidthPx, nodeHeightPx)
                }

                FamilyTreeSubcomposer(
                    graph = graph,
                    visibleRect = visibleRect,
                    nodeWidth = nodeWidth,
                    nodeHeight = nodeHeight,
                    nodeContent = nodeContent
                )
            }
        }
    }
}

@Composable
private fun FamilyTreeSubcomposer(
    graph: FamilyGraph,
    visibleRect: Rect,
    nodeWidth: Dp,
    nodeHeight: Dp,
    nodeContent: @Composable (Person) -> Unit
) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        // Identify persons within the viewport (with a small buffer/margin)
        val buffer = 100f
        val visiblePersons = graph.persons.values.filter { person ->
            val personRect = Rect(person.x, person.y, person.x + 400f, person.y + 200f) // approximate max size
            visibleRect.inflate(buffer).overlaps(personRect)
        }

        val nodePlaceables = visiblePersons.map { person ->
            val placeable = subcompose(person.id) {
                Box(modifier = Modifier.size(nodeWidth, nodeHeight)) {
                    nodeContent(person)
                }
            }.map { it.measure(Constraints()) }

            person to placeable
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            nodePlaceables.forEach { (person, placeables) ->
                placeables.forEach { placeable ->
                    placeable.place(person.x.roundToInt(), person.y.roundToInt())
                }
            }
        }
    }
}

/** Draws union connector lines: spouse links, and parent/child "bus" lines. */
private fun DrawScope.drawUnionLines(
    graph: FamilyGraph,
    nodeWidthPx: Float,
    nodeHeightPx: Float
) {
    val centerX = nodeWidthPx / 2
    val centerY = nodeHeightPx / 2

    graph.unions.forEach { union ->
        val spouseIds = union.spouseIds
        if (spouseIds.isEmpty()) return@forEach

        val s1 = graph.persons[spouseIds[0]] ?: return@forEach
        val s2 = if (spouseIds.size > 1) graph.persons[spouseIds[1]] else null
        val unionColor = if (union.isIncestuous) Color.Red else Color.Black
        val unionStroke = if (union.isIncestuous) 4f else 3f

        // 1. Connector between spouses
        if (s2 != null) {
            drawLine(
                color = unionColor,
                start = Offset(s1.x + centerX, s1.y + centerY),
                end = Offset(s2.x + centerX, s2.y + centerY),
                strokeWidth = unionStroke
            )
        }

        // 2. Lines to children
        val children = union.childrenIds.mapNotNull { graph.persons[it] }
        if (children.isNotEmpty()) {
            val childYTop = children.first().y - 30f // Height of the horizontal bus line

            val minChildX = children.minOf { it.x }
            val maxChildX = children.maxOf { it.x }

            // Ensure the bus reaches the union stem, even if parents are offset
            val busLeft = minOf(minChildX, union.x)
            val busRight = maxOf(maxChildX, union.x)

            // Vertical stem from union to bus
            drawLine(
                color = unionColor,
                start = Offset(union.x + centerX, union.y + centerY),
                end = Offset(union.x + centerX, childYTop),
                strokeWidth = unionStroke
            )

            // Horizontal bus
            drawLine(
                color = unionColor,
                start = Offset(busLeft + centerX, childYTop),
                end = Offset(busRight + centerX, childYTop),
                strokeWidth = 2f
            )

            // Vertical stems to children
            children.forEach { child ->
                val isAdopted = s1.adoptiveChildrenIds.contains(child.id) ||
                        (s2?.adoptiveChildrenIds?.contains(child.id) == true)

                val pathEffect = if (isAdopted) PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f) else null

                drawLine(
                    color = unionColor,
                    start = Offset(child.x + centerX, childYTop),
                    end = Offset(child.x + centerX, child.y),
                    strokeWidth = 2f,
                    pathEffect = pathEffect
                )
            }
        }
    }
}

@Composable
fun DefaultPersonCard(person: Person) {
    Card(modifier = Modifier.fillMaxSize().border(1.dp, Color.Gray)) {
        Column(
            modifier = Modifier.padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = person.name, style = MaterialTheme.typography.bodySmall)
            if (person.birthYear != null) {
                Text(text = person.birthYear.toString(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
