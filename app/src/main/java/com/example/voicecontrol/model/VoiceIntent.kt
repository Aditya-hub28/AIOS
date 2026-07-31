package com.example.voicecontrol.model

/**
 * Sealed hierarchy representing a parsed voice command intent.
 */
sealed interface VoiceIntent {
    /** Open an installed application */
    data class OpenApp(val appName: String, val packageName: String) : VoiceIntent

    /** Tap a UI element identified by visible text */
    data class TapText(val targetText: String) : VoiceIntent

    /** Type arbitrary text into the currently focused input field */
    data class TypeText(val text: String) : VoiceIntent

    /** Swipe gestures */
    object SwipeUp : VoiceIntent
    object SwipeDown : VoiceIntent
    object SwipeLeft : VoiceIntent
    object SwipeRight : VoiceIntent

    /** System global actions */
    object Home : VoiceIntent
    object Back : VoiceIntent
    object Recents : VoiceIntent

    /** Grid overlay controls */
    object ShowGrid : VoiceIntent
    object HideGrid : VoiceIntent
    object ResetGrid : VoiceIntent
    object ClickHere : VoiceIntent

    /** Unrecognised command */
    data class Unknown(val rawText: String) : VoiceIntent
}
