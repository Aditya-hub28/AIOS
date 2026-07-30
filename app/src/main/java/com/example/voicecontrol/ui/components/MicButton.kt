package com.example.voicecontrol.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.voicecontrol.state.VoiceUiState
import com.example.voicecontrol.ui.theme.ListeningGreen
import com.example.voicecontrol.ui.theme.ListeningPulseBg
import com.example.voicecontrol.ui.theme.ProcessingAmber

/**
 * Large center microphone button with dynamic Material 3 visual feedback & audio pulsing.
 */
@Composable
fun MicButton(
    uiState: VoiceUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isListening = uiState is VoiceUiState.Listening
    val isProcessing = uiState is VoiceUiState.Processing

    // Infinite breathing pulse effect while listening
    val infiniteTransition = rememberInfiniteTransition(label = "MicPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "PulseScale"
    )

    val rmsdB = if (uiState is VoiceUiState.Listening) uiState.rmsdB else 0f
    // Dynamic audio level scale based on real-time sound decibels
    val dynamicAudioScale = (1.0f + (rmsdB.coerceIn(0f, 12f) / 24f)).coerceIn(1.0f, 1.4f)

    val containerColor = when (uiState) {
        is VoiceUiState.Listening -> ListeningGreen
        is VoiceUiState.Processing -> ProcessingAmber
        is VoiceUiState.Error -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when (uiState) {
        is VoiceUiState.Listening -> Icons.Default.Stop
        is VoiceUiState.Processing -> Icons.Default.MicOff
        else -> Icons.Default.Mic
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(200.dp)
    ) {
        // Outer pulsing ring during active speech listening
        if (isListening) {
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .scale(pulseScale * dynamicAudioScale)
                    .background(color = ListeningPulseBg, shape = CircleShape)
            )
        }

        // Main center action button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(120.dp)
                .background(color = containerColor, shape = CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = true, radius = 60.dp),
                    onClick = onClick
                )
                .padding(24.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = if (isListening) "Stop Listening" else "Start Listening",
                tint = Color.White,
                modifier = Modifier.size(54.dp)
            )
        }
    }
}
