package com.vex.irshark.ui.macro

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.geometry.Offset
import com.vex.irshark.data.MacroStep
import com.vex.irshark.data.SavedMacro
import java.util.UUID

// ─────────────────────────────────────────────────────────────────────────────
// Block types
// ─────────────────────────────────────────────────────────────────────────────

enum class MacroBlockType {
    START, END,
    IR_SEND, DELAY, SHOW_TEXT, WAIT_CONFIRM, IF_ELSE
}

// ─────────────────────────────────────────────────────────────────────────────
// Block parameters (sealed, one variant per type that needs config)
// ─────────────────────────────────────────────────────────────────────────────

sealed class BlockParams {
    object None : BlockParams()

    data class IrSend(
        val displayLabel: String = "",
        val remoteName:   String = "",
        val buttonLabel:  String = "",
        val irCode:       String = ""
    ) : BlockParams()

    data class Delay(val ms: Long = 500L) : BlockParams()

    data class ShowText(val text: String = "", val durationMs: Long = 3000L, val async: Boolean = false) : BlockParams()

    data class WaitConfirm(val message: String = "Press OK to continue") : BlockParams()

    data class IfElse(val message: String = "Continue?") : BlockParams()
}

// ─────────────────────────────────────────────────────────────────────────────
// Output pin label  (YES / NO for IfElse; OUT for everything else)
// ─────────────────────────────────────────────────────────────────────────────

enum class PinId { OUT, YES, NO }

// ─────────────────────────────────────────────────────────────────────────────
// Node
// ─────────────────────────────────────────────────────────────────────────────

data class MacroNode(
    val id:     String         = UUID.randomUUID().toString(),
    val type:   MacroBlockType = MacroBlockType.DELAY,
    val pos:    Offset         = Offset(80f, 80f),
    val params: BlockParams    = BlockParams.None,
    val selected: Boolean      = false
) {
    /** Returns available output PinIds for this block type. */
    fun outputPins(): List<PinId> = when (type) {
        MacroBlockType.START      -> listOf(PinId.OUT)
        MacroBlockType.END        -> emptyList()
        MacroBlockType.IF_ELSE    -> listOf(PinId.YES, PinId.NO)
        else                      -> listOf(PinId.OUT)
    }

    /** Whether this block has an input pin. */
    fun hasInput(): Boolean = type != MacroBlockType.START
}

// ─────────────────────────────────────────────────────────────────────────────
// Edge  (directed: from output pin of one node → input of another)
// ─────────────────────────────────────────────────────────────────────────────

data class MacroEdge(
    val id:       String = UUID.randomUUID().toString(),
    val fromId:   String,       // source node id
    val fromPin:  PinId,        // which output pin
    val toId:     String        // destination node id (always input pin)
)

// ─────────────────────────────────────────────────────────────────────────────
// Graph state  (observable via Compose snapshot)
// ─────────────────────────────────────────────────────────────────────────────

class MacroGraph {
    val nodes: SnapshotStateList<MacroNode> = mutableStateListOf()
    val edges: SnapshotStateList<MacroEdge> = mutableStateListOf()

    // ── Mutation helpers ──────────────────────────────────────────────────

    fun addNode(node: MacroNode) { nodes.add(node) }

    fun removeNode(id: String) {
        nodes.removeAll { it.id == id }
        edges.removeAll { it.fromId == id || it.toId == id }
    }

    fun removeNodes(ids: Set<String>) {
        nodes.removeAll { it.id in ids }
        edges.removeAll { it.fromId in ids || it.toId in ids }
    }

    fun moveNode(id: String, newPos: Offset) {
        val idx = nodes.indexOfFirst { it.id == id }
        if (idx >= 0) nodes[idx] = nodes[idx].copy(pos = newPos)
    }

    fun moveNodes(ids: Set<String>, delta: Offset) {
        for (id in ids) {
            val idx = nodes.indexOfFirst { it.id == id }
            if (idx >= 0) nodes[idx] = nodes[idx].copy(pos = nodes[idx].pos + delta)
        }
    }

    fun updateParams(id: String, params: BlockParams) {
        val idx = nodes.indexOfFirst { it.id == id }
        if (idx >= 0) nodes[idx] = nodes[idx].copy(params = params)
    }

    fun setSelected(ids: Set<String>) {
        for (i in nodes.indices) {
            val n = nodes[i]
            val shouldSelect = n.id in ids
            if (n.selected != shouldSelect) nodes[i] = n.copy(selected = shouldSelect)
        }
    }

    /** Try to add an edge. Returns error string or null on success. */
    fun tryConnect(fromId: String, fromPin: PinId, toId: String): String? {
        if (fromId == toId) return "Cannot connect block to itself"
        val fromNode = nodes.firstOrNull { it.id == fromId } ?: return "Source not found"
        val toNode   = nodes.firstOrNull { it.id == toId }   ?: return "Target not found"
        if (!toNode.hasInput()) return "This block has no input"
        if (fromNode.type == MacroBlockType.END) return "End has no outputs"

        // One output pin → max one target
        if (edges.any { it.fromId == fromId && it.fromPin == fromPin }) return "Output already connected"
        // One input → max one source
        if (edges.any { it.toId == toId }) return "Input already connected"

        // Basic cycle detection: would toId eventually reach fromId?
        if (wouldCycle(from = toId, reaching = fromId)) return "Connection would create a cycle"

        edges.add(MacroEdge(fromId = fromId, fromPin = fromPin, toId = toId))
        return null
    }

    fun removeEdge(id: String) { edges.removeAll { it.id == id } }

    fun removeEdgeByPins(fromId: String, fromPin: PinId) {
        edges.removeAll { it.fromId == fromId && it.fromPin == fromPin }
    }

    fun removeEdgeToNode(toId: String) {
        edges.removeAll { it.toId == toId }
    }

    /** DFS: can we reach `reaching` starting from `from`? */
    private fun wouldCycle(from: String, reaching: String): Boolean {
        val visited = mutableSetOf<String>()
        fun dfs(cur: String): Boolean {
            if (cur == reaching) return true
            if (!visited.add(cur)) return false
            return edges.filter { it.fromId == cur }.any { dfs(it.toId) }
        }
        return dfs(from)
    }

    // ── Compiler: graph → List<MacroStep> ────────────────────────────────

    data class CompileResult(
        val steps: List<MacroStep>,
        val orphans: Int,           // nodes not reachable from Start
        val error: String?
    )

    fun compile(): CompileResult {
        val startNode = nodes.firstOrNull { it.type == MacroBlockType.START }
            ?: return CompileResult(emptyList(), 0, "No Start block found")

        val reachable = mutableSetOf<String>()
        val steps = mutableListOf<MacroStep>()
        val error = compileFrom(startNode.id, reachable, steps)
        val orphans = nodes.count { it.id !in reachable && it.type != MacroBlockType.START }
        return CompileResult(steps, orphans, error)
    }

    /** Recursive DFS compiler. Returns error string or null. */
    private fun compileFrom(
        nodeId: String,
        visited: MutableSet<String>,
        out: MutableList<MacroStep>
    ): String? {
        if (!visited.add(nodeId)) return null          // already compiled (shouldn't happen in DAG)
        val node = nodes.firstOrNull { it.id == nodeId } ?: return null

        when (node.type) {
            MacroBlockType.START -> { /* no step emitted */ }
            MacroBlockType.END   -> { out.add(MacroStep.Stop); return null }

            MacroBlockType.IR_SEND -> {
                val p = node.params as? BlockParams.IrSend ?: BlockParams.IrSend()
                out.add(MacroStep.IrSend(p.displayLabel, p.remoteName, p.buttonLabel, p.irCode))
            }
            MacroBlockType.DELAY -> {
                val p = node.params as? BlockParams.Delay ?: BlockParams.Delay()
                out.add(MacroStep.Delay(p.ms))
            }
            MacroBlockType.SHOW_TEXT -> {
                val p = node.params as? BlockParams.ShowText ?: BlockParams.ShowText()
                out.add(MacroStep.ShowText(p.text, p.durationMs, p.async))
            }
            MacroBlockType.WAIT_CONFIRM -> {
                val p = node.params as? BlockParams.WaitConfirm ?: BlockParams.WaitConfirm()
                out.add(MacroStep.WaitConfirm(p.message))
            }
            MacroBlockType.IF_ELSE -> {
                val p = node.params as? BlockParams.IfElse ?: BlockParams.IfElse()
                val yesEdge = edges.firstOrNull { it.fromId == nodeId && it.fromPin == PinId.YES }
                val noEdge  = edges.firstOrNull { it.fromId == nodeId && it.fromPin == PinId.NO }

                val yesSteps = mutableListOf<MacroStep>()
                val noSteps  = mutableListOf<MacroStep>()

                val yesVisited = mutableSetOf<String>()
                val noVisited  = mutableSetOf<String>()

                if (yesEdge != null) compileFrom(yesEdge.toId, yesVisited, yesSteps)
                if (noEdge  != null) compileFrom(noEdge.toId,  noVisited,  noSteps)

                out.add(MacroStep.IfConfirm(p.message, yesSteps, noSteps))
                // mark sub-graph nodes as visited so we don't emit them again
                visited.addAll(yesVisited)
                visited.addAll(noVisited)
                return null   // do not follow OUT pin (IF_ELSE has no OUT)
            }
        }

        // Follow the single OUT edge
        val outEdge = edges.firstOrNull { it.fromId == nodeId && it.fromPin == PinId.OUT }
        if (outEdge != null) compileFrom(outEdge.toId, visited, out)
        return null
    }

    // ── Serialization ─────────────────────────────────────────────────────

    fun toJson(): String {
        val sb = StringBuilder()
        sb.append("{\"nodes\":[")
        nodes.forEachIndexed { i, n ->
            if (i > 0) sb.append(",")
            sb.append("{")
            sb.append("\"id\":\"${n.id}\",")
            sb.append("\"type\":\"${n.type.name}\",")
            sb.append("\"x\":${n.pos.x},\"y\":${n.pos.y},")
            sb.append("\"params\":${paramsToJson(n.params)}")
            sb.append("}")
        }
        sb.append("],\"edges\":[")
        edges.forEachIndexed { i, e ->
            if (i > 0) sb.append(",")
            sb.append("{\"id\":\"${e.id}\",\"from\":\"${e.fromId}\",\"pin\":\"${e.fromPin.name}\",\"to\":\"${e.toId}\"}")
        }
        sb.append("]}")
        return sb.toString()
    }

    private fun paramsToJson(p: BlockParams): String = when (p) {
        is BlockParams.None        -> "null"
        is BlockParams.IrSend      -> "{\"kind\":\"ir\",\"label\":${jsonStr(p.displayLabel)},\"remote\":${jsonStr(p.remoteName)},\"button\":${jsonStr(p.buttonLabel)},\"code\":${jsonStr(p.irCode)}}"
        is BlockParams.Delay       -> "{\"kind\":\"delay\",\"ms\":${p.ms}}"
        is BlockParams.ShowText    -> "{\"kind\":\"show\",\"text\":${jsonStr(p.text)},\"dur\":${p.durationMs},\"async\":${p.async}}"
        is BlockParams.WaitConfirm -> "{\"kind\":\"wait\",\"msg\":${jsonStr(p.message)}}"
        is BlockParams.IfElse      -> "{\"kind\":\"if\",\"msg\":${jsonStr(p.message)}}"
    }

    private fun jsonStr(s: String) = "\"${s.replace("\\","\\\\").replace("\"","\\\"")}\""

    companion object {
        fun fromJson(json: String): MacroGraph {
            val g = MacroGraph()
            try {
                val root = org.json.JSONObject(json)
                val nodesArr = root.getJSONArray("nodes")
                for (i in 0 until nodesArr.length()) {
                    val o    = nodesArr.getJSONObject(i)
                    val type = MacroBlockType.valueOf(o.getString("type"))
                    val pObj = if (o.isNull("params")) null else o.optJSONObject("params")
                    val params: BlockParams = when (pObj?.optString("kind")) {
                        "ir"    -> BlockParams.IrSend(
                            pObj.optString("label"), pObj.optString("remote"),
                            pObj.optString("button"), pObj.optString("code"))
                        "delay" -> BlockParams.Delay(pObj.optLong("ms", 500))
                        "show"  -> BlockParams.ShowText(pObj.optString("text"), pObj.optLong("dur", 3000L), pObj.optBoolean("async", false))
                        "wait"  -> BlockParams.WaitConfirm(pObj.optString("msg"))
                        "if"    -> BlockParams.IfElse(pObj.optString("msg"))
                        else    -> BlockParams.None
                    }
                    g.nodes.add(MacroNode(
                        id     = o.getString("id"),
                        type   = type,
                        pos    = Offset(o.getDouble("x").toFloat(), o.getDouble("y").toFloat()),
                        params = params
                    ))
                }
                val edgesArr = root.getJSONArray("edges")
                for (i in 0 until edgesArr.length()) {
                    val o = edgesArr.getJSONObject(i)
                    g.edges.add(MacroEdge(
                        id      = o.getString("id"),
                        fromId  = o.getString("from"),
                        fromPin = PinId.valueOf(o.getString("pin")),
                        toId    = o.getString("to")
                    ))
                }
            } catch (_: Exception) {}
            return g
        }

        /** Build a fresh graph with just a Start block. */
        fun empty(): MacroGraph {
            val g = MacroGraph()
            g.nodes.add(MacroNode(
                id     = "start",
                type   = MacroBlockType.START,
                pos    = Offset(200f, 120f),
                params = BlockParams.None
            ))
            return g
        }
    }
}
