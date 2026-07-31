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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
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
 * In-App Back Tap Debug Dashboard (Jetpack Compose).
 * Displays live sensor telemetry, calculated metrics, tap counter, performance (Hz, Latency),
 * active state badge, motion classification badge, floating overlay switch, and 200-event log feed.
 */
@Composable
fun BackTapDebugScreen(
    modifier: Modifier = Modifier,
    onClose: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val telemetry by BackTapDebugManager.telemetry.collectAsState()
    val eventLogs by BackTapDebugManager.eventLogs.collectAsState()
    val isFloatingOverlayActive by BackTapDebugManager.isFloatingOverlayActive.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(14.dp)
    ) {
        // --- HEADER & CONTROL CARD ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.BugReport,
                            contentDescription = "Debug Dashboard",
                            tint = Color(0xFF00E676)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Back Tap Debug Dashboard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    if (onClose != null) {
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
                    // State Badge: IDLE, POSSIBLE_TAP, VALID_TAP, TRIPLE_TAP
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

                    // Motion Badge: STILL, MOVING, SHAKING, BACK_TAP_LIKE
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

                // FLOATING OVERLAY TOGGLE & CLEAR LOGS ACTION
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

        Spacer(modifier = Modifier.height(10.dp))

        // --- TAP COUNTER & PERFORMANCE PANEL ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricCell("Tap Count", "${telemetry.tapCount} / 3", isHighlight = true)
                MetricCell("Time Since Tap", "${telemetry.timeSinceLastTapMs} ms")
                MetricCell("Sampling Rate", "${telemetry.sensorEventsPerSec} Hz")
                MetricCell("Latency", "${telemetry.detectionLatencyMs} ms")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- SENSOR METRICS & THRESHOLDS PANEL ---
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "SENSORS & CALCULATED METRICS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E676)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricCell("Accel X", "%.2f".format(telemetry.accelX))
                    MetricCell("Accel Y", "%.2f".format(telemetry.accelY))
                    MetricCell("Accel Z", "%.2f".format(telemetry.accelZ))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricCell("LinAccel X", "%.2f".format(telemetry.linX))
                    MetricCell("LinAccel Y", "%.2f".format(telemetry.linY))
                    MetricCell("LinAccel Z", "%.2f".format(telemetry.linZ))
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricCell("Gyro X", "%.2f".format(telemetry.gyroX))
                    MetricCell("Gyro Y", "%.2f".format(telemetry.gyroY))
                    MetricCell("Gyro Z", "%.2f".format(telemetry.gyroZ))
                }

                Divider(modifier = Modifier.padding(vertical = 6.dp), color = Color.DarkGray)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MetricCell("Peak", "%.2f".format(telemetry.peak), isHighlight = true)
                    MetricCell("Z Peak", "%.2f".format(telemetry.zPeak), isHighlight = true)
                    MetricCell("Jerk", "%.2f".format(telemetry.jerk), isHighlight = true)
                    MetricCell("Gyro Mag", "%.2f".format(telemetry.gyroMag), isHighlight = true)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Active Thresholds: Imp: %.2f..%.2f | Jerk: >=%.2f | Gyro: <%.2f".format(
                        telemetry.minImpulse, telemetry.maxImpulse, telemetry.minJerk, telemetry.maxGyro
                    ),
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // --- LIVE EVENT LOG FEED (LAST 200 EVENTS) ---
        Text(
            text = "LIVE EVENT FEED (${eventLogs.size} / 200)",
            fontSize = 12.sp,
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
                        text = "No sensor events recorded yet.\nPerform actions on device to see live logs.",
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
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isHighlight) Color(0xFFFFD600) else Color.White
        )
    }
}

@Composable
private fun EventLogRow(logText: String) {
    val isTriple = logText.contains("TRIPLE_TAP")
    val isValid = logText.contains("VALID_TAP")
    val isNoise = logText.contains("NOISE") || logText.contains("INVALID_TAP")

    val bgColor = when {
        isTriple -> Color(0xFF1B5E20)
        isValid -> Color(0xFF33691E)
        isNoise -> Color(0xFF3E2723)
        else -> Color(0xFF212121)
    }

    val textColor = when {
        isTriple -> Color(0xFFB9F6CA)
        isValid -> Color(0xFFCCFF90)
        isNoise -> Color(0xFFFF8A80)
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
            fontWeight = if (isTriple || isValid) FontWeight.Bold else FontWeight.Normal
        )
    }
}
