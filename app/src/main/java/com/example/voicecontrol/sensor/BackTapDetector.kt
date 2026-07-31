package com.example.voicecontrol.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.example.voicecontrol.overlay.BackTapDebugOverlay
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * State Machine Enum for Back Tap Detection.
 */
enum class BackTapDetectorState {
    IDLE,
    POSSIBLE_TAP,
    VALID_TAP,
    TRIPLE_TAP
}

/**
 * Motion Classifier Enum.
 */
enum class MotionClassification {
    STILL,
    MOVING,
    SHAKING,
    BACK_TAP_LIKE
}

/**
 * BackTapDetector with FULL Sensor Debugging System.
 * Tag: BACK_TAP_DEBUG
 * Features: 500-sample rolling buffer, motion classification, state transition logging,
 * event logging (NOISE_DETECTED, POSSIBLE_TAP, VALID_TAP, TRIPLE_TAP, FALSE_TRIGGER),
 * tap timing gap analysis, CSV telemetry, and live BackTapDebugOverlay updates.
 */
class BackTapDetector(
    private val context: Context,
    private val onSingleTap: (() -> Unit)? = null,
    private val onDoubleTap: (() -> Unit)? = null,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    companion object {
        const val DEBUG_TAG = "BACK_TAP_DEBUG"

        // Filtering Coefficients
        private const val ALPHA_LOW_PASS = 0.82f

        // Thresholds (Observability mode: unchanged values)
        var MIN_TAP_IMPULSE = 0.30f
        var MAX_TAP_IMPULSE = 5.00f
        var MIN_JERK_THRESHOLD = 0.35f
        var MAX_GYRO_ROTATION = 1.50f

        // Timing Rules
        private const val DEBOUNCE_INTERVAL_MS = 80L
        private const val TRIPLE_TAP_WINDOW_MS = 1200L
        private const val LOCKOUT_PERIOD_MS = 1200L
        private const val ROLLING_BUFFER_MAX_SIZE = 500
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val linearAccelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isListening = false

    // State Machine State
    var currentState: BackTapDetectorState = BackTapDetectorState.IDLE
        private set

    var currentMotion: MotionClassification = MotionClassification.STILL
        private set

    // Sensor State Variables
    private var accelX = 0f; private var accelY = 0f; private var accelZ = 0f
    private var linX = 0f; private var linY = 0f; private var linZ = 0f
    private var gyroX = 0f; var gyroY = 0f; var gyroZ = 0f

    private var lpX = 0f; private var lpY = 0f; private var lpZ = 0f
    private var hpX = 0f; private var hpY = 0f; private var hpZ = 0f
    private var prevHpX = 0f; private var prevHpY = 0f; private var prevHpZ = 0f

    private var currentGyroMag = 0f

    // 500-sample Rolling Sensor Buffer
    private val rollingBuffer = ArrayDeque<SensorSample>(ROLLING_BUFFER_MAX_SIZE)

    // Tap Timings & Windows
    private val tapTimestamps = mutableListOf<Long>()
    private var lastTapTime = 0L
    private var lastDetectionTime = 0L
    private var lastGapMs = 0L

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
            Log.w(DEBUG_TAG, "HIGH_SAMPLING_RATE_SENSORS exception, fallback to SENSOR_DELAY_UI", e)
            try {
                val fallbackRate = SensorManager.SENSOR_DELAY_UI
                linearAccelSensor?.let { sensorManager.registerListener(this, it, fallbackRate); registered = true }
                    ?: accelSensor?.let { sensorManager.registerListener(this, it, fallbackRate); registered = true }
                gyroSensor?.let { sensorManager.registerListener(this, it, fallbackRate) }
            } catch (ex: Exception) {
                Log.e(DEBUG_TAG, "Error registering sensor listener", ex)
            }
        } catch (e: Exception) {
            Log.e(DEBUG_TAG, "Error registering sensor listener", e)
        }

        if (registered) {
            isListening = true
            resetFilters()
            transitionState(BackTapDetectorState.IDLE, "startListening")
            Log.i(DEBUG_TAG, "=== BACK TAP DEBUGGER ACTIVE ===")
        }
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        resetFilters()
        Log.i(DEBUG_TAG, "BackTapDetector stopped listening.")
    }

    private fun resetFilters() {
        lpX = 0f; lpY = 0f; lpZ = 0f
        hpX = 0f; hpY = 0f; hpZ = 0f
        prevHpX = 0f; prevHpY = 0f; prevHpZ = 0f
        currentGyroMag = 0f
        tapTimestamps.clear()
        rollingBuffer.clear()
    }

    private fun transitionState(newState: BackTapDetectorState, reason: String) {
        if (currentState != newState) {
            Log.i(DEBUG_TAG, "STATE_CHANGE: ${currentState.name} -> ${newState.name} [Reason: $reason]")
            currentState = newState
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = SystemClock.elapsedRealtime()

        if (now - lastDetectionTime < LOCKOUT_PERIOD_MS) return

        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                gyroX = event.values[0]
                gyroY = event.values[1]
                gyroZ = event.values[2]
                currentGyroMag = sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ)
            }

            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_ACCELEROMETER -> {
                val rawX = event.values[0]
                val rawY = event.values[1]
                val rawZ = event.values[2]

                if (event.sensor.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    linX = rawX; linY = rawY; linZ = rawZ
                } else {
                    accelX = rawX; accelY = rawY; accelZ = rawZ
                }

                val startTimeNanos = SystemClock.elapsedRealtimeNanos()

                // Low-pass & High-pass filtering
                lpX = ALPHA_LOW_PASS * lpX + (1f - ALPHA_LOW_PASS) * rawX
                lpY = ALPHA_LOW_PASS * lpY + (1f - ALPHA_LOW_PASS) * rawY
                lpZ = ALPHA_LOW_PASS * lpZ + (1f - ALPHA_LOW_PASS) * rawZ

                prevHpX = hpX; prevHpY = hpY; prevHpZ = hpZ
                hpX = rawX - lpX
                hpY = rawY - lpY
                hpZ = rawZ - lpZ

                val dX = hpX - prevHpX
                val dY = hpY - prevHpY
                val dZ = hpZ - prevHpZ
                val jerk = sqrt(dX * dX + dY * dY + dZ * dZ)
                val hpMagnitude = sqrt(hpX * hpX + hpY * hpY + hpZ * hpZ)
                val absZ = abs(hpZ)

                // 1. Motion Classification (Requirement 9)
                currentMotion = classifyMotion(hpMagnitude, currentGyroMag, jerk, absZ)

                // 2. Add to 500-sample Rolling Buffer (Requirement 8)
                val sample = SensorSample(
                    timestamp = System.currentTimeMillis(),
                    accelX = accelX, accelY = accelY, accelZ = accelZ,
                    linX = linX, linY = linY, linZ = linZ,
                    gyroX = gyroX, gyroY = gyroY, gyroZ = gyroZ,
                    magnitude = hpMagnitude, zPeak = absZ, jerk = jerk, gyroMag = currentGyroMag,
                    stateName = currentState.name, motionName = currentMotion.name
                )

                if (rollingBuffer.size >= ROLLING_BUFFER_MAX_SIZE) {
                    rollingBuffer.removeFirst()
                }
                rollingBuffer.addLast(sample)

                // 3. Log CSV Sample Telemetry (Requirement 2 & 7)
                Log.d(DEBUG_TAG, sample.toCsvString())

                // Compute processing latency
                val latencyMs = ((SystemClock.elapsedRealtimeNanos() - startTimeNanos) / 1_000_000L).coerceAtLeast(1L)
                val timeSinceLastTap = if (lastTapTime > 0) now - lastTapTime else 0L

                // 4. Update Live On-Screen Debug HUD Overlay (Requirement 1)
                if (BackTapDebugOverlay.isDebugOverlayVisible) {
                    BackTapDebugOverlay.updateTelemetry(
                        state = currentState.name,
                        motion = currentMotion.name,
                        taps = tapTimestamps.size,
                        gapMs = lastGapMs,
                        ax = accelX, ay = accelY, az = accelZ,
                        lx = linX, ly = linY, lz = linZ,
                        gx = gyroX, gy = gyroY, gz = gyroZ,
                        mag = hpMagnitude, zp = absZ, jk = jerk, gm = currentGyroMag,
                        minImp = MIN_TAP_IMPULSE, maxImp = MAX_TAP_IMPULSE,
                        minJk = MIN_JERK_THRESHOLD, maxGy = MAX_GYRO_ROTATION
                    )
                }

                // Update BackTapDebugManager Repository for Jetpack Compose In-App Dashboard & Overlay
                com.example.voicecontrol.manager.BackTapDebugManager.updateTelemetry(
                    ax = accelX, ay = accelY, az = accelZ,
                    lx = linX, ly = linY, lz = linZ,
                    gx = gyroX, gy = gyroY, gz = gyroZ,
                    mag = hpMagnitude, peak = hpMagnitude, zp = absZ, jk = jerk, gm = currentGyroMag,
                    minImp = MIN_TAP_IMPULSE, maxImp = MAX_TAP_IMPULSE, minJk = MIN_JERK_THRESHOLD, maxGy = MAX_GYRO_ROTATION,
                    state = currentState.name, motion = currentMotion.name, count = tapTimestamps.size,
                    timeSinceLastTap = timeSinceLastTap, latencyMs = latencyMs
                )

                // 5. Sensor Filters & Tap Evaluation
                if (currentGyroMag > MAX_GYRO_ROTATION) {
                    if (hpMagnitude > 1.0f) {
                        Log.w(DEBUG_TAG, "NOISE_DETECTED: Gyro rotation too high (%.2f > %.2f)".format(currentGyroMag, MAX_GYRO_ROTATION))
                        com.example.voicecontrol.manager.BackTapDebugManager.logEvent("NOISE [Gyro: %.2f]".format(currentGyroMag))
                    }
                    return
                }

                if (now - lastTapTime < DEBOUNCE_INTERVAL_MS) return

                val isImpactRange = (absZ in MIN_TAP_IMPULSE..MAX_TAP_IMPULSE) ||
                        (hpMagnitude in MIN_TAP_IMPULSE..MAX_TAP_IMPULSE)
                val isSharpJerk = jerk >= MIN_JERK_THRESHOLD

                if (isImpactRange && isSharpJerk) {
                    lastTapTime = now
                    processTapEvent(now, hpMagnitude, jerk)
                } else if (hpMagnitude > 0.8f && !isSharpJerk) {
                    Log.w(DEBUG_TAG, "INVALID_TAP: Impulse peak %.2f m/s² rejected (Jerk %.2f < %.2f)".format(hpMagnitude, jerk, MIN_JERK_THRESHOLD))
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("INVALID_TAP [Jerk: %.2f]".format(jerk))
                }
            }
        }
    }

    private fun classifyMotion(mag: Float, gyro: Float, jerk: Float, absZ: Float): MotionClassification {
        return when {
            gyro < 0.20f && mag < 0.25f -> MotionClassification.STILL
            gyro > 1.80f || mag > 4.50f -> MotionClassification.SHAKING
            (absZ in MIN_TAP_IMPULSE..MAX_TAP_IMPULSE) && jerk >= MIN_JERK_THRESHOLD -> MotionClassification.BACK_TAP_LIKE
            else -> MotionClassification.MOVING
        }
    }

    private fun processTapEvent(timestamp: Long, peakValue: Float, jerkValue: Float) {
        val currentTime = System.currentTimeMillis()

        if (tapTimestamps.isNotEmpty()) {
            lastGapMs = timestamp - tapTimestamps.last()
        }

        tapTimestamps.removeAll { timestamp - it > TRIPLE_TAP_WINDOW_MS }
        tapTimestamps.add(timestamp)

        val currentCount = tapTimestamps.size

        when (currentCount) {
            1 -> {
                transitionState(BackTapDetectorState.POSSIBLE_TAP, "Tap #1 detected")
                Log.i(DEBUG_TAG, "POSSIBLE_TAP: Tap #1 at ${currentTime}ms")
                com.example.voicecontrol.manager.BackTapDebugManager.logEvent("POSSIBLE_TAP")
                com.example.voicecontrol.manager.BackTapDebugManager.logEvent("VALID_TAP #1")
                onSingleTap?.invoke()
            }
            2 -> {
                transitionState(BackTapDetectorState.VALID_TAP, "Tap #2 detected")
                Log.i(DEBUG_TAG, "VALID_TAP: Tap #2 at ${currentTime}ms | Gap = ${lastGapMs}ms")
                com.example.voicecontrol.manager.BackTapDebugManager.logEvent("VALID_TAP #2 (Gap: ${lastGapMs}ms)")
                onDoubleTap?.invoke()
            }
            3 -> {
                val firstTap = tapTimestamps.first()
                val duration = timestamp - firstTap

                if (duration <= TRIPLE_TAP_WINDOW_MS) {
                    lastDetectionTime = timestamp
                    transitionState(BackTapDetectorState.TRIPLE_TAP, "Triple tap sequence matched")
                    Log.i(DEBUG_TAG, "TRIPLE_TAP: Tap #3 at ${currentTime}ms | Gap = ${lastGapMs}ms")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("VALID_TAP #3 (Gap: ${lastGapMs}ms)")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("TRIPLE_TAP")

                    // Requirement 8: Dump last 500 sensor samples to Logcat
                    dumpRollingBufferToLogcat("TRIPLE_TAP")

                    tapTimestamps.clear()
                    onTripleTap()
                } else {
                    Log.w(DEBUG_TAG, "FALSE_TRIGGER: Tap #3 duration ${duration}ms exceeded ${TRIPLE_TAP_WINDOW_MS}ms window. Dumping buffer...")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("FALSE_TRIGGER (Duration ${duration}ms)")
                    dumpRollingBufferToLogcat("FALSE_TRIGGER")
                    transitionState(BackTapDetectorState.IDLE, "Window expired")
                }
            }
        }
    }

    /**
     * Dumps all 500 rolling samples from buffer to Logcat under tag BACK_TAP_DEBUG.
     */
    private fun dumpRollingBufferToLogcat(triggerReason: String) {
        Log.i(DEBUG_TAG, "========== BEGIN ROLLING BUFFER DUMP (500 SAMPLES) [$triggerReason] ==========")
        for ((index, sample) in rollingBuffer.withIndex()) {
            Log.i(DEBUG_TAG, "SAMPLE [#%03d] %s".format(index + 1, sample.toCsvString()))
        }
        Log.i(DEBUG_TAG, "========== END ROLLING BUFFER DUMP (500 SAMPLES) ==========")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
