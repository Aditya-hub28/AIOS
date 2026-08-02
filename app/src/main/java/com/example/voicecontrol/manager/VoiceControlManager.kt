package com.example.voicecontrol.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.voicecontrol.service.VoiceControlService
import com.example.voicecontrol.util.VibrationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton controller managing Voice Control state, service lifecycle, vibration haptics, and Toast user feedback.
 */
object VoiceControlManager {

    private const val TAG = "VoiceControlManager"

    private val _isVoiceControlActive = MutableStateFlow(false)
    val isVoiceControlActive: StateFlow<Boolean> = _isVoiceControlActive.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes TapTapEngine and voice control system.
     */
    fun init(context: Context) {
        com.example.voicecontrol.taptap.TapTapEngine.init(context)
        Log.i(TAG, "VoiceControlManager initialized.")
    }

    /**
     * Toggles Voice Control state ON or OFF with vibration pulse and Toast notification feedback.
     */
    fun toggleVoiceControl(context: Context) {
        mainHandler.post {
            val currentState = _isVoiceControlActive.value
            val newState = !currentState
            _isVoiceControlActive.value = newState

            // Short vibration pulse feedback
            VibrationUtils.vibrate(context, 100L)

            if (newState) {
                // Start Voice Control
                VoiceControlService.start(context)
                Toast.makeText(context, "Voice Control Activated", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Voice Control ACTIVATED")
            } else {
                // Stop Voice Control
                VoiceControlService.stop(context)
                Toast.makeText(context, "Voice Control Deactivated", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Voice Control DEACTIVATED")
            }
        }
    }

    /**
     * Updates active state directly from UI or ViewModel.
     */
    fun setVoiceControlActive(active: Boolean) {
        _isVoiceControlActive.value = active
    }
}
