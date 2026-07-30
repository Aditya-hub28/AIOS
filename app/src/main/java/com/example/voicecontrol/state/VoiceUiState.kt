package com.example.voicecontrol.state

/**
 * Represents the distinct UI states of the VoiceControl application.
 * Follows Unidirectional Data Flow (UDF) best practices.
 */
sealed interface VoiceUiState {
    
    /**
     * Initial state when the system is waiting for user action.
     */
    data object Idle : VoiceUiState

    /**
     * State when Voice Control is toggled OFF via double Volume Up press.
     */
    data object Disabled : VoiceUiState

    /**
     * State while the SpeechRecognizer is actively listening for audio input.
     * @param rmsdB Sound level in decibels for real-time visual feedback (e.g. mic pulsing animation).
     * @param partialText Real-time preliminary recognized text before final speech result.
     */
    data class Listening(
        val rmsdB: Float = 0f,
        val partialText: String = ""
    ) : VoiceUiState

    /**
     * State while speech input has ended and the engine is processing speech-to-text.
     */
    data object Processing : VoiceUiState

    /**
     * State when an app launch command is identified and being executed.
     * @param appName Name of the application being launched.
     */
    data class LaunchingApp(
        val appName: String
    ) : VoiceUiState

    /**
     * State when speech has been successfully transcribed to text.
     * @param recognizedText The final recognized speech output string.
     */
    data class Success(
        val recognizedText: String
    ) : VoiceUiState

    /**
     * State when an error occurs during permission request or speech recognition.
     * @param message Human-readable error explanation.
     * @param isPermissionError True if the error is due to missing microphone permission.
     */
    data class Error(
        val message: String,
        val isPermissionError: Boolean = false
    ) : VoiceUiState
}
