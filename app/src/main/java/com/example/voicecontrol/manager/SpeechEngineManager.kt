package com.example.voicecontrol.manager

import android.content.Context
import android.util.Log
import com.example.voicecontrol.engine.AndroidSpeechRecognizerEngine
import com.example.voicecontrol.engine.EngineType
import com.example.voicecontrol.engine.RecognitionEngine
import com.example.voicecontrol.engine.VoskRecognizerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manager handling speech recognition engine instantiation, switching, and lifecycle management.
 */
class SpeechEngineManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechEngineManager"
    }

    private val androidEngine = AndroidSpeechRecognizerEngine(context)
    private val voskEngine = VoskRecognizerEngine(context)

    private val _selectedEngineType = MutableStateFlow(EngineType.NATIVE_ANDROID)
    val selectedEngineType: StateFlow<EngineType> = _selectedEngineType.asStateFlow()

    private var activeEngine: RecognitionEngine = androidEngine

    /**
     * Switches the active speech recognition engine dynamically at runtime.
     */
    fun selectEngine(engineType: EngineType) {
        if (_selectedEngineType.value == engineType) return

        Log.i(TAG, "Switching speech recognition engine: ${_selectedEngineType.value} -> $engineType")
        activeEngine.cancel()

        activeEngine = when (engineType) {
            EngineType.NATIVE_ANDROID -> androidEngine
            EngineType.VOSK_OFFLINE -> voskEngine
        }

        _selectedEngineType.value = engineType
    }

    fun isAvailable(): Boolean {
        return activeEngine.isAvailable()
    }

    fun startListening(listener: SpeechRecognitionListener) {
        activeEngine.startListening(listener)
    }

    fun stopListening() {
        activeEngine.stopListening()
    }

    fun cancel() {
        activeEngine.cancel()
    }

    fun destroy() {
        androidEngine.destroy()
        voskEngine.destroy()
    }

    fun getActiveEngineName(): String = activeEngine.getEngineName()
}
