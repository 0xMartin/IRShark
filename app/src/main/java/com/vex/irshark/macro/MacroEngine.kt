package com.vex.irshark.macro

import android.content.Context
import com.vex.irshark.data.MacroStep
import com.vex.irshark.data.SavedMacro
import com.vex.irshark.data.transmitIrCode
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ── Public state ──────────────────────────────────────────────────────────────

sealed class MacroRunState {
    object Idle : MacroRunState()

    data class Running(
        val macroName: String,
        val progress: String,        // e.g. "Step 3 / 10" or "Loop · iter 5"
        val displayText: String?,    // active ShowText message (null = none)
        val confirm: ConfirmRequest? // non-null = waiting for user input
    ) : MacroRunState()

    data class Finished(val macroName: String) : MacroRunState()
    data class Cancelled(val macroName: String) : MacroRunState()
}

data class ConfirmRequest(
    val message: String,
    val hasNoOption: Boolean     // true → Yes/No  (IfConfirm)
                                  // false → OK/Stop (WaitConfirm)
)

// ── Engine ────────────────────────────────────────────────────────────────────

class MacroEngine(private val context: Context) {

    private val _state = MutableStateFlow<MacroRunState>(MacroRunState.Idle)
    val state: StateFlow<MacroRunState> = _state

    private var runJob: Job? = null
    private var confirmDeferred: CompletableDeferred<Boolean>? = null

    // step counters
    private var flatStep = 0
    private var totalSteps = 0
    private var loopIter = 0
    private var inLoop = false
    private var macroName = ""
    private var displayText: String? = null

    fun launch(macro: SavedMacro, scope: CoroutineScope) {
        runJob?.cancel()
        flatStep = 0
        loopIter = 0
        inLoop = false
        displayText = null
        macroName = macro.name
        totalSteps = com.vex.irshark.data.countMacroSteps(macro.steps)
        runJob = scope.launch(Dispatchers.Default) {
            try {
                pushProgress()
                executeSteps(macro.steps)
                _state.value = MacroRunState.Finished(macroName)
            } catch (_: CancellationException) {
                _state.value = MacroRunState.Cancelled(macroName)
            }
        }
    }

    fun stop() {
        confirmDeferred?.complete(false)
        confirmDeferred = null
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
                withContext(Dispatchers.IO) { transmitIrCode(context, step.irCode) }
                delay(100L)
            }
            is MacroStep.Delay -> delay(step.ms.coerceAtLeast(1L))
            is MacroStep.ShowText -> {
                displayText = step.text
                pushProgress()
            }
            is MacroStep.WaitConfirm -> {
                val ok = suspendConfirm(step.message, hasNo = false)
                displayText = null
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
                val yes = suspendConfirm(step.message, hasNo = true)
                displayText = null
                if (yes) executeSteps(step.yesSteps) else executeSteps(step.noSteps)
            }
        }
    }

    private fun pushProgress(override: String? = null) {
        val progress = override ?: if (inLoop) "Loop · iter $loopIter"
                                    else "Step $flatStep / $totalSteps"
        val current = _state.value
        _state.value = if (current is MacroRunState.Running)
            current.copy(progress = progress, displayText = displayText, confirm = null)
        else
            MacroRunState.Running(macroName, progress, displayText, null)
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
