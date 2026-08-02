package com.example.voicecontrol.ui.taptap

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessibilityNew
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicecontrol.taptap.settings.TapTapSettings

/**
 * Complete TapTap UI Dashboard styled after KieronQuinn's TapTap application.
 * Manages Master Enable switch, Double/Triple Tap Actions, Filter Gates, Haptic Feedback, and Live Tap Testing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TapTapScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val settings = remember { TapTapSettings(context) }

    var isTapTapEnabled by remember { mutableStateOf(settings.isTapTapEnabled) }
    var doubleTapAction by remember { mutableStateOf(settings.doubleTapAction) }
    var tripleTapAction by remember { mutableStateOf(settings.tripleTapAction) }
    var isHapticEnabled by remember { mutableStateOf(settings.isHapticFeedbackEnabled) }
    var isScreenOffGate by remember { mutableStateOf(settings.isScreenOffGateEnabled) }
    var isCameraGate by remember { mutableStateOf(settings.isCameraGateEnabled) }
    var isCallGate by remember { mutableStateOf(settings.isCallGateEnabled) }

    var lastDetectedGesture by remember { mutableStateOf("Tap the back of your phone to test!") }

    val actionOptions = listOf(
        ActionOption(TapTapSettings.ACTION_TOGGLE_VOICE_CONTROL, "Toggle Voice Control", "Turns Voice Assistant ON/OFF", Icons.Default.Mic),
        ActionOption(TapTapSettings.ACTION_FLASHLIGHT, "Toggle Flashlight", "Turns Camera Torch ON/OFF", Icons.Default.Warning),
        ActionOption(TapTapSettings.ACTION_SCREENSHOT, "Take Screenshot", "Captures system screenshot", Icons.Default.AccessibilityNew),
        ActionOption(TapTapSettings.ACTION_QUICK_SETTINGS, "Open Quick Settings", "Expands notification shade", Icons.Default.BugReport),
        ActionOption(TapTapSettings.ACTION_MEDIA_PLAY_PAUSE, "Media Play/Pause", "Controls music/video playback", Icons.Default.Mic),
        ActionOption(TapTapSettings.ACTION_LOCK_SCREEN, "Lock Screen", "Locks device display", Icons.Default.Warning)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "TapTap Gestures",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            text = "Google Pixel & Samsung Back Tap Engine",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // --- MASTER SWITCH CARD ---
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isTapTapEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isTapTapEnabled) MaterialTheme.colorScheme.primary else Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessibilityNew,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isTapTapEnabled) "TapTap Active" else "TapTap Disabled",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Detect double & triple taps on phone back",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Switch(
                        checked = isTapTapEnabled,
                        onCheckedChange = { checked ->
                            isTapTapEnabled = checked
                            settings.isTapTapEnabled = checked
                            Toast.makeText(context, if (checked) "TapTap Enabled" else "TapTap Disabled", Toast.LENGTH_SHORT).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            AnimatedVisibility(visible = isTapTapEnabled) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // --- SECTION 1: GESTURES (DOUBLE & TRIPLE TAP) ---
                    Text(
                        text = "GESTURE ACTIONS",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )

                    ActionSelectorCard(
                        title = "Double Tap Action",
                        subtitle = "Triggered when back is tapped 2 times",
                        icon = Icons.Default.AccessibilityNew,
                        selectedActionId = doubleTapAction,
                        options = actionOptions,
                        onActionSelected = { id ->
                            doubleTapAction = id
                            settings.doubleTapAction = id
                        }
                    )

                    ActionSelectorCard(
                        title = "Triple Tap Action",
                        subtitle = "Triggered when back is tapped 3 times",
                        icon = Icons.Default.BugReport,
                        selectedActionId = tripleTapAction,
                        options = actionOptions,
                        onActionSelected = { id ->
                            tripleTapAction = id
                            settings.tripleTapAction = id
                        }
                    )

                    // --- SECTION 2: GATES (FILTERS) ---
                    Text(
                        text = "GATES (CONDITION FILTERS)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                            GateToggleRow(
                                title = "Screen Off Gate",
                                subtitle = "Block gesture detection when screen is turned off",
                                icon = Icons.Default.Warning,
                                checked = isScreenOffGate,
                                onCheckedChange = { checked ->
                                    isScreenOffGate = checked
                                    settings.isScreenOffGateEnabled = checked
                                }
                            )

                            GateToggleRow(
                                title = "Camera Active Gate",
                                subtitle = "Block gesture detection when camera app is open",
                                icon = Icons.Default.BugReport,
                                checked = isCameraGate,
                                onCheckedChange = { checked ->
                                    isCameraGate = checked
                                    settings.isCameraGateEnabled = checked
                                }
                            )

                            GateToggleRow(
                                title = "Phone Call Gate",
                                subtitle = "Block gesture detection during active calls",
                                icon = Icons.Default.Mic,
                                checked = isCallGate,
                                onCheckedChange = { checked ->
                                    isCallGate = checked
                                    settings.isCallGateEnabled = checked
                                }
                            )
                        }
                    }

                    // --- SECTION 3: FEEDBACK & TUNING ---
                    Text(
                        text = "FEEDBACK & TUNING",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp, top = 8.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AccessibilityNew,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(text = "Haptic Vibration Pulse", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(text = "Vibrate on tap detection", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Switch(
                                checked = isHapticEnabled,
                                onCheckedChange = { checked ->
                                    isHapticEnabled = checked
                                    settings.isHapticFeedbackEnabled = checked
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

data class ActionOption(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector
)

@Composable
fun ActionSelectorCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selectedActionId: String,
    options: List<ActionOption>,
    onActionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedOption = options.find { it.id == selectedActionId } ?: options.first()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background)
                    .padding(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(selectedOption.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(text = selectedOption.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(text = selectedOption.description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(option.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(option.title, fontWeight = FontWeight.Medium)
                                        Text(option.description, fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            },
                            onClick = {
                                onActionSelected(option.id)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun GateToggleRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(text = subtitle, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
