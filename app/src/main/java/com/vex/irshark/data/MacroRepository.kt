package com.vex.irshark.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

// ── Step model ────────────────────────────────────────────────────────────────

sealed class MacroStep {
    /** Send an IR code. irCode is the raw payload string. */
    data class IrSend(
        val displayLabel: String,
        val remoteName: String,
        val buttonLabel: String,
        val irCode: String
    ) : MacroStep()

    /** Wait N milliseconds. */
    data class Delay(val ms: Long) : MacroStep()

    /** Show a text message on the run screen. */
    data class ShowText(val text: String) : MacroStep()

    /** Pause and wait for the user to tap OK (or Stop). */
    data class WaitConfirm(val message: String) : MacroStep()

    /** Repeat the child steps a fixed number of times. */
    data class RepeatBlock(val count: Int, val steps: List<MacroStep>) : MacroStep()

    /** Loop the child steps indefinitely until the user stops the macro. */
    data class LoopUntilStop(val steps: List<MacroStep>) : MacroStep()

    /** Ask the user Yes/No and run the appropriate branch. */
    data class IfConfirm(
        val message: String,
        val yesSteps: List<MacroStep>,
        val noSteps: List<MacroStep>
    ) : MacroStep()

    /** Immediately terminate the macro. */
    object Stop : MacroStep()
}

// ── Macro model ───────────────────────────────────────────────────────────────

data class SavedMacro(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val steps: List<MacroStep>,
    val blocklyXml: String = ""          // Blockly workspace XML for re-editing
)

// ── Persistence ───────────────────────────────────────────────────────────────

private const val KEY_MACROS = "saved_macros_v2"

fun loadSavedMacros(context: Context): List<SavedMacro> {
    val prefs = context.getSharedPreferences("irshark_prefs", Context.MODE_PRIVATE)
    val json = prefs.getString(KEY_MACROS, null) ?: return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { parseMacroObj(arr.optJSONObject(it) ?: return@mapNotNull null) }
    } catch (_: Exception) {
        emptyList()
    }
}

fun saveSavedMacros(context: Context, macros: List<SavedMacro>) {
    val prefs = context.getSharedPreferences("irshark_prefs", Context.MODE_PRIVATE)
    prefs.edit().putString(KEY_MACROS, JSONArray().apply { macros.forEach { put(macroToObj(it)) } }.toString()).apply()
}

// ── JSON serialization ────────────────────────────────────────────────────────

private fun macroToObj(m: SavedMacro) = JSONObject().apply {
    put("id", m.id)
    put("name", m.name)
    put("blocklyXml", m.blocklyXml)
    put("steps", stepsToArr(m.steps))
}

private fun stepsToArr(steps: List<MacroStep>): JSONArray = JSONArray().apply { steps.forEach { put(stepToObj(it)) } }

private fun stepToObj(s: MacroStep): JSONObject = JSONObject().apply {
    when (s) {
        is MacroStep.IrSend -> {
            put("type", "ir_send")
            put("displayLabel", s.displayLabel)
            put("remoteName", s.remoteName)
            put("buttonLabel", s.buttonLabel)
            put("irCode", s.irCode)
        }
        is MacroStep.Delay -> { put("type", "delay"); put("ms", s.ms) }
        is MacroStep.ShowText -> { put("type", "show_text"); put("text", s.text) }
        is MacroStep.WaitConfirm -> { put("type", "wait_confirm"); put("message", s.message) }
        is MacroStep.RepeatBlock -> { put("type", "repeat"); put("count", s.count); put("steps", stepsToArr(s.steps)) }
        is MacroStep.LoopUntilStop -> { put("type", "loop_until_stop"); put("steps", stepsToArr(s.steps)) }
        is MacroStep.IfConfirm -> {
            put("type", "if_confirm"); put("message", s.message)
            put("yesSteps", stepsToArr(s.yesSteps)); put("noSteps", stepsToArr(s.noSteps))
        }
        is MacroStep.Stop -> { put("type", "stop") }
    }
}

private fun parseMacroObj(o: JSONObject): SavedMacro? {
    val name = o.optString("name").takeIf { it.isNotBlank() } ?: return null
    return SavedMacro(
        id = o.optString("id").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
        name = name,
        blocklyXml = o.optString("blocklyXml"),
        steps = parseStepsArr(o.optJSONArray("steps") ?: JSONArray())
    )
}

fun parseStepsArr(arr: JSONArray): List<MacroStep> =
    (0 until arr.length()).mapNotNull { parseStepObj(arr.optJSONObject(it) ?: return@mapNotNull null) }

/** Parses a JSON array string (from Blockly JS) into steps. */
fun parseMacroStepsFromJson(json: String): List<MacroStep> =
    try { parseStepsArr(JSONArray(json)) } catch (_: Exception) { emptyList() }

private fun parseStepObj(o: JSONObject): MacroStep? = when (o.optString("type")) {
    "ir_send" -> MacroStep.IrSend(
        displayLabel = o.optString("displayLabel"),
        remoteName   = o.optString("remoteName"),
        buttonLabel  = o.optString("buttonLabel"),
        irCode       = o.optString("irCode")
    )
    "delay"         -> MacroStep.Delay(o.optLong("ms", 500L))
    "show_text"     -> MacroStep.ShowText(o.optString("text"))
    "wait_confirm"  -> MacroStep.WaitConfirm(o.optString("message", "Continue?"))
    "repeat"        -> MacroStep.RepeatBlock(o.optInt("count", 1), parseStepsArr(o.optJSONArray("steps") ?: JSONArray()))
    "loop_until_stop" -> MacroStep.LoopUntilStop(parseStepsArr(o.optJSONArray("steps") ?: JSONArray()))
    "if_confirm"    -> MacroStep.IfConfirm(
        message  = o.optString("message", "Continue?"),
        yesSteps = parseStepsArr(o.optJSONArray("yesSteps") ?: JSONArray()),
        noSteps  = parseStepsArr(o.optJSONArray("noSteps") ?: JSONArray())
    )
    "stop" -> MacroStep.Stop
    else -> null
}

/** Flat step count (used for progress display; loops counted as 1). */
fun countMacroSteps(steps: List<MacroStep>): Int = steps.sumOf {
    when (it) {
        is MacroStep.RepeatBlock   -> 1 + countMacroSteps(it.steps)
        is MacroStep.LoopUntilStop -> 1 + countMacroSteps(it.steps)
        is MacroStep.IfConfirm     -> 1
        is MacroStep.Stop          -> 1
        else                       -> 1
    }
}
