package com.example.voicecontrol.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener

/**
 * BackTapDetector Wrapper delegating gesture detection to the Columbus ML Detection Engine (ColumbusMlTapDetector).
 * Preserves 100% API compatibility across VoiceControlManager, VoiceAccessibilityService, and VoiceControlService.
 */
class BackTapDetector(
    private val context: Context,
    private val onSingleTap: (() -> Unit)? = null,
    private val onDoubleTap: (() -> Unit)? = null,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    private val mlDetector = ColumbusMlTapDetector(
        context = context,
        onSingleTap = onSingleTap,
        onDoubleTap = onDoubleTap,
        onTripleTap = onTripleTap
    )

    fun startListening() {
        mlDetector.startListening()
    }

    fun stopListening() {
        mlDetector.stopListening()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        mlDetector.onSensorChanged(event)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        mlDetector.onAccuracyChanged(sensor, accuracy)
    }
}
