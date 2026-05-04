package com.m4r71n.irshark.ui.macro

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
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
    MacroBlockType.START, MacroBlockType.END -> 300f
    MacroBlockType.IR_SEND -> {
        val len = (params as? BlockParams.IrSend)?.displayLabel?.length ?: 0
        (360f + len * 8f).coerceIn(360f, 720f)
    }
    MacroBlockType.DELAY   -> 360f
    MacroBlockType.VIBRATE -> 360f
    MacroBlockType.REPEAT  -> 450f
    MacroBlockType.RETRY   -> 450f
    MacroBlockType.JOIN    -> 300f
    MacroBlockType.SWITCH  -> {
        val p = params as? BlockParams.Switch ?: BlockParams.Switch()
        ((p.options.size + 1) * 72f + 60f).coerceIn(300f, 700f)
    }
    else                   -> 450f   // SHOW_TEXT, WAIT_CONFIRM, IF_ELSE
}

fun MacroNode.blockH(): Float = when (type) {
    MacroBlockType.START, MacroBlockType.END -> 120f
    MacroBlockType.DELAY                     -> 200f
    MacroBlockType.SHOW_TEXT                 -> 220f
    MacroBlockType.WAIT_CONFIRM              -> 200f
    MacroBlockType.IR_SEND                   -> {
        val textLen = (params as? BlockParams.IrSend)?.displayLabel?.length ?: 0
        val extraLines = (textLen / 18).coerceIn(0, 8)
        (280f + extraLines * 24f)
    }
    MacroBlockType.IF_ELSE                   -> 260f
    MacroBlockType.VIBRATE                   -> 200f
    MacroBlockType.REPEAT                    -> 320f
    MacroBlockType.RETRY                     -> 340f
    MacroBlockType.JOIN                      -> 200f
    MacroBlockType.SWITCH                    -> {
        val msgLen = (params as? BlockParams.Switch)?.message?.length ?: 0
        val extraLines = (msgLen / 24).coerceIn(0, 4)
        (260f + extraLines * 20f)
    }
    else                                     -> 200f
}

// ─────────────────────────────────────────────────────────────────────────────
// Pin position helpers (canvas-local, relative to node.pos = top-left)
// ─────────────────────────────────────────────────────────────────────────────

fun inputPinOffset(blockW: Float = BLOCK_W): Offset = Offset(blockW / 2f, 0f)

fun outputPinOffset(pin: PinId, blockW: Float = BLOCK_W, blockH: Float = BLOCK_H): Offset =
    when (pin) {
        PinId.OUT  -> Offset(blockW / 2f,    blockH)
        PinId.YES  -> Offset(blockW * 0.28f, blockH)
        PinId.NO   -> Offset(blockW * 0.72f, blockH)
        PinId.BODY -> Offset(blockW * 0.28f, blockH)   // REPEAT body (left)
        PinId.CONT -> Offset(blockW * 0.72f, blockH)   // REPEAT continue (right)
        else       -> Offset(blockW / 2f,    blockH)   // OPT0-9, DEFAULT handled via absoluteOutPin
    }

fun absoluteOutPin(node: MacroNode, pin: PinId): Offset {
    // SWITCH pins: evenly spaced along the bottom
    if (node.type == MacroBlockType.SWITCH) {
        val allPins = node.outputPins()
        val idx     = allPins.indexOf(pin)
        if (idx >= 0) {
            val x = node.blockW() / (allPins.size + 1).toFloat() * (idx + 1)
            return node.pos + Offset(x, node.blockH())
        }
    }
    return node.pos + outputPinOffset(pin, node.blockW(), node.blockH())
}

/** Canvas-absolute input pin position.
 *  For JOIN blocks [slot] selects which of the evenly-spaced input pins (0-based). */
fun absoluteInPin(node: MacroNode, slot: Int = 0): Offset {
    if (node.type == MacroBlockType.JOIN) {
        val count = (node.params as? BlockParams.Join)?.inputCount?.coerceIn(1, 8) ?: 2
        val pinX  = node.blockW() / (count + 1).toFloat() * (slot + 1)
        return node.pos + Offset(pinX, 0f)
    }
    return node.pos + inputPinOffset(node.blockW())
}

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
    MacroBlockType.VIBRATE      -> Color(0xFF9B3DC2)
    MacroBlockType.REPEAT       -> Color(0xFFD47B1A)
    MacroBlockType.RETRY        -> Color(0xFFFF914D)
    MacroBlockType.SWITCH       -> Color(0xFF3D8ADF)
    MacroBlockType.JOIN         -> Color(0xFF3DA87A)
}

fun blockLabel(type: MacroBlockType): String = when (type) {
    MacroBlockType.START        -> "START"
    MacroBlockType.END          -> "STOP"
    MacroBlockType.IR_SEND      -> "IR Send"
    MacroBlockType.DELAY        -> "Delay"
    MacroBlockType.SHOW_TEXT    -> "Show Text"
    MacroBlockType.WAIT_CONFIRM -> "Wait for OK"
    MacroBlockType.IF_ELSE      -> "If / Else"
    MacroBlockType.VIBRATE      -> "Vibrate"
    MacroBlockType.REPEAT       -> "Repeat"
    MacroBlockType.RETRY        -> "Retry"
    MacroBlockType.SWITCH       -> "Switch"
    MacroBlockType.JOIN         -> "Join"
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
    onSendIrPreview:  (BlockParams.IrSend) -> Unit = {},
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
    var connectFromId      by remember { mutableStateOf<String?>(null) }
    var connectFromPin     by remember { mutableStateOf(PinId.OUT) }
    var connectFromIsInput by remember { mutableStateOf(false) }   // true = drag started from input pin
    var connectFromSlot    by remember { mutableStateOf(0) }       // slot of JOIN input pin dragged from
    var connectToSlot      by remember { mutableStateOf(0) }       // slot of JOIN input pin being targeted
    var connectCursor      by remember { mutableStateOf(Offset.Zero) }
    var hoveredInputId     by remember { mutableStateOf<String?>(null) }
    var hoveredOutputId    by remember { mutableStateOf<String?>(null) }

    // ── Selection rect (canvas-px)
    var selStart by remember { mutableStateOf<Offset?>(null) }
    var selEnd   by remember { mutableStateOf<Offset?>(null) }

    // ── Canvas interaction mode
    var isSelectMode by remember { mutableStateOf(false) }

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
    /** Returns (node, slotIndex) for the input pin closest to [cPos], or null if none hit. */
    fun findInputPinHitWithSlot(cPos: Offset): Pair<MacroNode, Int>? {
        for (n in graph.nodes.asReversed()) {
            if (!n.hasInput()) continue
            if (n.type == MacroBlockType.JOIN) {
                val count = (n.params as? BlockParams.Join)?.inputCount?.coerceIn(1, 8) ?: 2
                for (i in 0 until count) {
                    if ((cPos - absoluteInPin(n, i)).getDistance() < PIN_HIT_R * 1.5f) return n to i
                }
            } else {
                if ((cPos - absoluteInPin(n)).getDistance() < PIN_HIT_R * 1.5f) return n to 0
            }
        }
        return null
    }
    fun findInputPinHit(cPos: Offset): MacroNode? = findInputPinHitWithSlot(cPos)?.first
    fun findNodeHit(cPos: Offset): MacroNode? =
        graph.nodes.lastOrNull { n ->
            cPos.x in n.pos.x..(n.pos.x + n.blockW()) &&
            cPos.y in n.pos.y..(n.pos.y + n.blockH())
        }

    // Settings icon hit zone: top-right corner, sized to the actual icon area (~28dp)
    fun isSettingsIconHit(cPos: Offset, node: MacroNode): Boolean {
        if (node.type == MacroBlockType.START || node.type == MacroBlockType.END) return false
        val hitSize = 38f * density
        return cPos.x >= node.pos.x + node.blockW() - hitSize &&
               cPos.y <= node.pos.y + hitSize
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds().background(Color(0xFF08060F))) {

        // ── Canvas area (full screen, no top padding) ─────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 52.dp)
                .clipToBounds()
                .pointerInput(graph) {
                    awaitEachGesture {
                        // 1) First touch
                        val down     = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        var lastPos  = down.position
                        var moved    = false

                        // 2) Hit-test
                        val cDown    = screenToCanvas(down.position, pan, zoom)
                        val pinHit   = findOutputPinHit(cDown)
                        val inPinHit = if (pinHit == null) findInputPinHit(cDown) else null
                        val nodeHit  = if (pinHit == null && inPinHit == null) findNodeHit(cDown) else null
                        when {
                            pinHit != null -> {
                                connectFromId      = pinHit.first.id
                                connectFromPin     = pinHit.second
                                connectFromIsInput = false
                                connectCursor      = cDown
                            }
                            inPinHit != null -> {
                                // Dragging FROM an input pin → reverse-direction wire
                                val slotHit = findInputPinHitWithSlot(cDown)
                                connectFromId      = inPinHit.id
                                connectFromPin     = PinId.OUT   // placeholder; direction resolved at drop
                                connectFromIsInput = true
                                connectFromSlot    = slotHit?.second ?: 0
                                connectCursor      = cDown
                            }
                            nodeHit != null -> {
                                draggingId     = nodeHit.id
                                dragNodeOffset = cDown - nodeHit.pos  // raw canvas offset, no snap
                                // Auto-select the node being dragged
                                if (!nodeHit.selected) {
                                    graph.setSelected(setOf(nodeHit.id))
                                }
                            }
                            else -> {
                                // Empty space — start selection rect only in SELECT mode
                                if (isSelectMode) {
                                    selStart = cDown
                                    selEnd   = cDown
                                }
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
                                    val cPos = screenToCanvas(ch.position, pan, zoom)
                                    if (connectFromId != null) {
                                        // Wire mode: always update cursor without waiting for touchSlop
                                        connectCursor  = cPos
                                        if (connectFromIsInput) {
                                            // Dragging from input: highlight nearby output pins
                                            hoveredOutputId = findOutputPinHit(cPos)
                                                ?.takeIf { it.first.id != connectFromId }?.first?.id
                                        } else {
                                            val hit = findInputPinHitWithSlot(cPos)
                                                ?.takeIf { it.first.id != connectFromId && it.first.hasInput() }
                                            hoveredInputId = hit?.first?.id
                                            if (hit != null) connectToSlot = hit.second
                                        }
                                    } else if (draggingId != null) {
                                        // Node drag: immediate, no touchSlop required
                                        val node = graph.nodes.firstOrNull { it.id == draggingId }
                                        if (node != null) {
                                            val rawPos = cPos - dragNodeOffset
                                            val newPos = snapToGrid(rawPos)
                                            if (graph.nodes.count { it.selected } > 1 && node.selected) {
                                                val d = newPos - node.pos
                                                graph.moveNodes(graph.nodes.filter { it.selected }.map { it.id }.toSet(), d)
                                            } else {
                                                graph.moveNode(draggingId!!, newPos)
                                            }
                                        }
                                    } else {
                                        // Pan or selection rect: pan immediately on any movement
                                        if (delta.getDistance() > viewConfiguration.touchSlop) moved = true
                                        when {
                                            selStart != null && moved -> {
                                                // Expanding selection rectangle (only after touchSlop exceeded)
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
                                            selStart == null -> {
                                                // No selection active: pan workspace immediately
                                                pan += delta
                                            }
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
                            if (connectFromIsInput) {
                                // Reverse drag: from input pin → find output pin at cursor
                                val outHit = hoveredOutputId
                                    ?.let { id -> graph.nodes.firstOrNull { it.id == id }
                                        ?.let { n -> findOutputPinHit(connectCursor)
                                            ?.takeIf { it.first.id == id } } }
                                    ?: findOutputPinHit(connectCursor)?.takeIf { it.first.id != connectFromId }
                                if (outHit != null) {
                                    graph.tryConnect(outHit.first.id, outHit.second, connectFromId!!, connectFromSlot)
                                }
                                hoveredOutputId = null
                            } else {
                                // Forward drag: find the target node + slot
                                val pinHitWithSlot = hoveredInputId
                                    ?.let { id -> graph.nodes.firstOrNull { it.id == id }?.let { n -> n to connectToSlot } }
                                    ?: findInputPinHitWithSlot(connectCursor)?.takeIf { it.first.id != connectFromId && it.first.hasInput() }
                                    ?: if (moved) findNodeHit(connectCursor)?.takeIf { it.id != connectFromId && it.hasInput() }?.let { n ->
                                        // Dropped anywhere on block: assign first free slot for JOIN, else slot 0
                                        val slot = if (n.type == MacroBlockType.JOIN) {
                                            val usedSlots = graph.edges.filter { e -> e.toId == n.id }.map { e -> e.toSlot }.toSet()
                                            val count = (n.params as? BlockParams.Join)?.inputCount?.coerceIn(1, 8) ?: 2
                                            (0 until count).firstOrNull { s -> s !in usedSlots } ?: 0
                                        } else 0
                                        n to slot
                                    } else null
                                if (pinHitWithSlot != null) graph.tryConnect(connectFromId!!, connectFromPin, pinHitWithSlot.first.id, pinHitWithSlot.second)
                                hoveredInputId = null
                            }
                            connectFromId      = null
                            connectFromIsInput = false
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
                    val toScreen   = canvasToScreen(absoluteInPin(toNode, edge.toSlot), pan, zoom)

                    val edgeColor = when (edge.fromPin) {
                        PinId.YES     -> Color(0xFF5BFF9A)
                        PinId.NO      -> Color(0xFFFF7B9D)
                        PinId.BODY    -> Color(0xFFFFAA3D)
                        PinId.CONT    -> Color(0xFF6DB4FF)
                        PinId.DEFAULT -> Color(0xFFFFBB6D)
                        else          -> Color(0xFF9B6DFF)   // OUT, OPT0-OPT9
                    }
                    drawEdge(fromScreen, toScreen, edgeColor, strokeW = 2.5f * zoom.coerceIn(0.5f, 2f))
                }

                // Live wire while connecting
                val cid = connectFromId
                if (cid != null) {
                    val anchorNode = graph.nodes.firstOrNull { it.id == cid }
                    if (anchorNode != null) {
                        if (connectFromIsInput) {
                            // Dragging from input pin: draw wire from cursor to the specific input slot
                            val wireColor = if (hoveredOutputId != null) Color.White
                                            else Color.White.copy(alpha = 0.45f)
                            drawEdge(
                                canvasToScreen(connectCursor, pan, zoom),
                                canvasToScreen(absoluteInPin(anchorNode, connectFromSlot), pan, zoom),
                                wireColor, 2.2f
                            )
                        } else {
                            val wireColor = if (hoveredInputId != null) Color.White
                                            else Color.White.copy(alpha = 0.45f)
                            drawEdge(
                                canvasToScreen(absoluteOutPin(anchorNode, connectFromPin), pan, zoom),
                                canvasToScreen(connectCursor, pan, zoom),
                                wireColor, 2.2f
                            )
                        }
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
                    BlockViewHost(
                        node            = node,
                        highlightInput  = node.id == hoveredInputId,
                        highlightOutput = node.id == hoveredOutputId,
                        density         = density
                    )
                }
            }
        }

        // ── IR preview play buttons (rendered outside clipToBounds inner box) ─
        for (node in graph.nodes) {
            if (node.type != MacroBlockType.IR_SEND) continue
            val irParams = node.params as? BlockParams.IrSend ?: continue
            if (irParams.irCode.isBlank()) continue
            val sp = canvasToScreen(node.pos, pan, zoom)
            val btnPx = 132f
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = sp.x + node.blockW() * zoom + 8f
                        translationY = sp.y + zoom * (node.blockH() - btnPx) / 2f
                        scaleX = zoom
                        scaleY = zoom
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .size((btnPx / density).dp)
                    .clip(CircleShape)
                    .background(Color(0xFF7B4DDF).copy(alpha = 0.90f))
                    .border(1.5.dp, Color(0xFF9B6DFF), CircleShape)
                    .clickable { onSendIrPreview(irParams) }
                    .zIndex(30f),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Send IR preview",
                    tint = Color.White,
                    modifier = Modifier.size(((btnPx * 0.58f) / density).dp)
                )
            }
        }

        // ── Overlay row (always visible) ──────────────────────────────────
        val selCount = graph.nodes.count { it.selected }
        val wireHint = connectFromId != null
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .zIndex(20f),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Select / Pan mode toggle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isSelectMode) Color(0xFF9B6DFF).copy(alpha = 0.18f)
                        else Color(0xFF1A1730)
                    )
                    .border(
                        1.dp,
                        if (isSelectMode) Color(0xFF9B6DFF).copy(alpha = 0.70f) else Color(0xFF3A3460),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable { isSelectMode = !isSelectMode }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (isSelectMode) "SELECT" else "MOVE",
                    color      = if (isSelectMode) Color(0xFF9B6DFF) else Color(0xFF8A8899),
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
                    "Drag to input pin  \u2022  lift to cancel",
                    color    = Color(0xFF9B6DFF),
                    fontSize = 10.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0E0B1A).copy(alpha = 0.85f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // ── Bottom nav bar ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .align(Alignment.BottomCenter)
                .zIndex(40f)
                .background(Color(0xFF0E0B1A))
                .padding(horizontal = 6.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Add button — first position
            NavBtn(Icons.Filled.Add) { showAddMenu = true }
            NavBtn(Icons.Filled.ZoomOut)            { zoom = (zoom / 1.3f).coerceAtLeast(0.25f) }
            NavBtn(Icons.Filled.ZoomIn)             { zoom = (zoom * 1.3f).coerceAtMost(4f) }
            NavBtn(Icons.Filled.KeyboardArrowLeft)  { pan += Offset( 80f, 0f) }
            NavBtn(Icons.Filled.KeyboardArrowRight) { pan -= Offset( 80f, 0f) }
            NavBtn(Icons.Filled.KeyboardArrowUp)    { pan += Offset(0f,  80f) }
            NavBtn(Icons.Filled.KeyboardArrowDown)  { pan -= Offset(0f,  80f) }
            NavBtn(Icons.Filled.CenterFocusStrong)  { pan = Offset(40f, 60f); zoom = 1f }
        }

        if (showAddMenu) {
            AddBlockDialog(
                onDismiss = { showAddMenu = false },
                onPick = { type ->
                    showAddMenu = false
                    val center = snapToGrid(screenToCanvas(Offset(400f, 300f), pan, zoom))
                    onAddBlock(type, center)
                }
            )
        }
    }
}

@Composable
private fun BlockViewHost(
    node:            MacroNode,
    highlightInput:  Boolean,
    highlightOutput: Boolean,
    density:         Float
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            }
        },
        update = { composeView ->
            composeView.setContent {
                BlockViewContent(
                    node = node,
                    highlightInput = highlightInput,
                    highlightOutput = highlightOutput,
                    density = density
                )
            }
        }
    )
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
private fun BlockViewContent(
    node:            MacroNode,
    highlightInput:  Boolean = false,
    highlightOutput: Boolean = false,
    density:         Float   = 1f
) {
    val color    = blockColor(node.type)
    val label    = blockLabel(node.type)
    val selected = node.selected
    val isTerminal = node.type == MacroBlockType.START || node.type == MacroBlockType.END
    val pinDp    = (PIN_R * 2f / density).dp
    val pinOutDp = (PIN_R        / density).dp

    // Outer Box has no clip — pins overflow the block rect visually
    Box(modifier = Modifier.fillMaxSize()) {

        // Block body: opaque panel; START/END use simpler solid style
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .graphicsLayer {
                    shadowElevation = 10f
                    shape = RoundedCornerShape(12.dp)
                }
                .background(if (isTerminal) color.copy(alpha = 0.20f) else Color(0xFF14171D))
                .border(
                    width = if (selected) 2.5.dp else 1.5.dp,
                    color = if (selected) color.copy(alpha = 0.95f) else color.copy(alpha = 0.78f),
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            if (isTerminal) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        color = color,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── TOP SECTION: Label + Settings icon (fixed height)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp)
                            .background(color.copy(alpha = if (selected) 0.28f else 0.20f))
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            label,
                            color = color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = color.copy(alpha = if (selected) 0.85f else 0.70f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    // ── MIDDLE SECTION: Summary content (flexible, grows)
                    val irSource = (node.params as? BlockParams.IrSend)?.irSource?.takeIf { it.isNotEmpty() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(Color(0xFF14171D))
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 8.dp)
                    ) {
                        val summary = blockSummary(node)
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (summary.isNotEmpty()) {
                                Text(
                                    text = summary,
                                    color = Color.White.copy(alpha = 0.92f),
                                    fontSize = 12.sp,
                                    lineHeight = 15.sp,
                                    maxLines = if (irSource != null) 8 else 10,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))

                            // Source badge for IR Send blocks (always below content, no overlap)
                            if (irSource != null) {
                                val badgeColor = if (irSource == "DB") Color(0xFF2E7ADB) else Color(0xFF1E8A5E)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(badgeColor.copy(alpha = 0.12f))
                                        .border(1.dp, badgeColor.copy(alpha = 0.50f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = irSource,
                                        color = badgeColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }

                    // ── BOTTOM SECTION: Pin labels (fixed height, only for nodes with multiple pins)
                    when (node.type) {
                        MacroBlockType.IF_ELSE -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(Color(0xFF11141A))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("YES", color = Color(0xFF5BFF9A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("NO", color = Color(0xFFFF7B9D), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        MacroBlockType.REPEAT, MacroBlockType.RETRY -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(Color(0xFF11141A))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val bodyColor = if (node.type == MacroBlockType.RETRY) Color(0xFFFFB86B) else Color(0xFFFFAA3D)
                                val outColor = if (node.type == MacroBlockType.RETRY) Color(0xFFFFCFA8) else Color(0xFF6DB4FF)
                                Text("BODY", color = bodyColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text("OUT", color = outColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        MacroBlockType.SWITCH -> {
                            val allPins = node.outputPins()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(Color(0xFF11141A))
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                allPins.forEachIndexed { i, pin ->
                                    val isDefault  = (pin == PinId.DEFAULT)
                                    val pinColor   = if (isDefault) Color(0xFFFFBB6D) else Color(0xFF9B6DFF)
                                    val pinLabel   = if (isDefault) "D" else "${i + 1}"
                                    Text(pinLabel, color = pinColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        // Input pin (top-center, sticks above block)
        if (node.type == MacroBlockType.JOIN) {
            // Multiple input pins evenly spaced at the top
            val joinParams = node.params as? BlockParams.Join ?: BlockParams.Join()
            val bw = node.blockW()
            val count = joinParams.inputCount.coerceIn(1, 8)
            for (i in 0 until count) {
                val pinX = bw / (count + 1).toFloat() * (i + 1)
                val pinPadDp = ((pinX - PIN_R) / density).dp
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = pinPadDp)
                        .offset(y = -pinOutDp)
                        .size(pinDp)
                        .clip(CircleShape)
                        .background(Color(0xFF0E0B1A))
                        .border(
                            width = if (highlightInput) 3.dp else 2.5.dp,
                            color = if (highlightInput) Color.White else Color(0xFF9B6DFF).copy(alpha = 0.95f),
                            shape = CircleShape
                        )
                        .graphicsLayer { shadowElevation = 4f; shape = CircleShape }
                )
            }
        } else if (node.hasInput()) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = -pinOutDp)
                    .size(pinDp)
                    .clip(CircleShape)
                    .background(Color(0xFF0E0B1A))
                    .border(
                        width = if (highlightInput) 3.dp else 2.5.dp,
                        color = if (highlightInput) Color.White else Color(0xFF9B6DFF).copy(alpha = 0.95f),
                        shape = CircleShape
                    )
                    .graphicsLayer { shadowElevation = 4f; shape = CircleShape }
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
                    .background(Color(0xFF0E0B1A))
                    .border(2.5.dp, Color(0xFF5BFF9A).copy(alpha = 0.98f), CircleShape)
                    .graphicsLayer { shadowElevation = 4f; shape = CircleShape })
                // NO
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = noPad)
                    .offset(y = pinOutDp).size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A))
                    .border(2.5.dp, Color(0xFFFF7B9D).copy(alpha = 0.98f), CircleShape)
                    .graphicsLayer { shadowElevation = 4f; shape = CircleShape })
            }
            MacroBlockType.REPEAT, MacroBlockType.RETRY -> {
                val bw       = node.blockW()
                val bodyPad  = ((bw * 0.28f - PIN_R) / density).dp
                val contPad  = ((bw * 0.28f - PIN_R) / density).dp
                val bodyColor = if (node.type == MacroBlockType.RETRY) Color(0xFFFFB86B) else Color(0xFFFFAA3D)
                val contColor = if (node.type == MacroBlockType.RETRY) Color(0xFFFFCFA8) else Color(0xFF6DB4FF)
                // BODY pin (left)
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = bodyPad)
                    .offset(y = pinOutDp).size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A))
                    .border(if (highlightOutput) 3.dp else 2.5.dp, if (highlightOutput) Color.White else bodyColor.copy(alpha = 0.98f), CircleShape)
                    .graphicsLayer { shadowElevation = 4f; shape = CircleShape })
                // CONT pin (right)
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = contPad)
                    .offset(y = pinOutDp).size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A))
                    .border(if (highlightOutput) 3.dp else 2.5.dp, if (highlightOutput) Color.White else contColor.copy(alpha = 0.98f), CircleShape)
                    .graphicsLayer { shadowElevation = 4f; shape = CircleShape })
            }
            MacroBlockType.SWITCH -> {
                val p       = node.params as? BlockParams.Switch ?: BlockParams.Switch()
                val allPins = node.outputPins()
                val bw      = node.blockW()
                allPins.forEachIndexed { i, pin ->
                    val isDefault  = (pin == PinId.DEFAULT)
                    val pinColor   = if (isDefault) Color(0xFFFFBB6D) else Color(0xFF9B6DFF)
                    val pinX       = bw / (allPins.size + 1).toFloat() * (i + 1)
                    val pinPadDp   = ((pinX - PIN_R) / density).dp
                    Box(modifier = Modifier.align(Alignment.BottomStart).padding(start = pinPadDp)
                        .offset(y = pinOutDp).size(pinDp).clip(CircleShape)
                        .background(Color(0xFF0E0B1A))
                        .border(if (highlightOutput) 3.dp else 2.5.dp, if (highlightOutput) Color.White else pinColor.copy(alpha = 0.98f), CircleShape)
                        .graphicsLayer { shadowElevation = 4f; shape = CircleShape })
                }
            }
            MacroBlockType.END -> { /* no output pin */ }
            else -> {
                Box(modifier = Modifier.align(Alignment.BottomCenter).offset(y = pinOutDp)
                    .size(pinDp).clip(CircleShape)
                    .background(Color(0xFF0E0B1A))
                    .border(
                        width = if (highlightOutput) 3.dp else 2.5.dp,
                        color = if (highlightOutput) Color.White else Color(0xFF9B6DFF).copy(alpha = 0.95f),
                        shape = CircleShape
                    )
                    .graphicsLayer { shadowElevation = 4f; shape = CircleShape })
            }
        }
    }
}

private fun blockSummary(node: MacroNode): String = when (val p = node.params) {
    is BlockParams.IrSend      -> p.displayLabel.ifEmpty { "Tap ⚙ to pick IR button" }
    is BlockParams.Delay       -> "${p.ms} ms"
    is BlockParams.ShowText    -> {
        val dur      = "${p.durationMs / 1000}s"
        val asyncTag = if (p.async) " (async)" else ""
        if (p.text.isEmpty()) "duration: $dur$asyncTag" else "\"${p.text.take(28)}\"\n$dur$asyncTag"
    }
    is BlockParams.WaitConfirm -> p.message.ifEmpty { "Press OK to continue" }
    is BlockParams.IfElse      -> p.message.ifEmpty { "Continue?" }
    is BlockParams.Vibrate     -> "${p.durationMs} ms"
    is BlockParams.Repeat      -> "× ${p.count} times"
    is BlockParams.Retry       -> "Q: ${p.question.take(26)}\n${p.retryDelayMs} ms delay"
    is BlockParams.Switch      -> p.message.take(36)
    is BlockParams.Join        -> ""
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
            .background(Color(0xFF1A1730))
            .border(1.dp, tint.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
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
    MacroBlockType.VIBRATE,
    MacroBlockType.REPEAT,
    MacroBlockType.RETRY,
    MacroBlockType.JOIN,
    MacroBlockType.SWITCH,
    MacroBlockType.SHOW_TEXT,
    MacroBlockType.WAIT_CONFIRM,
    MacroBlockType.IF_ELSE,
    MacroBlockType.END
)

@Composable
private fun AddBlockDialog(
    onDismiss: () -> Unit,
    onPick: (MacroBlockType) -> Unit
) {
    val scroll = rememberScrollState()
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF121024))
                .border(1.dp, Color(0xFF9B6DFF).copy(alpha = 0.40f), RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Add Block",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Choose a block type",
                color = Color(0xFF8A8899),
                fontSize = 12.sp
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scroll),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                addableBlockTypes.forEach { type ->
                    val color = blockColor(type)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF181327))
                            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable { onPick(type) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
                        Text(blockLabel(type), color = Color.White, fontSize = 13.sp)
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1A1726))
                    .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Close", color = Color(0xFFB7B3CC), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
