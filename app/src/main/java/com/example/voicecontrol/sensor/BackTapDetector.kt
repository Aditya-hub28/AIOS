package com.example.voicecontrol.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlin.math.sqrt

/**
 * SensorEventListener detecting sharp triple back-tap impulses on the phone.
 * Combines Accelerometer & Gyroscope sensor fusion with false-positive motion filtering
 * (rejects walking motion, phone shaking, pocket movements, and random vibrations).
 * Low battery consumption & compatible with Android 12, 13, 14, 15, and 16+.
 */
class BackTapDetector(
    private val context: Context,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "BackTapDetector"

        // Sensor threshold tuning parameters
        private const val TAP_ACCEL_THRESHOLD = 13.5f // m/s² acceleration impulse spike
        private const val MAX_GYRO_ROTATION_THRESHOLD = 2.8f // rad/s maximum allowed angular velocity (rejects walking/shaking)
        private const val MIN_TAP_INTERVAL_MS = 120L // Minimum ms between taps (prevents vibration echo double counts)
        private const val TRIPLE_TAP_WINDOW_MS = 1000L // Entire 3-tap sequence must finish within 1000ms
        private const val LOCKOUT_PERIOD_MS = 1500L // Lockout duration after successful triple tap detection
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isListening = false

    // Accelerometer state for delta derivative calculation
    private var lastAx = 0f
    private var lastAy = 0f
    private var lastAz = 0f
    private var hasAccelBaseline = false

    // Gyroscope angular velocity magnitude
    private var currentGyroMag = 0f

    // Sliding window of tap timestamps
    private val tapTimestamps = mutableListOf<Long>()
    private var lastTapTime = 0L
    private var lastDetectionTime = 0L

    /**
     * Registers sensor listeners for Accelerometer & Gyroscope.
     */
    fun startListening() {
        if (isListening || sensorManager == null) return

        var registered = false
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            registered = true
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        if (registered) {
            isListening = true
            hasAccelBaseline = false
            tapTimestamps.clear()
            Log.i(TAG, "BackTapDetector started listening (Accelerometer + Gyroscope).")
        } else {
            Log.w(TAG, "Unable to start BackTapDetector: Accelerometer sensor not available.")
        }
    }

    /**
     * Unregisters sensor listeners to preserve battery.
     */
    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        tapTimestamps.clear()
        Log.i(TAG, "BackTapDetector stopped listening.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        val now = SystemClock.elapsedRealtime()

        // Ignore events during post-detection lockout period
        if (now - lastDetectionTime < LOCKOUT_PERIOD_MS) {
            return
        }

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val gx = event.values[0]
                val gy = event.values[1]
                val gz = event.values[2]
                currentGyroMag = sqrt(gx * gx + gy * gy + gz * gz)
            }

            Sensor.TYPE_ACCELEROMETER -> {
                val ax = event.values[0]
                val ay = event.values[1]
                val az = event.values[2]

                if (!hasAccelBaseline) {
                    lastAx = ax
                    lastAy = ay
                    lastAz = az
                    hasAccelBaseline = true
                    return
                }

                // Compute high-pass impulse derivative (delta acceleration)
                val dAx = ax - lastAx
                val dAy = ay - lastAy
                val dAz = az - lastAz
                lastAx = ax
                lastAy = ay
                lastAz = az

                val deltaMag = sqrt(dAx * dAx + dAy * dAy + dAz * dAz)

                // NOISE REJECTION FILTERS:
                // 1. Acceleration delta must exceed tap threshold (sharp impact)
                // 2. Gyroscope rotational speed must be low (rejects walking/shaking/pocket motion)
                // 3. Minimum interval between distinct taps (prevents vibration echo)
                val isTapImpulse = deltaMag > TAP_ACCEL_THRESHOLD
                val isLowRotation = currentGyroMag < MAX_GYRO_ROTATION_THRESHOLD
                val isDebounced = (now - lastTapTime) >= MIN_TAP_INTERVAL_MS

                if (isTapImpulse && isLowRotation && isDebounced) {
                    lastTapTime = now
                    recordTap(now)
                }
            }
        }
    }

    private fun recordTap(timestamp: Long) {
        // Prune taps older than 1000ms window
        tapTimestamps.removeAll { timestamp - it > TRIPLE_TAP_WINDOW_MS }

        tapTimestamps.add(timestamp)
        Log.d(TAG, "Back tap detected! Count: ${tapTimestamps.size} / 3 in window")

        if (tapTimestamps.size >= 3) {
            val firstTap = tapTimestamps.first()
            val lastTap = tapTimestamps.last()
            val duration = lastTap - firstTap

            if (duration <= TRIPLE_TAP_WINDOW_MS) {
                lastDetectionTime = timestamp
                tapTimestamps.clear()
                Log.i(TAG, "TRIPLE BACK TAP DETECTED! Sequence completed in ${duration}ms. Triggering action.")
                onTripleTap()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No action required on accuracy change
    }
}
