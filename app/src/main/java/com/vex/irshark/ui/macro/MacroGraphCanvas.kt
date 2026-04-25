package com.vex.irshark.ui.macro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

// ─────────────────────────────────────────────────────────────────────────────
// Canvas constants  (ALL in canvas-px units; 1 canvas-px = 1 screen-px at zoom=1)
//
// Canvas coordinate system:
//   node.pos (Offset)  — top-left corner of block in canvas px
//   pan      (Offset)  — screen-px offset of the canvas origin
//   zoom     (Float)   — scale factor
//   screen_px = canvas_px * zoom + pan
//
// Block composables are sized as (BLOCK_W / screenDensity).dp so that the
// dp→px conversion (×density) cancels and physical pixel width = BLOCK_W px.
// ─────────────────────────────────────────────────────────────────────────────

const val BLOCK_W   = 300f  // block width  (canvas px)
const val BLOCK_H   = 180f  // block height (canvas px)
const val PIN_R     = 10f   // visual pin radius (canvas px)
const val GRID_STEP = 32f   // snap-to-grid step (canvas px)

private const val PIN_HIT_R     = 32f   // touch-hit radius for pins (canvas px)
private const val LONG_PRESS_MS = 450L  // long-press threshold (ms)

// ─────────────────────────────────────────────────────────────────────────────
// Canvas-coordinate helpers
// ─────────────────────────────────────────────────────────────────────────────

fun screenToCanvas(screen: Offset, pan: Offset, zoom: Float): Offset = (screen - pan) / zoom
fun canvasToScreen(canvas: Offset, pan: Offset, zoom: Float): Offset = canvas * zoom + pan

fun snapToGrid(pos: Offset): Offset =
    Offset((pos.x / GRID_STEP).roundToInt() * GRID_STEP,
           (pos.y / GRID_STEP).roundToInt() * GRID_STEP)

// ─────────────────────────────────────────────────────────────────────────────
// Per-node block dimensions (canvas-px)
// ─────────────────────────────────────────────────────────────────────────────

fun MacroNode.blockW(): Float = when (type) {
    MacroBlockType.START, MacroBlockType.END -> 200f
    MacroBlockType.IR_SEND -> {
        val len = (params as? BlockParams.IrSend)?.displayLabel?.length ?: 0
        (240f + len * 8f).coerceIn(240f, 480f)
    }
    MacroBlockType.DELAY   -> 240f
    else                   -> 300f   // SHOW_TEXT, WAIT_CONFIRM, IF_ELSE
}

fun MacroNode.blockH(): Float = when (type) {
    MacroBlockType.START, MacroBlockType.END -> 100f
    MacroBlockType.DELAY                     -> 120f
    MacroBlockType.SHOW_TEXT                 -> 180f
    else                                     -> 150f   // IR_SEND, WAIT_CONFIRM, IF_ELSE
}

// ─────────────────────────────────────────────────────────────────────────────
// Pin position helpers (canvas-local, relative to node.pos = top-left)
// ─────────────────────────────────────────────────────────────────────────────

fun inputPinOffset(blockW: Float = BLOCK_W): Offset = Offset(blockW / 2f, 0f)

fun outputPinOffset(pin: PinId, blockW: Float = BLOCK_W, blockH: Float = BLOCK_H): Offset =
    when (pin) {
        PinId.OUT -> Offset(blockW / 2f,    blockH)
        PinId.YES -> Offset(blockW * 0.28f, blockH)
        PinId.NO  -> Offset(blockW * 0.72f, blockH)
    }

fun absoluteOutPin(node: MacroNode, pin: PinId): Offset = node.pos + outputPinOffset(pin, node.blockW(), node.blockH())
fun absoluteInPin(node: MacroNode): Offset             = node.pos + inputPinOffset(node.blockW())

// ─────────────────────────────────────────────────────────────────────────────
// Edge drawing (cubic bezier)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawEdge(from: Offset, to: Offset, color: Color, strokeW: Float = 2.5f) {
    val dy = (to.y - from.y).coerceAtLeast(60f)
    val cp1 = from + Offset(0f, dy * 0.5f)
    val cp2 = to   - Offset(0f, dy * 0.5f)
    val path = Path().apply {
        moveTo(from.x, from.y)
        cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, to.x, to.y)
    }
    drawPath(path, color, style = Stroke(width = strokeW))
    drawCircle(color, radius = strokeW * 2f, center = to)
}

// ─────────────────────────────────────────────────────────────────────────────
// Block color + label
// ─────────────────────────────────────────────────────────────────────────────

fun blockColor(type: MacroBlockType): Color = when (type) {
    MacroBlockType.START        -> Color(0xFF1E8A5E)
    MacroBlockType.END          -> Color(0xFFBF3B3B)
    MacroBlockType.IR_SEND      -> Color(0xFF7B4DDF)
    MacroBlockType.DELAY        -> Color(0xFF2E7ADB)
    MacroBlockType.SHOW_TEXT    -> Color(0xFF1E9A6E)
    MacroBlockType.WAIT_CONFIRM -> Color(0xFFC27C1A)
    MacroBlockType.IF_ELSE      -> Color(0xFF1E8AA8)
}

fun blockLabel(type: MacroBlockType): String = when (type) {
    MacroBlockType.START        -> "START"
    MacroBlockType.END          -> "END"
    MacroBlockType.IR_SEND      -> "IR Send"
    MacroBlockType.DELAY        -> "Delay"
    MacroBlockType.SHOW_TEXT    -> "Show Text"
    MacroBlockType.WAIT_CONFIRM -> "Wait for OK"
    MacroBlockType.IF_ELSE      -> "If / Else"
}

// ─────────────────────────────────────────────────────────────────────────────
// The main graph canvas
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun MacroGraphCanvas(
    graph:            MacroGraph,
    onNodeTap:        (MacroNode) -> Unit,
    onNodeLongPress:  (MacroNode) -> Unit,
    onAddBlock:       (MacroBlockType, Offset) -> Unit,
    onDeleteSelected: () -> Unit,
    modifier:         Modifier = Modifier
) {
    val density = LocalDensity.current.density   // dp → px factor

    // ── Viewport ──────────────────────────────────────────────────────────
    var pan  by remember { mutableStateOf(Offset(40f, 60f)) }
    var zoom by remember { mutableStateOf(1f) }

    // ── Gesture state ─────────────────────────────────────────────────────
    var draggingId     by remember { mutableStateOf<String?>(null) }
    var dragNodeOffset by remember { mutableStateOf(Offset.Zero) }

    // ── Wire drawing
    var connectFromId  by remember { mutableStateOf<String?>(null) }
    var connectFromPin by remember { mutableStateOf(PinId.OUT) }
    var connectCursor  by remember { mutableStateOf(Offset.Zero) }
    var hoveredInputId by remember { mutableStateOf<String?>(null) }

    // ── Selection rect (canvas-px)
    var selStart by remember { mutableStateOf<Offset?>(null) }
    var selEnd   by remember { mutableStateOf<Offset?>(null) }

    var showAddMenu by remember { mutableStateOf(false) }
    val violet = MaterialTheme.colorScheme.primary

    // ── Hit-test helpers (canvas-px) ──────────────────────────────────────
    fun findOutputPinHit(cPos: Offset): Pair<MacroNode, PinId>? {
        for (node in graph.nodes.asReversed()) {
            for (pin in node.outputPins()) {
                if ((cPos - absoluteOutPin(node, pin)).getDistance() < PIN_HIT_R) return node to pin
            }
        }
        return null
    }
    fun findInputPinHit(cPos: Offset): MacroNode? =
        graph.nodes.firstOrNull { n ->
            n.hasInput() && (cPos - absoluteInPin(n)).getDistance() < PIN_HIT_R * 1.5f
        }
    fun findNodeHit(cPos: Offset): MacroNode? =
        graph.nodes.lastOrNull { n ->
            cPos.x in n.pos.x..(n.pos.x + n.blockW()) &&
            cPos.y in n.pos.y..(n.pos.y + n.blockH())
        }

    // Settings icon hit zone: top-right ~48dp area of block
    fun isSettingsIconHit(cPos: Offset, node: MacroNode): Boolean {
        if (node.type == MacroBlockType.START || node.type == MacroBlockType.END) return false
        val hitSize = 52f * density
        return cPos.x >= node.pos.x + node.blockW() - hitSize &&
               cPos.y <= node.pos.y + hitSize
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF08060F))) {

        // ── Canvas area (full screen, no top padding) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 52.dp)
                .pointerInput(graph) {
                    awaitEachGesture {
                        // 1) First touch
                        val down     = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        var lastPos  = down.position
                        var moved    = false

                        // 2) Hit-test
                        val cDown   = screenToCanvas(down.position, pan, zoom)
                        val pinHit  = findOutputPinHit(cDown)
                        val nodeHit = if (pinHit == null) findNodeHit(cDown) else null
                        when {
                            pinHit != null -> {
                                connectFromId  = pinHit.first.id
                                connectFromPin = pinHit.second
                                connectCursor  = cDown
                            }
                            nodeHit != null -> {
                                draggingId     = nodeHit.id
                                dragNodeOffset = cDown - nodeHit.pos
                            }
                            else -> {
                                // Empty space — start selection rect
                                selStart = cDown
                                selEnd   = cDown
                            }
                        }

                        // 3) Event loop
                        do {
                            val event   = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            when (pressed.size) {
                                0 -> { /* finger lifted — do-while exits */ }
                                1 -> {
                                    val ch    = pressed.first()
                                    val delta = ch.position - lastPos
                                    if (delta.getDistance() > viewConfiguration.touchSlop) moved = true
                                    if (moved) {
                                        val cPos = screenToCanvas(ch.position, pan, zoom)
                                        when {
                                            connectFromId != null -> {
                                                connectCursor  = cPos
                                                hoveredInputId = findInputPinHit(cPos)
                                                    ?.takeIf { it.id != connectFromId && it.hasInput() }?.id
                                            }
                                            draggingId != null -> {
                                                val node = graph.nodes.firstOrNull { it.id == draggingId }
                                                if (node != null) {
                                                    val newPos = snapToGrid(cPos - dragNodeOffset)
                                                    if (graph.nodes.count { it.selected } > 1 && node.selected) {
                                                        val d = newPos - node.pos
                                                        graph.moveNodes(graph.nodes.filter { it.selected }.map { it.id }.toSet(), d)
                                                    } else {
                                                        graph.moveNode(draggingId!!, newPos)
                                                    }
                                                }
                                            }
                                            selStart != null -> {
                                                // Expanding selection rectangle
                                                selEnd = cPos
                                                val s = selStart!!
                                                val e = selEnd!!
                                                val rect = Rect(
                                                    left   = minOf(s.x, e.x), top    = minOf(s.y, e.y),
                                                    right  = maxOf(s.x, e.x), bottom = maxOf(s.y, e.y)
                                                )
                                                val ids = graph.nodes.filter { n ->
                                                    n.pos.x < rect.right  && (n.pos.x + n.blockW()) > rect.left &&
                                                    n.pos.y < rect.bottom && (n.pos.y + n.blockH()) > rect.top
                                                }.map { it.id }.toSet()
                                                graph.setSelected(ids)
                                            }
                                            else -> pan += delta
                                        }
                                    }
                                    lastPos = ch.position
                                    ch.consume()
                                }
                                else -> {
                                    // Two-finger: pinch-zoom + pan
                                    connectFromId  = null
                                    draggingId     = null
                                    hoveredInputId = null
                                    moved          = true
                                    val a     = event.changes[0]
                                    val b     = event.changes[1]
                                    val prevC = (a.previousPosition + b.previousPosition) / 2f
                                    val currC = (a.position         + b.position)         / 2f
                                    val prevD = (b.previousPosition - a.previousPosition).getDistance().coerceAtLeast(1f)
                                    val currD = (b.position         - a.position        ).getDistance().coerceAtLeast(1f)
                                    val newZ  = (zoom * currD / prevD).coerceIn(0.25f, 4f)
                                    pan  = currC - (prevC - pan) * (newZ / zoom)
                                    zoom = newZ
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // 4) Gesture ended
                        val elapsed = System.currentTimeMillis() - downTime
                        if (connectFromId != null) {
                            val target = hoveredInputId
                                ?.let { id -> graph.nodes.firstOrNull { it.id == id } }
                                ?: findInputPinHit(connectCursor)?.takeIf { it.id != connectFromId && it.hasInput() }
                                ?: if (moved) findNodeHit(connectCursor)?.takeIf { it.id != connectFromId && it.hasInput() } else null
                            if (target != null) graph.tryConnect(connectFromId!!, connectFromPin, target.id)
                            connectFromId  = null
                            hoveredInputId = null
                        } else if (selStart != null) {
                            // Finished selection rect drag
                            if (!moved) {
                                // Tap on empty space → deselect all
                                graph.setSelected(emptySet())
                            }
                            selStart = null
                            selEnd   = null
                        } else if (!moved) {
                            val cPos   = screenToCanvas(lastPos, pan, zoom)
                            val tapped = findNodeHit(cPos)
                            when {
                                tapped != null && elapsed >= LONG_PRESS_MS        -> onNodeLongPress(tapped)
                                tapped != null && isSettingsIconHit(cPos, tapped) -> onNodeTap(tapped)
                                tapped != null                                     -> graph.setSelected(setOf(tapped.id))
                                else                                               -> graph.setSelected(emptySet())
                            }
                        }
                        draggingId = null
                    }
                }
        ) {
            // Draw grid + edges on Canvas layer
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Grid dots
                val gridColor = Color(0xFF2E2845)
                val startX = ((-pan.x / zoom) / GRID_STEP).toInt() - 1
                val startY = ((-pan.y / zoom) / GRID_STEP).toInt() - 1
                val endX   = startX + (size.width  / (GRID_STEP * zoom)).toInt() + 2
                val endY   = startY + (size.height / (GRID_STEP * zoom)).toInt() + 2
                for (gx in startX..endX) for (gy in startY..endY) {
                    val sx = gx * GRID_STEP * zoom + pan.x
                    val sy = gy * GRID_STEP * zoom + pan.y
                    drawCircle(gridColor, radius = 2.5f, center = Offset(sx, sy))
                }

                // Edges
                for (edge in graph.edges) {
                    val fromNode = graph.nodes.firstOrNull { it.id == edge.fromId } ?: continue
                    val toNode   = graph.nodes.firstOrNull { it.id == edge.toId }   ?: continue

                    val fromScreen = canvasToScreen(absoluteOutPin(fromNode, edge.fromPin), pan, zoom)
                    val toScreen   = canvasToScreen(absoluteInPin(toNode), pan, zoom)

                    val edgeColor = when (edge.fromPin) {
                        PinId.YES -> Color(0xFF5BFF9A)
                        PinId.NO  -> Color(0xFFFF7B9D)
                        PinId.OUT -> Color(0xFF9B6DFF)
                    }
                    drawEdge(fromScreen, toScreen, edgeColor, strokeW = 2.5f * zoom.coerceIn(0.5f, 2f))
                }

                // Live wire while connecting
                val cid = connectFromId
                if (cid != null) {
                    val fromNode = graph.nodes.firstOrNull { it.id == cid }
                    if (fromNode != null) {
                        val wireColor = if (hoveredInputId != null) Color.White
                                        else Color.White.copy(alpha = 0.45f)
                        drawEdge(
                            canvasToScreen(absoluteOutPin(fromNode, connectFromPin), pan, zoom),
                            canvasToScreen(connectCursor, pan, zoom),
                            wireColor, 2.2f
                        )
                    }
                }

                // Selection rect
                val ss = selStart; val se = selEnd
                if (ss != null && se != null) {
                    val rL = canvasToScreen(Offset(minOf(ss.x, se.x), minOf(ss.y, se.y)), pan, zoom)
                    val rR = canvasToScreen(Offset(maxOf(ss.x, se.x), maxOf(ss.y, se.y)), pan, zoom)
                    drawRect(
                        color   = Color(0xFF9B6DFF).copy(alpha = 0.18f),
                        topLeft = rL,
                        size    = androidx.compose.ui.geometry.Size(rR.x - rL.x, rR.y - rL.y)
                    )
                    drawRect(
                        color   = Color(0xFF9B6DFF).copy(alpha = 0.75f),
                        topLeft = rL,
                        size    = androidx.compose.ui.geometry.Size(rR.x - rL.x, rR.y - rL.y),
                        style   = Stroke(width = 2f)
                    )
                }
            }

            // Block nodes — size uses density so canvas-px = block physical px
            for (node in graph.nodes) {
                val sp = canvasToScreen(node.pos, pan, zoom)
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = sp.x
                            translationY = sp.y
                            scaleX       = zoom
                            scaleY       = zoom
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        }
                        .size((node.blockW() / density).dp, (node.blockH() / density).dp)
                        .zIndex(if (node.selected || node.id == draggingId) 5f else 1f)
                ) {
                    BlockView(
                        node           = node,
                        highlightInput = node.id == hoveredInputId,
                        density        = density
                    )
                }
            }
        }

        // ── Multiselect / wire overlay (floating, top of canvas) ──────────
        val selCount    = graph.nodes.count { it.selected }
        val wireHint    = connectFromId != null
        if (selCount > 0 || wireHint) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .zIndex(20f),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selCount > 0) {
                    ToolbarBtn(icon = Icons.Filled.Delete, tint = Color(0xFFFF7B9D), label = "Delete ($selCount)") {
                        onDeleteSelected()
                    }
                    ToolbarBtn(icon = Icons.Filled.Close, tint = Color(0xFF8A8899), label = "Deselect") {
                        graph.setSelected(emptySet())
                    }
                }
                if (wireHint) {
                    Text(
                        "Drag to input pin  •  lift to cancel",
                        color    = Color(0xFF9B6DFF),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF0E0B1A).copy(alpha = 0.85f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // ── Bottom nav bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .background(Color(0xFF0E0B1A))
                .padding(horizontal = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Add button — first position
            Box {
                NavBtn(Icons.Filled.Add) { showAddMenu = true }
                AddBlockMenu(
                    expanded  = showAddMenu,
                    onDismiss = { showAddMenu = false },
                    onPick    = { type ->
                        showAddMenu = false
                        val center = snapToGrid(screenToCanvas(Offset(400f, 300f), pan, zoom))
                        onAddBlock(type, center)
                    }
                )
            }
            NavBtn(Icons.Filled.ZoomOut)            { zoom = (zoom / 1.3f).coerceAtLeast(0.25f) }
            NavBtn(Icons.Filled.ZoomIn)             { zoom = (zoom * 1.3f).coerceAtMost(4f) }
            NavBtn(Icons.Filled.KeyboardArrowLeft)  { pan += Offset( 80f, 0f) }
            NavBtn(Icons.Filled.KeyboardArrowRight) { pan -= Offset( 80f, 0f) }
            NavBtn(Icons.Filled.KeyboardArrowUp)    { pan += Offset(0f,  80f) }
            NavBtn(Icons.Filled.KeyboardArrowDown)  { pan -= Offset(0f,  80f) }
            NavBtn(Icons.Filled.CenterFocusStrong)  { pan = Offset(40f, 60f); zoom = 1f }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// BlockView  (pure rendering, no gesture handlers)
//
// Pin circle position math — ensures visual center matches canvas pin coords:
//   Block physical width = BLOCK_W px  (layout dp × density = BLOCK_W/density × density = BLOCK_W)
//   Input  pin: align(TopCenter).offset(y=-(PIN_R/density).dp)  → center at y=0 px  ✓
//   Output pin: align(BottomCenter).offset(y=+(PIN_R/density).dp) → center at y=BLOCK_H px ✓
//   YES pad start = (BLOCK_W·0.28−PIN_R)/density dp → center at x=BLOCK_W·0.28 px ✓
//   NO  pad end   = (BLOCK_W·0.28−PIN_R)/density dp → center at x=BLOCK_W·0.72 px ✓
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BlockView(
    node:           MacroNode,
    highlightInput: Boolean = false,
    density:        Float   = 1f
) {
    val color    = blockColor(node.type)
    val label    = blockLabel(node.type)
    val selected = node.selected
    val pinDp    = (PIN_R * 2f / density).dp    // pin circle diameter
    val pinOutDp = (PIN_R        / density).dp   // how far pin sticks out from edge

    // Outer Box has no clip — pins overflow the block rect visually
    Box(modifier = Modifier.fillMaxSize()) {

        // Block body
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(if (selected) color.copy(alpha = 0.28f) else Color(0xFF100D1C))
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) color else color.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(10.dp)
                )
        ) {
            Column(
                modifier            = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(label, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    lineHeight = 15.sp)
                val summary = blockSummary(node)
                if (summary.isNotEmpty()) {
                    Text(
                        text     = summary,
                        color    = Color.White.copy(alpha = 0.88f),
                        fontSize = 13.sp,
                        lineHeight = 16.sp,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            // Settings gear — top-right, only for configurable blocks
            val hasSettings = node.type !in listOf(MacroBlockType.START, MacroBlockType.END)
            if (hasSettings) {
                Icon(
                    imageVector        = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint               = color.copy(alpha = 0.65f),
                    modifier           = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                )
            }
        }

        // Input pin (top-center, sticks above block)
        if (node.hasInput()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -pinOutDp)
                    .size(pinDp)
                    .clip(CircleShape)
                    .background(Color(0xFF0E0B1A))
                    .border(
                        width = if (highlightInput) 3.dp else 2.dp,
                        color = if (highlightInput) Color.White else Color(0xFF9B6DFF),
                        shape = CircleShape
                    )
            )
        }

        // Output pin(s), sticking below block
        when (node.type) {
            MacroBlockType.IF_ELSE -> {
                val bw     = node.blockW()
                val yesPad = ((bw * 0.28f - PIN_R) / density).dp
                val noPad  = ((bw * 0.28f - PIN_R) / density).dp
                // YES
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = yesPad)
                    .offset(y = pinOutDp).size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A)).border(2.dp, Color(0xFF5BFF9A), CircleShape))
                Text("YES", color = Color(0xFF5BFF9A), fontSize = 7.sp,
                    modifier = Modifier.align(Alignment.BottomStart).padding(start = yesPad)
                        .offset(y = -(pinDp + 1.dp)))
                // NO
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = noPad)
                    .offset(y = pinOutDp).size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A)).border(2.dp, Color(0xFFFF7B9D), CircleShape))
                Text("NO", color = Color(0xFFFF7B9D), fontSize = 7.sp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(end = noPad)
                        .offset(y = -(pinDp + 1.dp)))
            }
            MacroBlockType.END -> { /* no output pin */ }
            else -> {
                Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = pinOutDp)
                    .size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A)).border(2.dp, Color(0xFF9B6DFF), CircleShape))
            }
        }
    }
}

private fun blockSummary(node: MacroNode): String = when (val p = node.params) {
    is BlockParams.IrSend      -> p.displayLabel.ifEmpty { "Tap ⚙ to pick IR button" }
    is BlockParams.Delay       -> "${p.ms} ms"
    is BlockParams.ShowText    -> {
        val dur = "${p.durationMs / 1000}s"
        if (p.text.isEmpty()) "duration: $dur" else "\"${p.text.take(28)}\"\n$dur"
    }
    is BlockParams.WaitConfirm -> p.message.ifEmpty { "Press OK to continue" }
    is BlockParams.IfElse      -> p.message.ifEmpty { "Continue?" }
    else                       -> ""
}

// ─────────────────────────────────────────────────────────────────────────────
// Toolbar button
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ToolbarBtn(
    icon:    androidx.compose.ui.graphics.vector.ImageVector,
    tint:    Color,
    label:   String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bottom nav button (zoom / pan)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NavBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF1A1730))
            .border(1.dp, Color(0xFF3A3460), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF9B6DFF), modifier = Modifier.size(22.dp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Add-block dropdown menu
// ─────────────────────────────────────────────────────────────────────────────

private val addableBlockTypes = listOf(
    MacroBlockType.IR_SEND,
    MacroBlockType.DELAY,
    MacroBlockType.SHOW_TEXT,
    MacroBlockType.WAIT_CONFIRM,
    MacroBlockType.IF_ELSE,
    MacroBlockType.END
)

@Composable
private fun AddBlockMenu(
    expanded:  Boolean,
    onDismiss: () -> Unit,
    onPick:    (MacroBlockType) -> Unit
) {
    DropdownMenu(
        expanded         = expanded,
        onDismissRequest = onDismiss,
        modifier         = Modifier.background(Color(0xFF121024))
    ) {
        addableBlockTypes.forEach { type ->
            val color = blockColor(type)
            DropdownMenuItem(
                text = {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text(blockLabel(type), color = Color.White, fontSize = 13.sp)
                    }
                },
                onClick = { onPick(type) }
            )
        }
    }
}
