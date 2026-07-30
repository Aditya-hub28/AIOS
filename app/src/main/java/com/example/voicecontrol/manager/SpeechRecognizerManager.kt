package com.example.voicecontrol.manager

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.util.Locale

/**
 * Listener callback interface for receiving SpeechRecognizer events.
 */
interface SpeechRecognitionListener {
    fun onReadyForSpeech()
    fun onBeginningOfSpeech()
    fun onRmsChanged(rmsdB: Float)
    fun onPartialResults(partialText: String)
    fun onResults(recognizedText: String)
    fun onError(errorMessage: String)
    fun onEndOfSpeech()
}

/**
 * SpeechRecognizerManager encapsulates the Android SpeechRecognizer API.
 * Handles speech recognition lifecycle, intent setup, events, and error mapping.
 */
class SpeechRecognizerManager(private val context: Context) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    /**
     * Checks whether speech recognition service is available on the device.
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    /**
     * Initializes and starts listening for speech input.
     * @param listener Callbacks for speech recognition events.
     * @param locale Desired locale for recognition (defaults to system default).
     */
    fun startListening(
        listener: SpeechRecognitionListener,
        locale: Locale = Locale.getDefault()
    ) {
        if (!isAvailable()) {
            listener.onError("Speech recognition is not available on this device.")
            return
        }

        // Clean up previous instance if active
        destroy()

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListening = true
                    listener.onReadyForSpeech()
                }

                override fun onBeginningOfSpeech() {
                    listener.onBeginningOfSpeech()
                }

                override fun onRmsChanged(rmsdB: Float) {
                    listener.onRmsChanged(rmsdB)
                }

                override fun onBufferReceived(buffer: ByteArray?) {
                    // Raw audio buffer received if needed
                }

                override fun onEndOfSpeech() {
                    isListening = false
                    listener.onEndOfSpeech()
                }

                override fun onError(error: Int) {
                    isListening = false
                    val message = getErrorMessage(error)
                    listener.onError(message)
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val recognizedText = matches?.firstOrNull() ?: ""
                    if (recognizedText.isNotBlank()) {
                        listener.onResults(recognizedText)
                    } else {
                        listener.onError("No speech was recognized. Please try speaking again.")
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val partialText = matches?.firstOrNull() ?: ""
                    if (partialText.isNotBlank()) {
                        listener.onPartialResults(partialText)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {
                    // Custom engine events
                }
            })
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toString())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

        speechRecognizer?.startListening(intent)
    }

    /**
     * Stops receiving speech input and finishes processing recorded audio.
     */
    fun stopListening() {
        if (isListening) {
            speechRecognizer?.stopListening()
            isListening = false
        }
    }

    /**
     * Cancels active speech recognition session immediately.
     */
    fun cancel() {
        speechRecognizer?.cancel()
        isListening = false
    }

    /**
     * Destroys the SpeechRecognizer instance and releases native resources.
     */
    fun destroy() {
        speechRecognizer?.let {
            it.cancel()
            it.destroy()
        }
        speechRecognizer = null
        isListening = false
    }

    /**
     * Maps SpeechRecognizer error codes to user-friendly messages.
     */
    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Please check your microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Client-side recognition error occurred."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required for speech recognition."
            SpeechRecognizer.ERROR_NETWORK -> "Network error. Please check your internet connection."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timed out. Please try again."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech recognized. Please speak clearly into the microphone."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition engine is busy. Please try again in a moment."
            SpeechRecognizer.ERROR_SERVER -> "Speech server error. Please try again later."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected. Please press the microphone and speak."
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Selected language is not supported."
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "Selected language service is currently unavailable."
            else -> "Speech recognition failed (Code $errorCode). Please try again."
        }
    }
}
