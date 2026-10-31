package com.hannes.familytree.ui.tree

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    focusedPersonId: String? = null,
    onPersonClick: (Person) -> Unit = {},
    nodeContent: @Composable (Person) -> Unit = { DefaultPersonCard(it) }
) {
    val density = LocalDensity.current
    val nodeWidthPx = with(density) { nodeWidth.toPx() }
    val nodeHeightPx = with(density) { nodeHeight.toPx() }
    val graphWidthPx = maxOf(
        graph.width,
        graph.persons.values.maxOfOrNull { it.x + nodeWidthPx } ?: 0f
    )
    val graphHeightPx = maxOf(
        graph.height,
        graph.persons.values.maxOfOrNull { it.y + nodeHeightPx } ?: 0f
    )
    val graphWidth = with(density) { graphWidthPx.toDp() }.coerceAtLeast(nodeWidth)
    val graphHeight = with(density) { graphHeightPx.toDp() }.coerceAtLeast(nodeHeight)
    val initialPersonId = graph.persons.values
        .minWithOrNull(compareBy<Person> { it.y }.thenBy { it.x })
        ?.id
    var internallyFocusedPersonId by remember(graph) {
        mutableStateOf(focusedPersonId ?: initialPersonId)
    }
    val activeFocusedPersonId = focusedPersonId ?: internallyFocusedPersonId

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewportSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        val focusedPerson = activeFocusedPersonId?.let(graph.persons::get)

        LaunchedEffect(activeFocusedPersonId, focusedPerson?.x, focusedPerson?.y, viewportSize, state) {
            if (focusedPerson != null) {
                state.offset = Offset(
                    viewportSize.width / 2f - (focusedPerson.x + nodeWidthPx / 2f) * state.scale,
                    viewportSize.height / 2f - (focusedPerson.y + nodeHeightPx / 2f) * state.scale
                )
            }
        }

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
                    .align(Alignment.TopStart)
                    .requiredSize(graphWidth, graphHeight)
                    .graphicsLayer(
                        scaleX = state.scale,
                        scaleY = state.scale,
                        translationX = state.offset.x,
                        translationY = state.offset.y,
                        transformOrigin = TransformOrigin(0f, 0f)
                    )
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawUnionLines(graph, visibleRect, nodeWidthPx, nodeHeightPx)
                }

                FamilyTreeSubcomposer(
                    graph = graph,
                    visibleRect = visibleRect,
                    nodeWidth = nodeWidth,
                    nodeHeight = nodeHeight,
                    focusedPersonId = activeFocusedPersonId,
                    onPersonClick = { person ->
                        if (focusedPersonId == null) {
                            internallyFocusedPersonId = person.id
                        }
                        onPersonClick(person)
                    },
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
    focusedPersonId: String?,
    onPersonClick: (Person) -> Unit,
    nodeContent: @Composable (Person) -> Unit
) {
    SubcomposeLayout(modifier = Modifier.fillMaxSize()) { constraints ->
        val nodeWidthPx = nodeWidth.toPx()
        val nodeHeightPx = nodeHeight.toPx()
        val bufferedVisibleRect = visibleRect.inflate(100f)
        val visiblePersons = graph.persons.values.filter { person ->
            bufferedVisibleRect.overlaps(
                Rect(
                    person.x,
                    person.y,
                    person.x + nodeWidthPx,
                    person.y + nodeHeightPx
                )
            )
        }
        val nodePlaceables = visiblePersons.map { person ->
            val widthPx = nodeWidth.roundToPx()
            val heightPx = nodeHeight.roundToPx()
            val placeables = subcompose(person.id) {
                Box(
                    Modifier
                        .size(nodeWidth, nodeHeight)
                        .then(
                            if (person.id == focusedPersonId) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.primary)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onPersonClick(person) }
                ) {
                    nodeContent(person)
                }
            }.map {
                it.measure(
                    Constraints(
                        minWidth = widthPx,
                        maxWidth = widthPx,
                        minHeight = heightPx,
                        maxHeight = heightPx
                    )
                )
            }
            person to placeables
        }

        layout(constraints.maxWidth, constraints.maxHeight) {
            nodePlaceables.forEach { (person, placeables) ->
                placeables.forEach { placeable ->
                    placeable.placeRelative(person.x.roundToInt(), person.y.roundToInt())
                }
            }
        }
    }
}

/** Draws union connector lines: spouse junction, and parent/child branch lines. */
private fun DrawScope.drawUnionLines(
    graph: FamilyGraph,
    visibleRect: Rect,
    nodeWidthPx: Float,
    nodeHeightPx: Float
) {
    val centerX = nodeWidthPx / 2
    val centerY = nodeHeightPx / 2
    val junctionRadius = 4.dp.toPx()
    val singleParentDrop = 12.dp.toPx()
    val childBranchGap = 20.dp.toPx()

    graph.unions.forEach { union ->
        val spouseIds = union.spouseIds
        if (spouseIds.isEmpty()) return@forEach

        val spouses = spouseIds.mapNotNull(graph.persons::get)
        if (spouses.isEmpty()) return@forEach
        val firstSpouse = spouses.first()
        val secondSpouse = spouses.getOrNull(1)
        val unionColor = if (union.isIncestuous) Color.Red else Color.DarkGray
        val unionStroke = if (union.isIncestuous) 4f else 2f
        val children = union.childrenIds.mapNotNull(graph.persons::get)
        val relatedPersons = spouses + children
        val familyBounds = Rect(
            left = relatedPersons.minOf { it.x },
            top = relatedPersons.minOf { it.y },
            right = relatedPersons.maxOf { it.x + nodeWidthPx },
            bottom = relatedPersons.maxOf { it.y + nodeHeightPx }
        )
        if (!visibleRect.inflate(200f).overlaps(familyBounds)) return@forEach

        val junction = if (secondSpouse != null) {
            val leftSpouse = if (firstSpouse.x <= secondSpouse.x) firstSpouse else secondSpouse
            val rightSpouse = if (leftSpouse === firstSpouse) secondSpouse else firstSpouse
            val leftCenterY = leftSpouse.y + centerY
            val rightCenterY = rightSpouse.y + centerY
            val lineStart = Offset(leftSpouse.x + nodeWidthPx, leftCenterY)
            val lineEnd = Offset(rightSpouse.x, rightCenterY)
            drawLine(
                color = unionColor,
                start = lineStart,
                end = lineEnd,
                strokeWidth = unionStroke
            )

            Offset(
                x = (lineStart.x + lineEnd.x) / 2f,
                y = (lineStart.y + lineEnd.y) / 2f
            )
        } else {
            Offset(firstSpouse.x + centerX, firstSpouse.y + nodeHeightPx + singleParentDrop)
        }

        if (children.isEmpty()) {
            if (secondSpouse != null) {
                drawCircle(unionColor, junctionRadius, junction)
            }
            return@forEach
        }

        val childYTop = children.minOf { it.y } - childBranchGap
        val parentBottom = firstSpouse.y + nodeHeightPx
        if (secondSpouse == null) {
            drawLine(
                color = unionColor,
                start = Offset(firstSpouse.x + centerX, parentBottom),
                end = Offset(junction.x, junction.y - junctionRadius),
                strokeWidth = unionStroke
            )
        }
        drawCircle(unionColor, junctionRadius, junction)

        val trunkStartY = junction.y + junctionRadius

        val firstChildX = children.minOf { it.x + centerX }
        val lastChildX = children.maxOf { it.x + centerX }
        drawLine(
            color = unionColor,
            start = Offset(junction.x, trunkStartY),
            end = Offset(junction.x, childYTop),
            strokeWidth = unionStroke
        )
        drawLine(
            color = Color.Gray,
            start = Offset(firstChildX, childYTop),
            end = Offset(lastChildX, childYTop),
            strokeWidth = 2f
        )

        children.forEach { child ->
            val isAdopted = firstSpouse.adoptiveChildrenIds.contains(child.id) ||
                secondSpouse?.adoptiveChildrenIds?.contains(child.id) == true
            drawLine(
                color = Color.Gray,
                start = Offset(child.x + centerX, childYTop),
                end = Offset(child.x + centerX, child.y),
                strokeWidth = 2f,
                pathEffect = if (isAdopted) {
                    PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                } else {
                    null
                }
            )
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
