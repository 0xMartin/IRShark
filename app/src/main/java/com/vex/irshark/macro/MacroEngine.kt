package com.vex.irshark.macro

import android.content.Context
import com.vex.irshark.data.MacroStep
import com.vex.irshark.data.SavedMacro
import com.vex.irshark.util.transmitIrCode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── IR log entry ─────────────────────────────────────────────────────────────

data class IrLogEntry(
    val displayLabel: String,
    val remoteName:   String,
    val buttonLabel:  String,
    val irSource:     String = "",
    val elapsedMs:    Long   = 0L
)

// ── Public state ──────────────────────────────────────────────────────────────

sealed class MacroRunState {
    object Idle : MacroRunState()

    data class Running(
        val macroName:    String,
        val progress:     String,        // e.g. "Step 3 / 10" or "Loop · iter 5"
        val displayTexts: List<String>,  // active ShowText messages (empty = none)
        val confirm:      ConfirmRequest?,// non-null = waiting for yes/no input
        val irLog:        List<IrLogEntry> = emptyList(),
        val switch:       SwitchRequest? = null  // non-null = waiting for option pick
    ) : MacroRunState()

    data class Finished(val macroName: String, val irLog: List<IrLogEntry> = emptyList()) : MacroRunState()
    data class Cancelled(val macroName: String, val irLog: List<IrLogEntry> = emptyList()) : MacroRunState()
}

data class ConfirmRequest(
    val message: String,
    val hasNoOption: Boolean     // true → Yes/No  (IfConfirm)
                                  // false → OK/Stop (WaitConfirm)
)

data class SwitchRequest(
    val message: String,
    val options: List<String>
)

// ── Engine ────────────────────────────────────────────────────────────────────

class MacroEngine(private val context: Context) {

    private val _state = MutableStateFlow<MacroRunState>(MacroRunState.Idle)
    val state: StateFlow<MacroRunState> = _state

    private val _irTransmitEvent = MutableSharedFlow<IrLogEntry>(extraBufferCapacity = 8)
    val irTransmitEvent: SharedFlow<IrLogEntry> = _irTransmitEvent

    private var runJob:  Job? = null
    private var runScope: CoroutineScope? = null
    private var confirmDeferred: CompletableDeferred<Boolean>? = null
    private var switchDeferred:  CompletableDeferred<Int>? = null

    // step counters
    private var flatStep  = 0
    private var totalSteps = 0
    private var loopIter  = 0
    private var inLoop    = false
    private var macroName = ""
    private var macroStartTime = 0L
    private val displayTexts = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var irLog: List<IrLogEntry> = emptyList()

    fun launch(macro: SavedMacro, scope: CoroutineScope) {
        runJob?.cancel()
        flatStep    = 0
        loopIter    = 0
        inLoop      = false
        displayTexts.clear()
        irLog           = emptyList()
        macroStartTime  = System.currentTimeMillis()
        runScope    = scope
        macroName   = macro.name
        totalSteps  = com.vex.irshark.data.countMacroSteps(macro.steps)
        runJob = scope.launch(Dispatchers.Default) {
            try {
                pushProgress()
                executeSteps(macro.steps)
                _state.value = MacroRunState.Finished(macroName, irLog)
            } catch (_: CancellationException) {
                _state.value = MacroRunState.Cancelled(macroName, irLog)
            }
        }
    }

    fun stop() {
        confirmDeferred?.complete(false)
        confirmDeferred = null
        switchDeferred?.complete(-1)
        switchDeferred = null
        runJob?.cancel()
    }

    /** Called when user taps OK on a WaitConfirm or Yes on IfConfirm. */
    fun respondYes() {
        confirmDeferred?.complete(true)
        confirmDeferred = null
    }

    /** Called when user taps No on an IfConfirm. */
    fun respondNo() {
        confirmDeferred?.complete(false)
        confirmDeferred = null
    }

    /** Called when user picks an option in a Switch block. index=-1 means default. */
    fun respondSwitch(index: Int) {
        switchDeferred?.complete(index)
        switchDeferred = null
    }

    // ── Internal execution ────────────────────────────────────────────────────

    private suspend fun executeSteps(steps: List<MacroStep>) {
        for (step in steps) {
            currentCoroutineContext().ensureActive()
            executeStep(step)
        }
    }

    private suspend fun executeStep(step: MacroStep) {
        currentCoroutineContext().ensureActive()
        flatStep++
        pushProgress()

        when (step) {
            is MacroStep.IrSend -> {
                val elapsed = System.currentTimeMillis() - macroStartTime
                val entry = IrLogEntry(step.displayLabel, step.remoteName, step.buttonLabel, step.irSource, elapsed)
                irLog = irLog + entry
                _irTransmitEvent.tryEmit(entry)
                pushProgress()  // update state with new log entry immediately
                withContext(Dispatchers.IO) { transmitIrCode(context, step.irCode) }
                delay(100L)
            }
            is MacroStep.Delay -> delay(step.ms.coerceAtLeast(1L))
            is MacroStep.ShowText -> {
                displayTexts.add(step.text)
                pushProgress()
                if (step.async) {
                    // Non-blocking: remove this text after duration without halting execution
                    val textSnapshot = step.text
                    val durationMs   = step.durationMs.coerceAtLeast(100L)
                    runScope?.launch(Dispatchers.Default) {
                        delay(durationMs)
                        displayTexts.remove(textSnapshot)
                        pushProgress()
                    }
                } else {
                    delay(step.durationMs.coerceAtLeast(100L))
                    displayTexts.remove(step.text)
                    pushProgress()
                }
            }
            is MacroStep.WaitConfirm -> {
                val ok = suspendConfirm(step.message, hasNo = false)
                if (!ok) throw CancellationException("User stopped macro")
            }
            is MacroStep.RepeatBlock -> {
                repeat(step.count) { iter ->
                    currentCoroutineContext().ensureActive()
                    pushProgress("Repeat ${iter + 1}/${step.count}")
                    executeSteps(step.steps)
                }
                pushProgress()
            }
            is MacroStep.LoopUntilStop -> {
                inLoop = true
                loopIter = 0
                while (true) {
                    currentCoroutineContext().ensureActive()
                    loopIter++
                    pushProgress("Loop · iter $loopIter")
                    executeSteps(step.steps)
                }
            }
            is MacroStep.IfConfirm -> {
                val yes    = suspendConfirm(step.message, hasNo = true)
                val branch = if (yes) step.yesSteps else step.noSteps
                // Dynamically recalculate total: steps executed so far + steps in chosen branch
                totalSteps = flatStep + com.vex.irshark.data.countMacroSteps(branch)
                pushProgress()
                if (yes) executeSteps(step.yesSteps) else executeSteps(step.noSteps)
            }
            is MacroStep.Stop -> throw CancellationException("Stop block reached")
            is MacroStep.Vibrate -> {
                // Fire-and-forget: vibrate without blocking macro execution
                runScope?.launch(Dispatchers.Main) {
                    val vib = context.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                        as? android.os.Vibrator
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        vib?.vibrate(android.os.VibrationEffect.createOneShot(
                            step.durationMs.coerceIn(1L, 5000L),
                            android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vib?.vibrate(step.durationMs.coerceIn(1L, 5000L))
                    }
                }
            }
            is MacroStep.Switch -> {
                val idx    = suspendSwitch(step.message, step.options)
                val branch = if (idx in step.branches.indices) step.branches[idx] else step.defaultBranch
                totalSteps = flatStep + com.vex.irshark.data.countMacroSteps(branch)
                pushProgress()
                executeSteps(branch)
            }
        }
    }

    private fun pushProgress(override: String? = null) {
        val progress = override ?: if (inLoop) "Loop · iter $loopIter"
                                    else "Step $flatStep / $totalSteps"
        val current = _state.value
        _state.value = if (current is MacroRunState.Running)
            current.copy(progress = progress, displayTexts = displayTexts.toList(), confirm = null, switch = null, irLog = irLog)
        else
            MacroRunState.Running(macroName, progress, displayTexts.toList(), null, irLog)
    }

    private suspend fun suspendSwitch(message: String, options: List<String>): Int {
        val deferred = CompletableDeferred<Int>()
        switchDeferred = deferred
        val current = _state.value
        if (current is MacroRunState.Running) {
            _state.value = current.copy(switch = SwitchRequest(message, options))
        }
        return deferred.await()
    }

    private suspend fun suspendConfirm(message: String, hasNo: Boolean): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmDeferred = deferred
        val current = _state.value
        if (current is MacroRunState.Running) {
            _state.value = current.copy(confirm = ConfirmRequest(message, hasNo))
        }
        return deferred.await()
    }
}
