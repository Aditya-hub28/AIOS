package com.example.voicecontrol.engine

import com.example.voicecontrol.manager.SpeechRecognitionListener

/**
 * Enum defining available speech recognition engines.
 */
enum class EngineType(val displayName: String) {
    NATIVE_ANDROID("Native Android Engine"),
    VOSK_OFFLINE("Vosk Offline Engine (Experimental)")
}

/**
 * Unified abstraction interface for speech recognition engines.
 * Enables seamless runtime switching between Native Android SpeechRecognizer and Vosk Offline Engine.
 */
interface RecognitionEngine {
    /**
     * Checks if this speech recognition engine is available on the current device.
     */
    fun isAvailable(): Boolean

    /**
     * Starts continuous speech recognition.
     * @param listener Callbacks for speech recognition lifecycle events.
     */
    fun startListening(listener: SpeechRecognitionListener)

    /**
     * Stops listening and finishes processing recorded audio.
     */
    fun stopListening()

    /**
     * Cancels active recognition session immediately.
     */
    fun cancel()

    /**
     * Destroys engine instance and releases native memory resources.
     */
    fun destroy()

    /**
     * Returns human-readable engine name for logging and UI display.
     */
    fun getEngineName(): String
}
