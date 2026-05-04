package com.m4r71n.irshark.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.m4r71n.irshark.BuildConfig
import com.m4r71n.irshark.R
import com.m4r71n.irshark.data.RemoteHistoryEntry
import com.m4r71n.irshark.ui.components.ListRow
import com.m4r71n.irshark.util.IrCompatibilityReport
import com.m4r71n.irshark.util.IrTxMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val historyDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

@Composable
fun SettingsScreen(
    intervalMs: Float,
    autoStopAtEnd: Boolean,
    showTxLed: Boolean,
    hapticFeedback: Boolean,
    txModeRaw: String,
    bridgeEndpoint: String,
    compatibilityReport: IrCompatibilityReport,
    useDownloadedDb: Boolean,
    downloadedDbAvailable: Boolean,
    bundledDbVersion: String,
    downloadedDbVersion: String?,
    effectiveDbSourceLabel: String,
    historyEntries: List<RemoteHistoryEntry>,
    onOpenHistoryItem: (RemoteHistoryEntry) -> Unit,
    onIntervalChange: (Float) -> Unit,
    onAutoStopAtEndChange: (Boolean) -> Unit,
    onShowTxLedChange: (Boolean) -> Unit,
    onHapticFeedbackChange: (Boolean) -> Unit,
    onTxModeChange: (String) -> Unit,
    onBridgeEndpointChange: (String) -> Unit,
    onUseDefaultDb: () -> Unit,
    onUseDownloadedDb: () -> Unit,
    onImportDatabaseZip: () -> Unit,
    onIntervalPresetSelect: (Float) -> Unit,
    onResetDefaults: () -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary
    val txMode = IrTxMode.fromRaw(txModeRaw)
    val historyMaxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.5f
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("IRShark", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Version ${BuildConfig.VERSION_NAME}", color = Color(0xFF8A8899), fontSize = 12.sp)
                    Text("Author: 0xM4R71N", color = Color(0xFF8A8899), fontSize = 12.sp)
                    Text(
                        text = "GitHub: github.com/0xMartin",
                        color = violet,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/0xMartin")
                        }
                    )

                    Button(
                        onClick = { uriHandler.openUri("https://github.com/sponsors/0xMartin") },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDB61A2),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(999.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 0.dp
                        )
                    ) {
                        Icon(
                            Icons.Filled.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Sponsor", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Image(
                    painter = painterResource(id = R.drawable.app_icon),
                    contentDescription = "IRShark logo",
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(20.dp))
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Transmission", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

                Text(
                    text = "Phone IR blaster: ${if (compatibilityReport.hasIrEmitter) "Available" else "Not detected"}",
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )
                Text(
                    text = "Effective route: ${compatibilityReport.effectiveRoute}",
                    color = if (compatibilityReport.canTransmit) Color(0xFF9BE7B6) else Color(0xFFFFA1A1),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = compatibilityReport.message,
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onTxModeChange(IrTxMode.AUTO.rawValue) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (txMode == IrTxMode.AUTO) violet else Color(0xFF8A8899)
                        )
                    ) {
                        Text("Auto", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onTxModeChange(IrTxMode.LOCAL.rawValue) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (txMode == IrTxMode.LOCAL) violet else Color(0xFF8A8899)
                        )
                    ) {
                        Text("Local", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { onTxModeChange(IrTxMode.BRIDGE_HTTP.rawValue) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (txMode == IrTxMode.BRIDGE_HTTP) violet else Color(0xFF8A8899)
                        )
                    ) {
                        Text("Bridge", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = bridgeEndpoint,
                    onValueChange = onBridgeEndpointChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Bridge endpoint") },
                    placeholder = { Text("http://192.168.1.20:8080/ir/send") },
                    textStyle = androidx.compose.material3.LocalTextStyle.current.copy(fontSize = 12.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = violet.copy(alpha = 0.45f),
                        unfocusedBorderColor = violet.copy(alpha = 0.2f),
                        focusedContainerColor = Color(0xFF13101E),
                        unfocusedContainerColor = Color(0xFF13101E)
                    )
                )

                Text(
                    text = "Bridge mode sends JSON POST with field payload.",
                    color = Color(0xFF6A6880),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Database", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Active source: $effectiveDbSourceLabel",
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onUseDefaultDb,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (!useDownloadedDb) violet else Color(0xFF8A8899)
                        )
                    ) {
                        Text("Use default", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = onUseDownloadedDb,
                        enabled = downloadedDbAvailable,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (useDownloadedDb) violet else Color(0xFF8A8899),
                            disabledContentColor = Color(0xFF5F5B74)
                        )
                    ) {
                        Text("Use downloaded", fontSize = 11.sp)
                    }
                }

                Text(
                    text = "Downloaded: ${downloadedDbVersion ?: "Not imported"}",
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )

                Text(
                    text = "Supports Flipper Zero .ir format only. Import a ZIP archive containing .ir files (e.g. flipper-irdb).",
                    color = Color(0xFF6A6880),
                    fontSize = 10.sp
                )

                Button(
                    onClick = onImportDatabaseZip,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Import database ZIP", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Universal Remote IR delay", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${intervalMs.toInt()} ms between codes", color = Color(0xFF8A8899), fontSize = 11.sp)
                Slider(
                    value = intervalMs,
                    onValueChange = onIntervalChange,
                    valueRange = 0f..1000f
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SpeedPresetButton("Fast", onClick = { onIntervalPresetSelect(50f) })
                    SpeedPresetButton("Balanced", onClick = { onIntervalPresetSelect(150f) })
                    SpeedPresetButton("Safe", onClick = { onIntervalPresetSelect(400f) })
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleCard(
            title = "Auto stop at end",
            subtitle = "Stop universal sending automatically when last profile is reached",
            checked = autoStopAtEnd,
            onCheckedChange = onAutoStopAtEndChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleCard(
            title = "Show TX LED",
            subtitle = "Animated indicator in header when IR transmission is active",
            checked = showTxLed,
            onCheckedChange = onShowTxLedChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleCard(
            title = "Haptic feedback",
            subtitle = "Vibrate briefly when an IR button is pressed",
            checked = hapticFeedback,
            onCheckedChange = onHapticFeedbackChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .clickable(onClick = onResetDefaults)
                .padding(12.dp)
        ) {
            Text(
                text = "Reset settings to default",
                color = violet,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F0D1A))
                .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recent remotes", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = "Up to 100 recently opened remotes are stored locally.",
                    color = Color(0xFF8A8899),
                    fontSize = 11.sp
                )

                if (historyEntries.isEmpty()) {
                    Text(
                        text = "No recent remotes yet.",
                        color = Color(0xFF8A8899),
                        fontSize = 12.sp
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = historyMaxHeight)
                            .verticalScroll(rememberScrollState())
                    ) {
                        historyEntries.forEach { entry ->
                            ListRow(
                                title = entry.name,
                                subtitle = historyDateFormatter.format(Instant.ofEpochMilli(entry.openedAtEpochMs)),
                                actionLabel = "Open",
                                onOpen = { onOpenHistoryItem(entry) },
                                onAction = { onOpenHistoryItem(entry) },
                                leadingIconName = entry.iconName
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Changes are saved automatically.",
            color = Color(0xFF8A8899),
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SpeedPresetButton(label: String, onClick: () -> Unit) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(violet.copy(alpha = 0.14f))
            .border(1.dp, violet.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = violet, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SettingToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val violet = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0F0D1A))
            .border(1.dp, violet.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color(0xFF8A8899), fontSize = 11.sp)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
