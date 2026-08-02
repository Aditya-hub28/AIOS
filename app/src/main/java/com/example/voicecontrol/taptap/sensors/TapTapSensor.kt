package com.example.voicecontrol.taptap.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.example.voicecontrol.taptap.feedback.TapTapFeedback
import com.example.voicecontrol.taptap.gates.CameraGate
import com.example.voicecontrol.taptap.gates.ScreenStateGate
import com.example.voicecontrol.taptap.gates.TapTapGate
import com.example.voicecontrol.taptap.gates.TelephonyGate
import com.example.voicecontrol.taptap.settings.TapTapSettings
import com.example.voicecontrol.util.ScreenTouchTracker
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Sensor pipeline for TapTap gesture detection engine.
 * Integrates 6-axis signal processing, Jerk derivative peak detection,
 * gate filtering (Screen Off, Call active, Camera), and action dispatching.
 */
class TapTapSensor(
    private val context: Context,
    private val onDoubleTap: () -> Unit,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "TapTapSensor"
        private const val SAMPLE_PERIOD_MS = 20L
        private const val DEBOUNCE_MS = 80L
        private const val MAX_INTER_TAP_GAP_MS = 500L
        private const val TRIPLE_TAP_WINDOW_MS = 1200L
        private const val ALPHA = 0.82f
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val settings = TapTapSettings(context)

    // Gates
    private val gates: List<TapTapGate> = listOf(
        ScreenStateGate(),
        CameraGate().apply { init(context) },
        TelephonyGate()
    )

    private var isListening = false
    private var lastSampleTime = 0L
    private var lastTapTime = 0L

    // Signal state
    private var lpX = 0f; private var lpY = 0f; private var lpZ = 0f
    private var hpX = 0f; private var hpY = 0f; private var hpZ = 0f
    private var prevHpX = 0f; private var prevHpY = 0f; private var prevHpZ = 0f

    private var lastGyroX = 0f; private var lastGyroY = 0f; private var lastGyroZ = 0f

    private val tapTimestamps = mutableListOf<Long>()

    fun start() {
        if (isListening || sensorManager == null) return
        val rate = SensorManager.SENSOR_DELAY_GAME
        try {
            accelSensor?.let { sensorManager.registerListener(this, it, rate) }
            gyroSensor?.let { sensorManager.registerListener(this, it, rate) }
            isListening = true
            Log.i(TAG, "TapTapSensor started listening.")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting TapTapSensor", e)
        }
    }

    fun stop() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        tapTimestamps.clear()
        Log.i(TAG, "TapTapSensor stopped listening.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !settings.isTapTapEnabled) return

        // Check Gates
        for (gate in gates) {
            if (gate.isBlocked(context)) return
        }

        val now = SystemClock.elapsedRealtime()

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                lastGyroX = event.values[0]
                lastGyroY = event.values[1]
                lastGyroZ = event.values[2]
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                // Low pass gravity tracking
                lpX = ALPHA * lpX + (1f - ALPHA) * rawX
                lpY = ALPHA * lpY + (1f - ALPHA) * rawY
                lpZ = ALPHA * lpZ + (1f - ALPHA) * rawZ

                prevHpX = hpX; prevHpY = hpY; prevHpZ = hpZ
                hpX = rawX - lpX
                hpY = rawY - lpY
                hpZ = rawZ - lpZ

                if (now - lastSampleTime < SAMPLE_PERIOD_MS) return
                lastSampleTime = now

                val dX = hpX - prevHpX
                val dY = hpY - prevHpY
                val dZ = hpZ - prevHpZ
                val jerk = sqrt(dX * dX + dY * dY + dZ * dZ)
                val vectorMag = sqrt(hpX * hpX + hpY * hpY + hpZ * hpZ)
                val absZ = abs(hpZ)
                val zRatio = if (vectorMag > 0.01f) absZ / vectorMag else 0f
                val gyroMag = sqrt(lastGyroX * lastGyroX + lastGyroY * lastGyroY + lastGyroZ * lastGyroZ)

                // Screen touch & keyboard suppression
                if (ScreenTouchTracker.isKeyboardTypingSuppressed(now)) return
                if (now - ScreenTouchTracker.lastScreenTouchTimestamp in 0L..400L) return

                // Physics Tap Classification
                if (gyroMag < 0.45f && jerk >= 0.44f && zRatio >= 0.80f) {
                    if (now - lastTapTime < DEBOUNCE_MS) return
                    lastTapTime = now
                    onTapDetected(now)
                }
            }
        }
    }

    private fun onTapDetected(now: Long) {
        // Clear stale taps
        if (tapTimestamps.isNotEmpty() && (now - tapTimestamps.last() > MAX_INTER_TAP_GAP_MS)) {
            tapTimestamps.clear()
        }

        tapTimestamps.add(now)
        Log.i(TAG, "Tap Event Accepted [Sequence Count: ${tapTimestamps.size}]")

        if (settings.isHapticFeedbackEnabled) {
            TapTapFeedback.triggerHaptic(context)
        }

        if (tapTimestamps.size == 3) {
            val totalDuration = now - tapTimestamps.first()
            if (totalDuration <= TRIPLE_TAP_WINDOW_MS) {
                Log.i(TAG, "🔥 TRIPLE TAP DETECTED! (Duration: ${totalDuration}ms)")
                tapTimestamps.clear()
                onTripleTap()
            } else {
                tapTimestamps.clear()
            }
        } else if (tapTimestamps.size == 2) {
            // Schedule double tap trigger if 3rd tap does not follow within 300ms
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (tapTimestamps.size == 2 && SystemClock.elapsedRealtime() - tapTimestamps.last() >= 280L) {
                    Log.i(TAG, "🔥 DOUBLE TAP DETECTED!")
                    tapTimestamps.clear()
                    onDoubleTap()
                }
            }, 300L)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
