package com.example.voicecontrol.manager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.example.voicecontrol.sensor.BackTapDetector
import com.example.voicecontrol.service.VoiceControlService
import com.example.voicecontrol.util.VibrationUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton controller managing Voice Control state, background Triple Back Tap detection,
 * service lifecycle, vibration haptics, and Toast user feedback.
 */
object VoiceControlManager {

    private const val TAG = "VoiceControlManager"

    private val _isVoiceControlActive = MutableStateFlow(false)
    val isVoiceControlActive: StateFlow<Boolean> = _isVoiceControlActive.asStateFlow()

    private var backTapDetector: BackTapDetector? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes BackTapDetector and starts monitoring triple back-tap gestures.
     */
    fun init(context: Context) {
        com.example.voicecontrol.taptap.TapTapEngine.init(context)
        if (backTapDetector == null) {
            val detector = BackTapDetector(
                context = context.applicationContext,
                onTripleTap = {
                    toggleVoiceControl(context.applicationContext)
                }
            )
            backTapDetector = detector
            detector.startListening()
            Log.i(TAG, "VoiceControlManager initialized with Triple Back-Tap Detection.")
        }
    }

    /**
     * Starts listening for triple back taps.
     */
    fun startBackTapDetection(context: Context) {
        init(context)
        backTapDetector?.startListening()
    }

    /**
     * Stops listening for triple back taps.
     */
    fun stopBackTapDetection() {
        backTapDetector?.stopListening()
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
                Log.i(TAG, "Triple Back Tap -> Voice Control ACTIVATED")
            } else {
                // Stop Voice Control
                VoiceControlService.stop(context)
                Toast.makeText(context, "Voice Control Deactivated", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "Triple Back Tap -> Voice Control DEACTIVATED")
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
