package com.example.voicecontrol.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.voicecontrol.state.VoiceUiState
import com.example.voicecontrol.ui.theme.ErrorRed
import com.example.voicecontrol.ui.theme.IdleGray
import com.example.voicecontrol.ui.theme.ListeningGreen
import com.example.voicecontrol.ui.theme.ProcessingAmber

/**
 * Status indicator badge component displaying current application state:
 * - Idle
 * - Listening
 * - Processing
 * - Error
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun StatusIndicator(
    uiState: VoiceUiState,
    modifier: Modifier = Modifier
) {
    val (statusLabel, statusColor, isProcessing) = when (uiState) {
        is VoiceUiState.Idle -> Triple("Idle", IdleGray, false)
        is VoiceUiState.Disabled -> Triple("Voice Control Disabled", ErrorRed, false)
        is VoiceUiState.Listening -> Triple("Listening...", ListeningGreen, false)
        is VoiceUiState.Processing -> Triple("Processing...", ProcessingAmber, true)
        is VoiceUiState.LaunchingApp -> Triple("Opening ${uiState.appName}...", ListeningGreen, true)
        is VoiceUiState.Success -> Triple("Listening Complete", ListeningGreen, false)
        is VoiceUiState.Error -> Triple("Error", ErrorRed, false)
    }

    Surface(
        color = statusColor.copy(alpha = 0.12f),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = statusLabel to statusColor,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "StatusBadgeTransition"
        ) { (label, color) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        color = color,
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(12.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(10.dp)
                            .background(color = color, shape = CircleShape)
                    )
                }
                Text(
                    text = label,
                    color = color,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
