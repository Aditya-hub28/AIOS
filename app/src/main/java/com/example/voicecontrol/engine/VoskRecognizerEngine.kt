package com.example.voicecontrol.engine

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.voicecontrol.manager.SpeechRecognitionListener
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.File

/**
 * Enhanced Vosk Offline Speech Recognition Engine with detailed Logcat diagnostics,
 * multi-folder asset fallback probing ("model", "model-en-us"), and runtime status checks.
 */
class VoskRecognizerEngine(private val context: Context) : RecognitionEngine {

    companion object {
        private const val TAG = "VoskEngine"
        private const val PERF_TAG = "PERF"

        // Common asset folder names where developers place Vosk speech models
        private val MODEL_ASSET_NAMES = listOf("model", "model-en-us", "vosk-model-small-en-us-0.15")

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
    private var modelLoadErrorMessage: String? = null

    init {
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "🚀 Initializing Vosk Offline Speech Recognition Engine...")
        verifyMicrophonePermission()
        loadVoskModel()
    }

    /**
     * Checks whether RECORD_AUDIO permission is granted.
     */
    private fun verifyMicrophonePermission(): Boolean {
        val permissionState = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
        val isGranted = permissionState == PackageManager.PERMISSION_GRANTED
        if (isGranted) {
            Log.i(TAG, "✅ Microphone Permission (RECORD_AUDIO): GRANTED")
        } else {
            Log.w(TAG, "❌ Microphone Permission (RECORD_AUDIO): DENIED. Audio recording will fail.")
        }
        return isGranted
    }

    /**
     * Inspects assets directory and unpacks Vosk speech model.
     */
    private fun loadVoskModel() {
        if (voskModel != null || isModelLoading) return
        isModelLoading = true
        modelLoadErrorMessage = null

        Log.i(TAG, "🔍 Probing assets/ directory for Vosk speech model...")
        try {
            val assetList = context.assets.list("") ?: emptyArray()
            Log.i(TAG, "📂 Assets Root Contents: ${assetList.joinToString(", ", "[", "]")}")

            // Find matching asset folder name
            val detectedFolder = MODEL_ASSET_NAMES.firstOrNull { folderName ->
                try {
                    val subAssets = context.assets.list(folderName) ?: emptyArray()
                    subAssets.isNotEmpty()
                } catch (e: Exception) {
                    false
                }
            }

            if (detectedFolder != null) {
                Log.i(TAG, "📦 Found Vosk model folder in assets: 'assets/$detectedFolder'")
                unpackModelFromAssets(detectedFolder)
            } else {
                Log.w(TAG, "⚠️ No Vosk model folder ('model' or 'model-en-us') found in assets/!")
                Log.w(TAG, "🔍 Checking local app storage fallback path: ${context.filesDir.absolutePath}")
                tryLoadLocalModelFallback()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception during assets directory inspection", e)
            tryLoadLocalModelFallback()
        }
    }

    /**
     * Unpacks assets model to app internal filesDir using Vosk StorageService.
     */
    private fun unpackModelFromAssets(assetFolderName: String) {
        Log.i(TAG, "⏳ Unpacking 'assets/$assetFolderName' to '${context.filesDir.absolutePath}/$assetFolderName'...")

        StorageService.unpack(
            context,
            assetFolderName,
            assetFolderName,
            { model ->
                voskModel = model
                isModelLoading = false
                modelLoadErrorMessage = null
                Log.i(TAG, "==========================================================")
                Log.i(TAG, "✅ Vosk Model Successfully Initialized & Ready!")
                Log.i(TAG, "==========================================================")
            },
            { exception ->
                isModelLoading = false
                modelLoadErrorMessage = exception.message ?: "Failed to unpack model from assets"
                Log.e(TAG, "❌ StorageService.unpack() failed for 'assets/$assetFolderName': ${exception.message}", exception)
                tryLoadLocalModelFallback()
            }
        )
    }

    /**
     * Tries to load model directly from local internal storage directory if already unpacked.
     */
    private fun tryLoadLocalModelFallback() {
        for (folderName in MODEL_ASSET_NAMES) {
            val localModelDir = File(context.filesDir, folderName)
            Log.i(TAG, "🔎 Testing local path: '${localModelDir.absolutePath}' (Exists: ${localModelDir.exists()}, IsDir: ${localModelDir.isDirectory})")

            if (localModelDir.exists() && localModelDir.isDirectory) {
                try {
                    Log.i(TAG, "⚙️ Attempting Model('${localModelDir.absolutePath}')...")
                    voskModel = Model(localModelDir.absolutePath)
                    isModelLoading = false
                    modelLoadErrorMessage = null
                    Log.i(TAG, "==========================================================")
                    Log.i(TAG, "✅ Vosk Model loaded successfully from local storage!")
                    Log.i(TAG, "==========================================================")
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to instantiate Model from '${localModelDir.absolutePath}'", e)
                }
            }
        }

        isModelLoading = false
        val missingMsg = "No Vosk speech model found! Please place your model files inside 'app/src/main/assets/model/'"
        modelLoadErrorMessage = missingMsg
        Log.e(TAG, "==========================================================")
        Log.e(TAG, "❌ VOSK ENGINE SETUP ERROR: $missingMsg")
        Log.e(TAG, "==========================================================")
    }

    override fun isAvailable(): Boolean {
        return voskModel != null
    }

    override fun startListening(listener: SpeechRecognitionListener) {
        if (!verifyMicrophonePermission()) {
            listener.onError("Microphone permission is required for Vosk engine.")
            return
        }

        if (voskModel == null) {
            if (isModelLoading) {
                listener.onError("Vosk offline model is loading... Please wait a moment.")
            } else {
                val errMsg = modelLoadErrorMessage ?: "Vosk model not found. Place model in 'app/src/main/assets/model/'"
                listener.onError(errMsg)
            }
            loadVoskModel()
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
                    Log.e(TAG, "❌ Vosk Recognition Error", exception)
                    listener.onError(exception?.message ?: "Vosk recognition error")
                }

                override fun onTimeout() {
                    isListening = false
                    Log.w(TAG, "⚠️ Vosk recognition timeout")
                    listener.onEndOfSpeech()
                }
            })

            voskSpeechService = speechService
            isListening = true
            listener.onReadyForSpeech()
            Log.i(TAG, "🎙️ Vosk SpeechService active and listening.")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Exception starting Vosk SpeechService", e)
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
