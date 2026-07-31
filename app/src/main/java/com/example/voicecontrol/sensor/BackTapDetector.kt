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
 * Diagnostic SensorEventListener for Back Tap Tuning.
 * Logs all real-time sensor metrics (peak acceleration, Z-axis impact, Jerk derivative, Gyro rotation)
 * directly to Logcat under tag "BACK_TAP" so you can test under different physical conditions.
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

        // Diagnostic & Calibrated Thresholds based on real device sensor data
        var MIN_TAP_IMPULSE = 0.30f       // Minimum peak m/s² (captures 0.45 - 0.67 m/s² subtle back taps)
        var MAX_TAP_IMPULSE = 5.00f       // Maximum peak m/s² (rejects heavy motion/shakes)
        var MIN_JERK_THRESHOLD = 0.35f    // Minimum Jerk m/s³ derivative spike (captures 0.67 m/s³)
        var MAX_GYRO_ROTATION = 1.50f     // rad/s gyro rotation ceiling (held stable: 0.08 rad/s)

        // Timing Rules
        private const val DEBOUNCE_INTERVAL_MS = 80L // 80ms debounce between distinct taps
        private const val TRIPLE_TAP_WINDOW_MS = 1200L // 3 taps must complete within 1200ms
        private const val LOCKOUT_PERIOD_MS = 1200L // Cool-down period after triple tap match
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isListening = false

    // Low-Pass Filter State (Gravity / Slow Drift)
    private var lpX = 0f
    private var lpY = 0f
    private var lpZ = 0f

    // High-Pass Filter State
    private var hpX = 0f
    private var hpY = 0f
    private var hpZ = 0f
    private var prevHpX = 0f
    private var prevHpY = 0f
    private var prevHpZ = 0f

    // Gyroscope Rotational State
    private var currentGyroMag = 0f

    // Tap Event Sequence Sliding Window
    private val tapTimestamps = mutableListOf<Long>()
    private var lastTapTime = 0L
    private var lastDiagnosticLogTime = 0L
    private var lastDetectionTime = 0L

    fun startListening() {
        if (isListening || sensorManager == null) return

        var registered = false
        val rate = SensorManager.SENSOR_DELAY_GAME

        try {
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
            Log.w(TAG, "HIGH_SAMPLING_RATE_SENSORS fallback to SENSOR_DELAY_UI", e)
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
            Log.i(TAG, "=== BackTapDetector DIAGNOSTIC LOGGER STARTED ===")
        }
    }

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
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = SystemClock.elapsedRealtime()

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

                // Low-Pass Filter
                lpX = ALPHA_LOW_PASS * lpX + (1f - ALPHA_LOW_PASS) * rawX
                lpY = ALPHA_LOW_PASS * lpY + (1f - ALPHA_LOW_PASS) * rawY
                lpZ = ALPHA_LOW_PASS * lpZ + (1f - ALPHA_LOW_PASS) * rawZ

                // High-Pass Filter
                prevHpX = hpX; prevHpY = hpY; prevHpZ = hpZ
                hpX = rawX - lpX
                hpY = rawY - lpY
                hpZ = rawZ - lpZ

                // Compute Jerk Derivative & Magnitude
                val dX = hpX - prevHpX
                val dY = hpY - prevHpY
                val dZ = hpZ - prevHpZ
                val jerk = sqrt(dX * dX + dY * dY + dZ * dZ)
                val hpMagnitude = sqrt(hpX * hpX + hpY * hpY + hpZ * hpZ)
                val absZ = abs(hpZ)

                // DIAGNOSTIC METRIC LOGGER: Log any noticeable impulse spike (> 0.40 m/s²)
                if (hpMagnitude > 0.40f && (now - lastDiagnosticLogTime >= 50L)) {
                    lastDiagnosticLogTime = now
                    Log.d(TAG, "SENSOR_IMPULSE: peak=%.2f m/s² | zPeak=%.2f m/s² | jerk=%.2f m/s³ | gyro=%.2f rad/s".format(
                        hpMagnitude, absZ, jerk, currentGyroMag
                    ))
                }

                // ROTATIONAL STABILITY FILTER
                if (currentGyroMag > MAX_GYRO_ROTATION) return

                // DEBOUNCE CHECK
                if (now - lastTapTime < DEBOUNCE_INTERVAL_MS) return

                // TAP EVALUATION
                val isImpactRange = (absZ in MIN_TAP_IMPULSE..MAX_TAP_IMPULSE) ||
                        (hpMagnitude in MIN_TAP_IMPULSE..MAX_TAP_IMPULSE)
                val isSharpJerk = jerk >= MIN_JERK_THRESHOLD

                if (isImpactRange && isSharpJerk) {
                    lastTapTime = now
                    processTapEvent(now, hpMagnitude, jerk)
                }
            }
        }
    }

    private fun processTapEvent(timestamp: Long, peakValue: Float, jerkValue: Float) {
        tapTimestamps.removeAll { timestamp - it > TRIPLE_TAP_WINDOW_MS }
        tapTimestamps.add(timestamp)

        val currentCount = tapTimestamps.size
        val systemTime = System.currentTimeMillis()

        Log.i(TAG, "TAP_REGISTERED | Count: $currentCount/3 | Peak: %.2f m/s² | Jerk: %.2f m/s³ | Gyro: %.2f | TS: %d".format(
            peakValue, jerkValue, currentGyroMag, systemTime
        ))

        when (currentCount) {
            1 -> onSingleTap?.invoke()
            2 -> onDoubleTap?.invoke()
            3 -> {
                val firstTap = tapTimestamps.first()
                val duration = timestamp - firstTap

                if (duration <= TRIPLE_TAP_WINDOW_MS) {
                    lastDetectionTime = timestamp
                    tapTimestamps.clear()
                    Log.i(TAG, ">>> TRIPLE TAP MATCHED! Completed in %d ms. Triggering Voice Control. <<<".format(duration))
                    onTripleTap()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
