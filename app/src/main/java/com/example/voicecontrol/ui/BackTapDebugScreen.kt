package com.example.voicecontrol.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicecontrol.manager.BackTapDebugManager

/**
 * Production-Grade In-App Developer Debug Screen & Telemetry HUD Dashboard.
 * Displays real-time sensor updates (10-20 FPS), 3-axis peak analysis (X, Y, Z), Z-dominance ratio,
 * screen-touch suppression status, rolling timestamps, sequence state, and live 200-event feed.
 */
@Composable
fun BackTapDebugScreen(
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val telemetry by BackTapDebugManager.telemetry.collectAsState()
    val eventLogs by BackTapDebugManager.eventLogs.collectAsState()
    val isFloatingOverlayActive by BackTapDebugManager.isFloatingOverlayActive.collectAsState()

    val confidencePct = (telemetry.minImpulse * 100f).coerceIn(0f, 100f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(12.dp)
    ) {
        // --- TOP TOOLBAR HEADER ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Icon",
                            tint = Color(0xFF00E676),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Columbus ML Back Tap Engine",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Row {
                        OutlinedButton(onClick = onClose) {
                            Text("Close", color = Color.White)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // STATE BADGE & MOTION CLASSIFICATION BADGE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = when (telemetry.stateName) {
                                    "TRIPLE_TAP" -> Color(0xFF00E676)
                                    "VALID_TAP" -> Color(0xFFFFD600)
                                    "POSSIBLE_TAP" -> Color(0xFFFF9100)
                                    else -> Color(0xFF424242)
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "STATE: ${telemetry.stateName}",
                            color = if (telemetry.stateName == "IDLE") Color.White else Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = when (telemetry.motionName) {
                                    "BACK_TAP_LIKE" -> Color(0xFF00E676)
                                    "STILL" -> Color(0xFF29B6F6)
                                    "SHAKING" -> Color(0xFFFF5252)
                                    else -> Color(0xFF757575)
                                },
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "MOTION: ${telemetry.motionName}",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // FLOATING OVERLAY TOGGLE & CLEAR LOGS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Floating Overlay",
                            tint = Color.LightGray
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Floating Overlay HUD", color = Color.White, fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isFloatingOverlayActive,
                            onCheckedChange = { BackTapDebugManager.toggleFloatingOverlay(context) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF00E676))
                        )
                    }

                    Button(
                        onClick = { BackTapDebugManager.clearLogs() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear Logs",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Logs", fontSize = 11.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- SCREEN TOUCH SUPPRESSION CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = "Touch Suppression",
                            tint = if (telemetry.isSuppressionActive) Color(0xFFFF5252) else Color(0xFF00E676),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("SCREEN TOUCH SUPPRESSION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    Box(
                        modifier = Modifier
                            .background(
                                color = if (telemetry.isSuppressionActive) Color(0xFFFF5252) else Color(0xFF1B5E20),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (telemetry.isSuppressionActive) "SUPPRESSION ACTIVE (250ms)" else "READY",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetricCell("Touch TS", if (telemetry.touchTimestampMs > 0) "${telemetry.touchTimestampMs} ms" else "None")
                    MetricCell("Impulse TS", if (telemetry.impulseTimestampMs > 0) "${telemetry.impulseTimestampMs} ms" else "None")
                    MetricCell("Z Dominance Ratio", "${"%.0f".format(telemetry.zDominanceRatio * 100f)}% (Req >= 80%)", isHighlight = telemetry.zDominanceRatio >= 0.80f)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- IMPULSE DIRECTION & AXIS BREAKDOWN CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell("X Peak", "%.2f".format(telemetry.xPeak))
                MetricCell("Y Peak", "%.2f".format(telemetry.yPeak))
                MetricCell("Z Peak", "%.2f".format(telemetry.zPeak), isHighlight = true)
                MetricCell("Jerk Peak", "%.2f".format(telemetry.jerk))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- ML GESTURE CONFIDENCE CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ML GESTURE CONFIDENCE SCORE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00E676)
                    )
                    Text(
                        text = "${"%.1f".format(confidencePct)}%  (Threshold >= 79%)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (confidencePct >= 79f) Color(0xFF00E676) else Color(0xFFFFD600)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { (confidencePct / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                    color = if (confidencePct >= 79f) Color(0xFF00E676) else if (confidencePct >= 50f) Color(0xFFFFD600) else Color(0xFFFF5252),
                    trackColor = Color.DarkGray
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- TAP COUNTER & PERFORMANCE PANEL ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell("Sequence", telemetry.sequenceText, isHighlight = true)
                MetricCell("Time Since Tap", "${telemetry.timeSinceLastTapMs} ms")
                MetricCell("Sensor Rate", "${telemetry.sensorEventsPerSec} Hz")
                MetricCell("TFLite Latency", "${telemetry.detectionLatencyMs} ms")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // --- LIVE EVENT FEED (LAST 200 EVENTS) ---
        Text(
            text = "LIVE EVENT FEED (${eventLogs.size} / 200)",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.LightGray
        )

        Spacer(modifier = Modifier.height(4.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF181818)),
            shape = RoundedCornerShape(12.dp)
        ) {
            if (eventLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No sensor events recorded yet.\nTap phone back to view ML inferences.",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                ) {
                    items(eventLogs) { logItem ->
                        EventLogRow(logText = logItem)
                        Spacer(modifier = Modifier.height(3.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCell(label: String, value: String, isHighlight: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 9.sp, color = Color.Gray)
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) Color(0xFFFFD600) else Color.White
        )
    }
}

@Composable
private fun EventLogRow(logText: String) {
    val isTriple = logText.contains("TRIPLE_TAP")
    val isAccepted = logText.contains("BACK_TAP_ACCEPTED") || logText.contains("VALID_TAP") || logText.contains("SEQUENCE_STARTED")
    val isSuppressed = logText.contains("SCREEN_TAP_SUPPRESSED")
    val isRejected = logText.contains("BACK_TAP_REJECTED") || logText.contains("REJECTED") || logText.contains("SEQUENCE_RESET")

    val bgColor = when {
        isTriple -> Color(0xFF1B5E20)
        isAccepted -> Color(0xFF2E7D32)
        isSuppressed -> Color(0xFFE65100)
        isRejected -> Color(0xFFB71C1C)
        else -> Color(0xFF212121)
    }

    val textColor = when {
        isTriple -> Color(0xFFB9F6CA)
        isAccepted -> Color(0xFFCCFF90)
        isSuppressed -> Color(0xFFFFE0B2)
        isRejected -> Color(0xFFFF8A80)
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = logText,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isTriple || isAccepted || isSuppressed) FontWeight.Bold else FontWeight.Normal
        )
    }
}
