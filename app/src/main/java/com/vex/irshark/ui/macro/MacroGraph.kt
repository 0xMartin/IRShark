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
    IR_SEND, DELAY, SHOW_TEXT, WAIT_CONFIRM, IF_ELSE,
    VIBRATE, REPEAT, SWITCH
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
        val irCode:       String = "",
        val irSource:     String = ""   // "DB" | "CUSTOM" | ""
    ) : BlockParams()

    data class Delay(val ms: Long = 500L) : BlockParams()

    data class ShowText(val text: String = "", val durationMs: Long = 3000L, val async: Boolean = false) : BlockParams()

    data class WaitConfirm(val message: String = "Press OK to continue") : BlockParams()

    data class IfElse(val message: String = "Continue?") : BlockParams()

    data class Vibrate(val durationMs: Long = 500L) : BlockParams()

    /** Loop: repeat the body chain N times, then follow the continue pin. */
    data class Repeat(val count: Int = 3) : BlockParams()

    data class Switch(
        val message: String = "Choose an option",
        val options: List<String> = listOf("Option 1", "Option 2")
    ) : BlockParams()
}

// ─────────────────────────────────────────────────────────────────────────────
// Output pin label  (YES / NO for IfElse; OUT for everything else)
// ─────────────────────────────────────────────────────────────────────────────

enum class PinId {
    OUT, YES, NO,
    BODY, CONT,                              // REPEAT block pins
    OPT0, OPT1, OPT2, OPT3, OPT4,           // SWITCH option pins
    OPT5, OPT6, OPT7, OPT8, OPT9,
    DEFAULT                                   // SWITCH default pin
}

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
        MacroBlockType.REPEAT     -> listOf(PinId.BODY, PinId.CONT)
        MacroBlockType.SWITCH     -> {
            val p = params as? BlockParams.Switch ?: BlockParams.Switch()
            val optPins = listOf(PinId.OPT0, PinId.OPT1, PinId.OPT2, PinId.OPT3, PinId.OPT4,
                                 PinId.OPT5, PinId.OPT6, PinId.OPT7, PinId.OPT8, PinId.OPT9)
            optPins.take(p.options.size) + listOf(PinId.DEFAULT)
        }
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

    /** Try to add an edge. Returns error string or null on success.
     *  If the output pin or input pin is already connected, the old wire is replaced. */
    fun tryConnect(fromId: String, fromPin: PinId, toId: String): String? {
        if (fromId == toId) return "Cannot connect block to itself"
        val fromNode = nodes.firstOrNull { it.id == fromId } ?: return "Source not found"
        val toNode   = nodes.firstOrNull { it.id == toId }   ?: return "Target not found"
        if (!toNode.hasInput()) return "This block has no input"
        if (fromNode.type == MacroBlockType.END) return "End has no outputs"

        // Basic cycle detection: would toId eventually reach fromId?
        if (wouldCycle(from = toId, reaching = fromId)) return "Connection would create a cycle"

        // Remove any existing wire from this output pin, and any existing wire into the target input
        edges.removeAll { (it.fromId == fromId && it.fromPin == fromPin) || it.toId == toId }

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
            MacroBlockType.VIBRATE -> {
                val p = node.params as? BlockParams.Vibrate ?: BlockParams.Vibrate()
                out.add(MacroStep.Vibrate(p.durationMs))
            }
            MacroBlockType.REPEAT -> {
                val p = node.params as? BlockParams.Repeat ?: BlockParams.Repeat()
                // BODY pin → steps that execute on each iteration
                val bodyEdge    = edges.firstOrNull { it.fromId == nodeId && it.fromPin == PinId.BODY }
                val bodySteps   = mutableListOf<MacroStep>()
                val bodyVisited = mutableSetOf<String>()
                if (bodyEdge != null) compileFrom(bodyEdge.toId, bodyVisited, bodySteps)
                visited.addAll(bodyVisited)
                out.add(MacroStep.RepeatBlock(p.count.coerceAtLeast(1), bodySteps))
                // CONT pin → steps executed after all iterations complete
                val contEdge = edges.firstOrNull { it.fromId == nodeId && it.fromPin == PinId.CONT }
                if (contEdge != null) compileFrom(contEdge.toId, visited, out)
                return null
            }
            MacroBlockType.SWITCH -> {
                val p = node.params as? BlockParams.Switch ?: BlockParams.Switch()
                val allOptPins = listOf(PinId.OPT0, PinId.OPT1, PinId.OPT2, PinId.OPT3, PinId.OPT4,
                                        PinId.OPT5, PinId.OPT6, PinId.OPT7, PinId.OPT8, PinId.OPT9)
                val branches = p.options.mapIndexed { i, _ ->
                    val pin = allOptPins.getOrElse(i) { PinId.OPT9 }
                    val edge = edges.firstOrNull { it.fromId == nodeId && it.fromPin == pin }
                    val steps = mutableListOf<MacroStep>()
                    val vis   = mutableSetOf<String>()
                    if (edge != null) compileFrom(edge.toId, vis, steps)
                    visited.addAll(vis)
                    steps.toList()
                }
                val defaultEdge  = edges.firstOrNull { it.fromId == nodeId && it.fromPin == PinId.DEFAULT }
                val defaultSteps = mutableListOf<MacroStep>()
                val defVis       = mutableSetOf<String>()
                if (defaultEdge != null) compileFrom(defaultEdge.toId, defVis, defaultSteps)
                visited.addAll(defVis)
                out.add(MacroStep.Switch(p.message, p.options, branches, defaultSteps.toList()))
                return null   // all execution goes through branches
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
        is BlockParams.IrSend      -> "{\"kind\":\"ir\",\"label\":${jsonStr(p.displayLabel)},\"remote\":${jsonStr(p.remoteName)},\"button\":${jsonStr(p.buttonLabel)},\"code\":${jsonStr(p.irCode)},\"source\":${jsonStr(p.irSource)}}"
        is BlockParams.Delay       -> "{\"kind\":\"delay\",\"ms\":${p.ms}}"
        is BlockParams.ShowText    -> "{\"kind\":\"show\",\"text\":${jsonStr(p.text)},\"dur\":${p.durationMs},\"async\":${p.async}}"
        is BlockParams.WaitConfirm -> "{\"kind\":\"wait\",\"msg\":${jsonStr(p.message)}}"
        is BlockParams.IfElse      -> "{\"kind\":\"if\",\"msg\":${jsonStr(p.message)}}"
        is BlockParams.Vibrate     -> "{\"kind\":\"vibrate\",\"ms\":${p.durationMs}}"
        is BlockParams.Repeat      -> "{\"kind\":\"repeat\",\"count\":${p.count}}"
        is BlockParams.Switch      -> {
            val opts = p.options.joinToString(",") { jsonStr(it) }
            "{\"kind\":\"switch\",\"msg\":${jsonStr(p.message)},\"options\":[$opts]}"
        }
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
                            pObj.optString("button"), pObj.optString("code"),
                            pObj.optString("source"))
                        "delay" -> BlockParams.Delay(pObj.optLong("ms", 500))
                        "show"  -> BlockParams.ShowText(pObj.optString("text"), pObj.optLong("dur", 3000L), pObj.optBoolean("async", false))
                        "wait"    -> BlockParams.WaitConfirm(pObj.optString("msg"))
                        "if"      -> BlockParams.IfElse(pObj.optString("msg"))
                        "vibrate" -> BlockParams.Vibrate(pObj.optLong("ms", 500L))
                        "repeat"  -> BlockParams.Repeat(pObj.optInt("count", 3))
                        "switch"  -> {
                            val arr  = pObj.optJSONArray("options") ?: org.json.JSONArray()
                            val opts = (0 until arr.length()).map { arr.optString(it) }
                            BlockParams.Switch(pObj.optString("msg", "Choose an option"), opts)
                        }
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
