package com.example.voicecontrol.engine

import android.content.Context
import com.example.voicecontrol.manager.SpeechRecognitionListener
import com.example.voicecontrol.manager.SpeechRecognizerManager

/**
 * RecognitionEngine implementation wrapping native Android SpeechRecognizer.
 * 100% preserves existing SpeechRecognizerManager implementation.
 */
class AndroidSpeechRecognizerEngine(context: Context) : RecognitionEngine {

    private val speechManager = SpeechRecognizerManager(context)

    override fun isAvailable(): Boolean {
        return speechManager.isAvailable()
    }

    override fun startListening(listener: SpeechRecognitionListener) {
        speechManager.startListening(listener)
    }

    override fun stopListening() {
        speechManager.stopListening()
    }

    override fun cancel() {
        speechManager.cancel()
    }

    override fun destroy() {
        speechManager.destroy()
    }

    override fun getEngineName(): String = EngineType.NATIVE_ANDROID.displayName
}
