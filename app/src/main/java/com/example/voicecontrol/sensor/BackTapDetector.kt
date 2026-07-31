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
 * SensorEventListener detecting sharp triple back-tap impulses on the phone.
 * Combines Accelerometer & Gyroscope sensor fusion with false-positive motion filtering
 * (rejects walking motion, phone shaking, pocket movements, and random vibrations).
 * Tuned for highly responsive real-device back tap detection across Android 12, 13, 14, 15, and 16+.
 */
class BackTapDetector(
    private val context: Context,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "BackTapDetector"

        // Sensor threshold tuning parameters (tuned for realistic finger taps on phone back/case)
        private const val TAP_ACCEL_THRESHOLD = 6.5f // m/s² acceleration impulse spike
        private const val TAP_Z_ACCEL_THRESHOLD = 5.5f // m/s² Z-axis sharp inward impact spike
        private const val MAX_GYRO_ROTATION_THRESHOLD = 4.5f // rad/s max allowed angular velocity
        private const val MIN_TAP_INTERVAL_MS = 100L // Min ms between taps (prevents vibration echo)
        private const val TRIPLE_TAP_WINDOW_MS = 1200L // 3 taps within 1200ms window
        private const val LOCKOUT_PERIOD_MS = 1200L // Lockout cool-down after detection
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
     * Registers sensor listeners for Accelerometer & Gyroscope using SENSOR_DELAY_FASTEST.
     */
    fun startListening() {
        if (isListening || sensorManager == null) return

        var registered = false
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
            registered = true
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST)
        }

        if (registered) {
            isListening = true
            hasAccelBaseline = false
            tapTimestamps.clear()
            Log.i(TAG, "BackTapDetector started listening with SENSOR_DELAY_FASTEST.")
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
                // 1. Acceleration delta or Z-axis delta must exceed tap threshold
                // 2. Gyroscope rotational speed must be below rotation ceiling
                // 3. Debounce interval between taps
                val isTapImpulse = deltaMag > TAP_ACCEL_THRESHOLD || abs(dAz) > TAP_Z_ACCEL_THRESHOLD
                val isLowRotation = currentGyroMag < MAX_GYRO_ROTATION_THRESHOLD
                val isDebounced = (now - lastTapTime) >= MIN_TAP_INTERVAL_MS

                if (isTapImpulse && isLowRotation && isDebounced) {
                    lastTapTime = now
                    Log.i(TAG, "Back Tap Impulse Detected! deltaMag=%.2f, dAz=%.2f, gyroMag=%.2f".format(deltaMag, dAz, currentGyroMag))
                    recordTap(now)
                }
            }
        }
    }

    private fun recordTap(timestamp: Long) {
        // Prune taps older than window
        tapTimestamps.removeAll { timestamp - it > TRIPLE_TAP_WINDOW_MS }

        tapTimestamps.add(timestamp)
        Log.i(TAG, "Back tap count: ${tapTimestamps.size} / 3 in window")

        if (tapTimestamps.size >= 3) {
            val firstTap = tapTimestamps.first()
            val duration = timestamp - firstTap

            if (duration <= TRIPLE_TAP_WINDOW_MS) {
                lastDetectionTime = timestamp
                tapTimestamps.clear()
                Log.i(TAG, "TRIPLE BACK TAP SUCCESSFUL! Sequence completed in ${duration}ms.")
                onTripleTap()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
