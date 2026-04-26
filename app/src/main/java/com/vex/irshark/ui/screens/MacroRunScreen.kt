package com.vex.irshark.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import com.vex.irshark.macro.ConfirmRequest
import com.vex.irshark.macro.IrLogEntry
import com.vex.irshark.macro.MacroRunState
import com.vex.irshark.macro.SwitchRequest

@Composable
fun MacroRunScreen(
    state:      MacroRunState.Running,
    onStop:     () -> Unit,
    onYes:      () -> Unit,   // WaitConfirm OK  /  IfConfirm Yes
    onNo:       () -> Unit,   // IfConfirm No
    onSwitch:   (Int) -> Unit // Switch option selected (index, -1 = default)
) {
    val violet = MaterialTheme.colorScheme.primary

    // Parse progress fraction for the progress bar
    val fraction = run {
        val parts = state.progress.split("/")
        if (parts.size == 2) {
            val cur = parts[0].filter { it.isDigit() }.toLongOrNull()
            val tot = parts[1].filter { it.isDigit() }.toLongOrNull()
            if (cur != null && tot != null && tot > 0) cur.toFloat() / tot.toFloat() else null
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Header card ───────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF100D1C))
                .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            "Running: ${state.macroName}",
                            color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            state.progress,
                            color = violet, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    // Stop button
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF2A0A18))
                            .border(1.dp, Color(0xFFFF7B9D).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onStop),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "Stop",
                            tint = Color(0xFFFF7B9D),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // Progress bar (only when we have a fraction)
                if (fraction != null) {
                    LinearProgressIndicator(
                        progress         = { fraction.coerceIn(0f, 1f) },
                        modifier         = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color            = violet,
                        trackColor       = violet.copy(alpha = 0.18f)
                    )
                } else {
                    // Indeterminate for loops
                    LinearProgressIndicator(
                        modifier   = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color      = violet,
                        trackColor = violet.copy(alpha = 0.18f)
                    )
                }
            }
        }

        // ── Display text (ShowText step) ──────────────────────────────────
        if (!state.displayText.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0C1A12))
                    .border(1.dp, Color(0xFF5BFF9A).copy(alpha = 0.30f), RoundedCornerShape(14.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text      = state.displayText,
                    color     = Color(0xFF5BFF9A),
                    fontSize  = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Confirm prompt ────────────────────────────────────────────────
        state.confirm?.let { req ->
            ConfirmCard(req = req, onYes = onYes, onNo = onNo)
        }
        // ── Switch prompt ─────────────────────────────────────────────────
        state.switch?.let { req ->
            SwitchCard(req = req, onSelect = onSwitch)
        }
        // ── IR transmission log ─────────────────────────────────────────────────────
        if (state.irLog.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0D0A1A))
                    .border(1.dp, Color(0xFF9B6DFF).copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("IR Log", color = Color(0xFF9B6DFF), fontSize = 11.sp,
                        fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    state.irLog.takeLast(10).reversed().forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF9B6DFF).copy(alpha = 0.70f))
                                )
                                Text(
                                    entry.displayLabel,
                                    color    = Color.White.copy(alpha = 0.90f),
                                    fontSize = 12.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                entry.remoteName,
                                color    = Color(0xFF9B6DFF).copy(alpha = 0.65f),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }    }
}

@Composable
private fun ConfirmCard(req: ConfirmRequest, onYes: () -> Unit, onNo: () -> Unit) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF1A1208))
            .border(1.dp, Color(0xFFC27C1A).copy(alpha = 0.50f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                text      = req.message,
                color     = Color(0xFFFFC14D),
                fontSize  = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier  = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (req.hasNoOption) {
                    // IfConfirm: Yes / No
                    ConfirmButton(
                        label   = "Yes",
                        icon    = Icons.Filled.Check,
                        tint    = Color(0xFF5BFF9A),
                        bg      = Color(0x225BFF9A),
                        border  = Color(0xFF5BFF9A),
                        modifier = Modifier.weight(1f),
                        onClick = onYes
                    )
                    ConfirmButton(
                        label   = "No",
                        icon    = Icons.Filled.Close,
                        tint    = Color(0xFFFF7B9D),
                        bg      = Color(0xFF1A1726),
                        border  = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.weight(1f),
                        onClick = onNo
                    )
                } else {
                    // WaitConfirm: OK / Stop
                    ConfirmButton(
                        label   = "OK – Continue",
                        icon    = Icons.Filled.Check,
                        tint    = violet,
                        bg      = violet.copy(alpha = 0.18f),
                        border  = violet,
                        modifier = Modifier.weight(1f),
                        onClick = onYes
                    )
                    ConfirmButton(
                        label   = "Stop macro",
                        icon    = Icons.Filled.Stop,
                        tint    = Color(0xFFFF7B9D),
                        bg      = Color(0xFF1A1726),
                        border  = Color.White.copy(alpha = 0.18f),
                        modifier = Modifier.weight(1f),
                        onClick = onNo
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchCard(req: SwitchRequest, onSelect: (Int) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D1420))
            .border(1.dp, Color(0xFF3D8ADF).copy(alpha = 0.50f), RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text       = req.message,
                color      = Color(0xFF7BB4FF),
                fontSize   = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign  = TextAlign.Center,
                modifier   = Modifier.fillMaxWidth()
            )
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            req.options.forEachIndexed { i, opt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF9B6DFF).copy(alpha = 0.14f))
                        .border(1.dp, Color(0xFF9B6DFF).copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable { onSelect(i) }
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(opt, color = Color.White, fontSize = 13.sp)
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A1726))
                    .border(1.dp, Color(0xFFFFBB6D).copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                    .clickable { onSelect(-1) }
                    .padding(10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Default / Skip", color = Color(0xFFFFBB6D), fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun ConfirmButton(
    label:    String,
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    tint:     Color,
    bg:       Color,
    border:   Color,
    modifier: Modifier = Modifier,
    onClick:  () -> Unit
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
