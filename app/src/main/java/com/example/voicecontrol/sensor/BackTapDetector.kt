package com.example.voicecontrol.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Advanced BackTapDetector using Sensor Fusion (Linear Acceleration + Accelerometer + Gyroscope),
 * Low-Pass & High-Pass Impulse Filtering, Jerk Derivative calculation, and Multi-Layer False-Positive Suppression.
 * Optimized specifically for Vivo V40, Vivo V2348, and modern Android 12-16+ hardware.
 */
class BackTapDetector(
    private val context: Context,
    private val onSingleTap: (() -> Unit)? = null,
    private val onDoubleTap: (() -> Unit)? = null,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "BACK_TAP"

        // Filtering Coefficients
        private const val ALPHA_LOW_PASS = 0.82f
        private const val ALPHA_HIGH_PASS = 0.78f

        // Detection Thresholds tuned based on real Vivo V40 Logcat empirical data (peaks 3.0 - 5.5 m/s²)
        private const val IMPULSE_JERK_THRESHOLD = 6.5f // m/s³ linear acceleration derivative spike
        private const val HIGH_PASS_Z_THRESHOLD = 2.5f   // m/s² Z-axis sharp impact peak
        private const val MAX_GYRO_ROTATION_THRESHOLD = 3.5f // rad/s rotation ceiling (blocks walking/shaking)

        // Timing Rules
        private const val DEBOUNCE_INTERVAL_MS = 80L // Ignore repeated sensor spikes for 80ms (allows fast consecutive taps)
        private const val TRIPLE_TAP_WINDOW_MS = 1200L // 3 taps must complete within 1200ms window
        private const val LOCKOUT_PERIOD_MS = 600L // Cool-down period after successful detection (allows quick re-triggering)
        private const val MAX_VIBRATION_SPIKES_IN_WINDOW = 4 // Vehicle vibration suppression threshold
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isListening = false

    // Low-Pass Filter State (Gravity & Slow Drift)
    private var lpX = 0f
    private var lpY = 0f
    private var lpZ = 0f

    // High-Pass Filter State (Transient Impact Spikes)
    private var hpX = 0f
    private var hpY = 0f
    private var hpZ = 0f
    private var prevHpX = 0f
    private var prevHpY = 0f
    private var prevHpZ = 0f

    // Gyroscope Rotational State
    private var currentGyroMag = 0f

    // Vehicle Vibration Suppression Window
    private val recentSpikeTimestamps = mutableListOf<Long>()

    // Tap Event Sequence Sliding Window
    private val tapTimestamps = mutableListOf<Long>()
    private var lastTapTime = 0L
    private var lastDetectionTime = 0L

    /**
     * Starts continuous background sensor monitoring.
     */
    fun startListening() {
        if (isListening || sensorManager == null) return

        var registered = false
        val rate = SensorManager.SENSOR_DELAY_GAME

        try {
            // Prefer TYPE_LINEAR_ACCELERATION (gravity isolated)
            if (linearAccelSensor != null) {
                sensorManager.registerListener(this, linearAccelSensor, rate)
                registered = true
            } else if (accelSensor != null) {
                sensorManager.registerListener(this, accelSensor, rate)
                registered = true
            }

            gyroSensor?.let {
                sensorManager.registerListener(this, it, rate)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "HIGH_SAMPLING_RATE_SENSORS exception, falling back to SENSOR_DELAY_UI", e)
            try {
                val fallbackRate = SensorManager.SENSOR_DELAY_UI
                linearAccelSensor?.let { sensorManager.registerListener(this, it, fallbackRate); registered = true }
                    ?: accelSensor?.let { sensorManager.registerListener(this, it, fallbackRate); registered = true }
                gyroSensor?.let { sensorManager.registerListener(this, it, fallbackRate) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error registering sensor listener", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering sensor listener", e)
        }

        if (registered) {
            isListening = true
            resetFilters()
            Log.i(TAG, "BackTapDetector started listening (LinearAccel + Accel + Gyro).")
        } else {
            Log.w(TAG, "Unable to start BackTapDetector: Sensors not available.")
        }
    }

    /**
     * Unregisters sensor listeners to preserve battery.
     */
    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        resetFilters()
        Log.i(TAG, "BackTapDetector stopped listening.")
    }

    private fun resetFilters() {
        lpX = 0f; lpY = 0f; lpZ = 0f
        hpX = 0f; hpY = 0f; hpZ = 0f
        prevHpX = 0f; prevHpY = 0f; prevHpZ = 0f
        currentGyroMag = 0f
        tapTimestamps.clear()
        recentSpikeTimestamps.clear()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = SystemClock.elapsedRealtime()

        // Post-detection lockout check
        if (now - lastDetectionTime < LOCKOUT_PERIOD_MS) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                currentGyroMag = sqrt(gx * gx + gy * gy + gz * gz)
            }

            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                // 1. Low-Pass Filter (tracks baseline tilt / gravity)
                lpX = ALPHA_LOW_PASS * lpX + (1f - ALPHA_LOW_PASS) * rawX
                lpY = ALPHA_LOW_PASS * lpY + (1f - ALPHA_LOW_PASS) * rawY
                lpZ = ALPHA_LOW_PASS * lpZ + (1f - ALPHA_LOW_PASS) * rawZ

                // 2. High-Pass Filter (isolates transient back tap impact spikes)
                prevHpX = hpX; prevHpY = hpY; prevHpZ = hpZ
                hpX = rawX - lpX
                hpY = rawY - lpY
                hpZ = rawZ - lpZ

                // 3. Jerk (Derivative of High-Pass Acceleration Vector)
                val dX = hpX - prevHpX
                val dY = hpY - prevHpY
                val dZ = hpZ - prevHpZ
                val jerk = sqrt(dX * dX + dY * dY + dZ * dZ)
                val hpMagnitude = sqrt(hpX * hpX + hpY * hpY + hpZ * hpZ)

                // FALSE POSITIVE REJECTION RULES:
                // Rule A: Rotational Stability Guard (rejects walking, phone shaking, pocket movements)
                if (currentGyroMag > MAX_GYRO_ROTATION_THRESHOLD) return

                // Rule B: Vehicle Vibration Suppressor (rejects continuous road noise spikes)
                if (jerk > IMPULSE_JERK_THRESHOLD * 0.7f) {
                    recentSpikeTimestamps.removeAll { now - it > 400L }
                    recentSpikeTimestamps.add(now)
                    if (recentSpikeTimestamps.size > MAX_VIBRATION_SPIKES_IN_WINDOW) {
                        // Suppress vehicle vibration chatter
                        return
                    }
                }

                // Rule C: Debounce Check (must be >= 120ms after previous tap peak)
                if (now - lastTapTime < DEBOUNCE_INTERVAL_MS) return

                // Rule D: Transient Impulse Peak Detection
                val isImpactSpike = jerk >= IMPULSE_JERK_THRESHOLD || abs(hpZ) >= HIGH_PASS_Z_THRESHOLD

                if (isImpactSpike) {
                    lastTapTime = now
                    processTapEvent(now, hpMagnitude)
                }
            }
        }
    }

    private fun processTapEvent(timestamp: Long, peakValue: Float) {
        // Prune taps older than 1000ms window
        tapTimestamps.removeAll { timestamp - it > TRIPLE_TAP_WINDOW_MS }
        tapTimestamps.add(timestamp)

        val currentCount = tapTimestamps.size
        val systemTime = System.currentTimeMillis()

        // Required Logcat Output: Tap Detected | Tap Count | Peak Value | Timestamp
        Log.i(TAG, "Tap Detected | Tap Count: $currentCount | Peak Value: %.2f m/s² | Timestamp: %d".format(peakValue, systemTime))

        when (currentCount) {
            1 -> {
                onSingleTap?.invoke()
            }
            2 -> {
                onDoubleTap?.invoke()
            }
            3 -> {
                val firstTap = tapTimestamps.first()
                val duration = timestamp - firstTap

                if (duration <= TRIPLE_TAP_WINDOW_MS) {
                    lastDetectionTime = timestamp
                    tapTimestamps.clear()
                    Log.i(TAG, "TRIPLE TAP MATCHED! Sequence completed in ${duration}ms. Triggering Voice Control toggle.")
                    onTripleTap()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
