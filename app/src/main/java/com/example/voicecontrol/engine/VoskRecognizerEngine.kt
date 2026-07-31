package com.example.voicecontrol.engine

import android.content.Context
import android.util.Log
import com.example.voicecontrol.manager.SpeechRecognitionListener
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File

/**
 * RecognitionEngine implementation utilizing the Vosk Offline Speech Recognition SDK.
 * Configured with custom command grammar JSON for sub-100ms offline recognition accuracy.
 */
class VoskRecognizerEngine(private val context: Context) : RecognitionEngine {

    companion object {
        private const val TAG = "VoskEngine"
        private const val PERF_TAG = "PERF"

        // Constrained command grammar JSON array for sub-100ms offline recognition
        private const val COMMAND_GRAMMAR_JSON = """[
            "open whatsapp", "open chrome", "open instagram", "open youtube", "open settings", "open camera",
            "go home", "home", "back", "recent apps", "recents",
            "swipe up", "swipe down", "swipe left", "swipe right",
            "tap search", "tap settings", "tap profile", "tap install", "tap send",
            "show numbers", "hide numbers",
            "show grid", "hide grid", "reset grid", "click here",
            "1", "2", "3", "4", "5", "6", "7", "8", "9", "10",
            "[unk]"
        ]"""
    }

    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private var isListening: Boolean = false
    private var isModelLoading: Boolean = false

    init {
        loadVoskModel()
    }

    /**
     * Unpacks assets model and initializes Vosk Model.
     */
    private fun loadVoskModel() {
        if (voskModel != null || isModelLoading) return
        isModelLoading = true

        StorageService.unpack(context, "model-en-us", "model-en-us",
            { model ->
                voskModel = model
                isModelLoading = false
                Log.i(TAG, "Vosk offline language model loaded successfully.")
            },
            { exception ->
                isModelLoading = false
                Log.w(TAG, "Failed to load Vosk model from assets/model-en-us. Checking local cache: ${exception.message}")
                tryLoadLocalModel()
            }
        )
    }

    private fun tryLoadLocalModel() {
        try {
            val localModelDir = File(context.filesDir, "model-en-us")
            if (localModelDir.exists()) {
                voskModel = Model(localModelDir.absolutePath)
                Log.i(TAG, "Vosk model loaded from local storage path.")
            } else {
                Log.w(TAG, "No Vosk model files found at ${localModelDir.absolutePath}. Add 'model-en-us' to app/src/main/assets/")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing local Vosk model", e)
        }
    }

    override fun isAvailable(): Boolean {
        return voskModel != null
    }

    override fun startListening(listener: SpeechRecognitionListener) {
        if (voskModel == null) {
            loadVoskModel()
            listener.onError("Vosk offline model is loading. Please place 'model-en-us' in assets/ or retry.")
            return
        }

        try {
            stopListening()

            val tStart = System.currentTimeMillis()
            Log.i(PERF_TAG, "[PERF] [VOSK] Starting Vosk continuous audio stream listener: $tStart ms")

            val recognizer = Recognizer(voskModel, 16000.0f, COMMAND_GRAMMAR_JSON)
            val speechService = SpeechService(recognizer, 16000.0f)

            speechService.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) {
                    if (hypothesis.isNullOrBlank()) return
                    val partialText = parseHypothesisText(hypothesis, isPartial = true)
                    if (partialText.isNotBlank()) {
                        Log.i(PERF_TAG, "[PERF] [VOSK] Partial hypothesis: \"$partialText\"")
                        listener.onPartialResults(partialText)
                    }
                }

                override fun onResult(hypothesis: String?) {
                    if (hypothesis.isNullOrBlank()) return
                    val text = parseHypothesisText(hypothesis, isPartial = false)
                    if (text.isNotBlank()) {
                        Log.i(PERF_TAG, "[PERF] [VOSK] Final result hypothesis: \"$text\"")
                        listener.onResults(text)
                    }
                }

                override fun onFinalResult(hypothesis: String?) {
                    onResult(hypothesis)
                }

                override fun onError(exception: Exception?) {
                    isListening = false
                    Log.e(TAG, "Vosk Recognition Error", exception)
                    listener.onError(exception?.message ?: "Vosk recognition error")
                }

                override fun onTimeout() {
                    isListening = false
                    Log.w(TAG, "Vosk recognition timeout")
                    listener.onEndOfSpeech()
                }
            })

            voskSpeechService = speechService
            isListening = true
            listener.onReadyForSpeech()

        } catch (e: Exception) {
            Log.e(TAG, "Error starting Vosk SpeechService", e)
            listener.onError("Vosk engine error: ${e.message}")
        }
    }

    override fun stopListening() {
        try {
            voskSpeechService?.stop()
            voskSpeechService?.shutdown()
            voskSpeechService = null
            isListening = false
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping Vosk SpeechService", e)
        }
    }

    override fun cancel() {
        stopListening()
    }

    override fun destroy() {
        stopListening()
        voskModel?.close()
        voskModel = null
    }

    override fun getEngineName(): String = EngineType.VOSK_OFFLINE.displayName

    /**
     * Parses JSON hypothesis string returned by Vosk SDK.
     */
    private fun parseHypothesisText(jsonString: String, isPartial: Boolean): String {
        return try {
            val json = JSONObject(jsonString)
            val textKey = if (isPartial) "partial" else "text"
            json.optString(textKey, "").trim()
        } catch (e: Exception) {
            ""
        }
    }
}
