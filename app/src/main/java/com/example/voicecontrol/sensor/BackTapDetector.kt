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
 * BackTapDetector with Strict 3-Tap Sequence State Machine.
 * Tag: BACK_TAP_DEBUG
 * Rules:
 * 1. Requires 3 VALID taps within rolling 1000ms window (t3 - t1 < 1000ms).
 * 2. If inter-tap gap exceeds 400ms -> triggers SEQUENCE_RESET.
 * 3. Logs SEQUENCE_STARTED, SEQUENCE_RESET, SEQUENCE_COMPLETED.
 * 4. Displays sequence progress (1/3, 2/3, 3/3) and timestamp arrays [t1, t2, t3].
 * 5. Prevents count corruption (#2, #2, #2) by maintaining strict sequence start time and clearing list on reset.
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

        // Thresholds (Calibrated for device)
        var MIN_TAP_IMPULSE = 0.30f
        var MAX_TAP_IMPULSE = 5.00f
        var MIN_JERK_THRESHOLD = 0.35f
        var MAX_GYRO_ROTATION = 1.50f

        // Timing Rules (Strict Specification)
        private const val DEBOUNCE_INTERVAL_MS = 80L
        private const val MAX_INTER_TAP_GAP_MS = 400L  // Max gap between consecutive taps: 400ms
        private const val TRIPLE_TAP_WINDOW_MS = 1000L // Total 3-tap duration window: 1000ms (t3 - t1 < 1000ms)
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

    // Sequence State Machine Variables
    private val tapTimestamps = mutableListOf<Long>()
    private var sequenceStartTime = 0L
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
            Log.i(DEBUG_TAG, "=== BACK TAP SEQUENCE DETECTOR ACTIVE ===")
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
        sequenceStartTime = 0L
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

                // 1. Motion Classification
                currentMotion = classifyMotion(hpMagnitude, currentGyroMag, jerk, absZ)

                // 2. Add to 500-sample Rolling Buffer
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

                // 3. Log CSV Sample Telemetry
                Log.d(DEBUG_TAG, sample.toCsvString())

                // Performance Metrics
                val latencyMs = ((SystemClock.elapsedRealtimeNanos() - startTimeNanos) / 1_000_000L).coerceAtLeast(1L)
                val timeSinceLastTap = if (lastTapTime > 0) now - lastTapTime else 0L
                val sequenceText = "${tapTimestamps.size}/3"

                // 4. Update Live On-Screen Debug HUD Overlay
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
                    seqText = sequenceText, timestamps = tapTimestamps.toList(),
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

    /**
     * Strict Sequence Processing logic enforcing max 400ms inter-tap gap and max 1000ms rolling window (t3 - t1 < 1000ms).
     */
    private fun processTapEvent(timestamp: Long, peakValue: Float, jerkValue: Float) {
        val gap = if (tapTimestamps.isNotEmpty()) timestamp - tapTimestamps.last() else 0L
        lastGapMs = gap

        // 1. Max Inter-Tap Gap Rule: If gap > 400ms, RESET SEQUENCE!
        if (tapTimestamps.isNotEmpty() && gap > MAX_INTER_TAP_GAP_MS) {
            Log.w(DEBUG_TAG, "SEQUENCE_RESET: Gap ${gap}ms exceeded ${MAX_INTER_TAP_GAP_MS}ms limit. Resetting sequence.")
            com.example.voicecontrol.manager.BackTapDebugManager.logEvent("SEQUENCE_RESET (Gap ${gap}ms > 400ms)")
            tapTimestamps.clear()
            sequenceStartTime = 0L
            transitionState(BackTapDetectorState.IDLE, "Inter-tap gap exceeded 400ms")
        }

        // 2. Start Sequence if empty
        if (tapTimestamps.isEmpty()) {
            sequenceStartTime = timestamp
            Log.i(DEBUG_TAG, "SEQUENCE_STARTED at ${timestamp}ms")
            com.example.voicecontrol.manager.BackTapDebugManager.logEvent("SEQUENCE_STARTED")
        }

        tapTimestamps.add(timestamp)
        val currentCount = tapTimestamps.size
        val totalDuration = timestamp - sequenceStartTime

        // 3. Debug Output Logging (Requirement 9)
        Log.i(DEBUG_TAG, "TAP_EVENT: tapCount=$currentCount | lastTapTime=$timestamp | sequenceStartTime=$sequenceStartTime | Gap=${gap}ms | Tap Times=$tapTimestamps")

        when (currentCount) {
            1 -> {
                transitionState(BackTapDetectorState.POSSIBLE_TAP, "Tap 1/3")
                Log.i(DEBUG_TAG, "VALID_TAP 1/3 at ${timestamp}ms | Timestamps: $tapTimestamps")
                com.example.voicecontrol.manager.BackTapDebugManager.logEvent("VALID_TAP 1/3")
                onSingleTap?.invoke()
            }
            2 -> {
                transitionState(BackTapDetectorState.VALID_TAP, "Tap 2/3")
                Log.i(DEBUG_TAG, "VALID_TAP 2/3 at ${timestamp}ms | Gap: ${gap}ms | Timestamps: $tapTimestamps")
                com.example.voicecontrol.manager.BackTapDebugManager.logEvent("VALID_TAP 2/3 (Gap: ${gap}ms)")
                onDoubleTap?.invoke()
            }
            3 -> {
                // Rule: (t3 - t1) < 1000ms
                if (totalDuration < TRIPLE_TAP_WINDOW_MS) {
                    lastDetectionTime = timestamp
                    transitionState(BackTapDetectorState.TRIPLE_TAP, "Sequence 3/3 matched")
                    Log.i(DEBUG_TAG, "SEQUENCE_COMPLETED: Tap 3/3 at ${timestamp}ms | Total Duration: ${totalDuration}ms | Timestamps: $tapTimestamps")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("VALID_TAP 3/3 (Gap: ${gap}ms)")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("SEQUENCE_COMPLETED (${totalDuration}ms)")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("TRIPLE_TAP")

                    dumpRollingBufferToLogcat("TRIPLE_TAP")
                    tapTimestamps.clear()
                    sequenceStartTime = 0L
                    onTripleTap()
                } else {
                    Log.w(DEBUG_TAG, "SEQUENCE_RESET: Total duration ${totalDuration}ms exceeded ${TRIPLE_TAP_WINDOW_MS}ms window.")
                    com.example.voicecontrol.manager.BackTapDebugManager.logEvent("SEQUENCE_RESET (Duration ${totalDuration}ms > 1000ms)")
                    dumpRollingBufferToLogcat("FALSE_TRIGGER")
                    tapTimestamps.clear()
                    sequenceStartTime = 0L
                    transitionState(BackTapDetectorState.IDLE, "Sequence window expired")
                }
            }
            else -> {
                // Defensive reset to prevent count corruption (> 3)
                Log.w(DEBUG_TAG, "SEQUENCE_RESET: State corruption recovery. Resetting sequence.")
                tapTimestamps.clear()
                sequenceStartTime = 0L
                transitionState(BackTapDetectorState.IDLE, "State corruption recovery")
            }
        }
    }

    private fun dumpRollingBufferToLogcat(triggerReason: String) {
        Log.i(DEBUG_TAG, "========== BEGIN ROLLING BUFFER DUMP (500 SAMPLES) [$triggerReason] ==========")
        for ((index, sample) in rollingBuffer.withIndex()) {
            Log.i(DEBUG_TAG, "SAMPLE [#%03d] %s".format(index + 1, sample.toCsvString()))
        }
        Log.i(DEBUG_TAG, "========== END ROLLING BUFFER DUMP (500 SAMPLES) ==========")
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
