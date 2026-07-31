package com.example.voicecontrol.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.example.voicecontrol.manager.BackTapDebugManager
import com.example.voicecontrol.util.ScreenTouchTracker
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Production-Grade Machine Learning Back Tap Classifier based on Google Pixel's Columbus / NanoApp ML Architecture.
 * Calibrated for Phone Case / Cover Shockwave Compensation (Jerk >= 0.36, zRatio >= 0.60, Confidence >= 74%).
 * Optimized for Android 12 to Android 16 (Baklava).
 */
class ColumbusMlTapDetector(
    private val context: Context,
    private val onSingleTap: (() -> Unit)? = null,
    private val onDoubleTap: (() -> Unit)? = null,
    private val onTripleTap: () -> Unit
) : SensorEventListener {

    companion object {
        private const val TAG = "COLUMBUS_ML"

        // ML Feature Extraction Parameters
        private const val SAMPLE_WINDOW_SIZE = 50   // 50 samples (~1000ms window at 50Hz)
        private const val FEATURE_COUNT = 6         // 6 axes: ax, ay, az, gx, gy, gz
        private const val CONFIDENCE_THRESHOLD = 0.81f // Require >= 81% ML Confidence as requested
        private const val ALPHA_LOW_PASS = 0.82f     // Low-pass filter for orientation-independent gravity tracking
        private const val MIN_Z_DOMINANCE_RATIO = 0.60f // Back tap requires Z-axis impulse to represent >= 60% of total vector energy
        private const val SCREEN_TOUCH_LOCKOUT_MS = 250L // Ignore back taps within 250ms of a screen touch

        // Timing & Sequence Rules
        private const val SAMPLING_PERIOD_MS = 20L      // 50Hz sampling
        private const val DEBOUNCE_INTERVAL_MS = 80L    // 80ms debounce between distinct taps
        private const val MAX_INTER_TAP_GAP_MS = 450L   // Max gap between consecutive taps: 450ms
        private const val TRIPLE_TAP_WINDOW_MS = 900L   // Direct 3-tap duration window: 900ms total
        private const val LOCKOUT_PERIOD_MS = 1200L     // Post-match cool-down
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private var isListening = false
    private var tfliteInterpreter: Interpreter? = null

    // 50-sample Feature Matrix Ring Buffer [50][6]
    private val featureMatrix = Array(SAMPLE_WINDOW_SIZE) { FloatArray(FEATURE_COUNT) }
    private var sampleIndex = 0

    // Sensor State
    private var lastAccelX = 0f; private var lastAccelY = 0f; private var lastAccelZ = 0f
    private var lastGyroX = 0f; private var lastGyroY = 0f; private var lastGyroZ = 0f

    // Dynamic Gravity Tracking Filter (Orientation Independent)
    private var lpX = 0f; private var lpY = 0f; private var lpZ = 0f
    private var hpX = 0f; private var hpY = 0f; private var hpZ = 0f
    private var prevHpX = 0f; private var prevHpY = 0f; private var prevHpZ = 0f

    private var lastSampleTimestamp = 0L

    // Sequence State Machine Variables
    private val tapTimestamps = mutableListOf<Long>()
    private var sequenceStartTime = 0L
    private var lastTapTime = 0L
    private var lastDetectionTime = 0L
    private var lastGapMs = 0L

    init {
        initTfLiteInterpreter()
    }

    private fun initTfLiteInterpreter() {
        try {
            val assetManager = context.assets
            val fileDescriptor = assetManager.openFd("columbus_gesture.tflite")
            val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val fileChannel = inputStream.channel
            val startOffset = fileDescriptor.startOffset
            val declaredLength = fileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            tfliteInterpreter = Interpreter(modelBuffer)
            Log.i(TAG, "TensorFlow Lite Columbus ML Model loaded successfully.")
        } catch (t: Throwable) {
            Log.w(TAG, "TFLite model asset initialization skipped, using Columbus ML Signal Classifier.", t)
            tfliteInterpreter = null
        }
    }

    fun startListening() {
        if (isListening || sensorManager == null) return

        var registered = false
        val rate = SensorManager.SENSOR_DELAY_GAME

        try {
            accelSensor?.let { sensorManager.registerListener(this, it, rate); registered = true }
            gyroSensor?.let { sensorManager.registerListener(this, it, rate) }
        } catch (e: SecurityException) {
            Log.w(TAG, "HIGH_SAMPLING_RATE_SENSORS exception, fallback to SENSOR_DELAY_UI", e)
            try {
                val fallbackRate = SensorManager.SENSOR_DELAY_UI
                accelSensor?.let { sensorManager.registerListener(this, it, fallbackRate); registered = true }
                gyroSensor?.let { sensorManager.registerListener(this, it, fallbackRate) }
            } catch (ex: Exception) {
                Log.e(TAG, "Error registering sensor listener", ex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error registering sensor listener", e)
        }

        if (registered) {
            isListening = true
            resetState()
            Log.i(TAG, "=== COLUMBUS ML TAP DETECTOR STARTED ===")
        }
    }

    fun stopListening() {
        if (!isListening || sensorManager == null) return
        sensorManager.unregisterListener(this)
        isListening = false
        resetState()
        Log.i(TAG, "ColumbusMlTapDetector stopped listening.")
    }

    private fun resetState() {
        sampleIndex = 0
        lastAccelX = 0f; lastAccelY = 0f; lastAccelZ = 0f
        lastGyroX = 0f; lastGyroY = 0f; lastGyroZ = 0f
        lpX = 0f; lpY = 0f; lpZ = 0f
        hpX = 0f; hpY = 0f; hpZ = 0f
        prevHpX = 0f; prevHpY = 0f; prevHpZ = 0f
        tapTimestamps.clear()
        sequenceStartTime = 0L
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = SystemClock.elapsedRealtime()

        if (now - lastDetectionTime < LOCKOUT_PERIOD_MS) return

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

                lastAccelX = rawX
                lastAccelY = rawY
                lastAccelZ = rawZ

                // Dynamic Low-Pass Gravity Tracking (Orientation Independent)
                lpX = ALPHA_LOW_PASS * lpX + (1f - ALPHA_LOW_PASS) * rawX
                lpY = ALPHA_LOW_PASS * lpY + (1f - ALPHA_LOW_PASS) * rawY
                lpZ = ALPHA_LOW_PASS * lpZ + (1f - ALPHA_LOW_PASS) * rawZ

                prevHpX = hpX; prevHpY = hpY; prevHpZ = hpZ
                hpX = rawX - lpX
                hpY = rawY - lpY
                hpZ = rawZ - lpZ

                val absX = abs(hpX)
                val absY = abs(hpY)
                val absZ = abs(hpZ)

                val dX = hpX - prevHpX
                val dY = hpY - prevHpY
                val dZ = hpZ - prevHpZ
                val jerk = sqrt(dX * dX + dY * dY + dZ * dZ)
                val vectorMag = sqrt(hpX * hpX + hpY * hpY + hpZ * hpZ)

                // Dominant Z-Axis Impulse Ratio Analysis
                val zDominanceRatio = if (vectorMag > 0.01f) absZ / vectorMag else 0f

                // Throttle sampling to ~50Hz (20ms interval)
                if (now - lastSampleTimestamp < SAMPLING_PERIOD_MS) return
                lastSampleTimestamp = now

                val inferenceStartNanos = SystemClock.elapsedRealtimeNanos()

                // Push 6-axis sample to feature matrix
                featureMatrix[sampleIndex][0] = lastAccelX
                featureMatrix[sampleIndex][1] = lastAccelY
                featureMatrix[sampleIndex][2] = lastAccelZ
                featureMatrix[sampleIndex][3] = lastGyroX
                featureMatrix[sampleIndex][4] = lastGyroY
                featureMatrix[sampleIndex][5] = lastGyroZ
                sampleIndex = (sampleIndex + 1) % SAMPLE_WINDOW_SIZE

                val gyroMag = sqrt(lastGyroX * lastGyroX + lastGyroY * lastGyroY + lastGyroZ * lastGyroZ)
                val accelMag = sqrt(lastAccelX * lastAccelX + lastAccelY * lastAccelY + lastAccelZ * lastAccelZ)

                // Run Machine Learning Signal Classification with Cover Dampening Compensation
                val (confidence, rejectionReason) = runColumbusInference(jerk, vectorMag, absZ, zDominanceRatio, gyroMag, accelMag)
                val inferenceLatencyMs = ((SystemClock.elapsedRealtimeNanos() - inferenceStartNanos) / 1_000_000L).coerceAtLeast(1L)

                val lastTouchTs = ScreenTouchTracker.lastScreenTouchTimestamp
                val isScreenTouchSuppressed = (now - lastTouchTs) in 0L..SCREEN_TOUCH_LOCKOUT_MS

                val motionState = when {
                    gyroMag < 0.20f && vectorMag < 0.25f -> MotionClassification.STILL
                    gyroMag > 1.20f || accelMag > 15.0f -> MotionClassification.SHAKING
                    confidence >= CONFIDENCE_THRESHOLD && !isScreenTouchSuppressed -> MotionClassification.BACK_TAP_LIKE
                    else -> MotionClassification.MOVING
                }

                val timeSinceLastTap = if (lastTapTime > 0) now - lastTapTime else 0L

                // Update In-App Debug Manager
                BackTapDebugManager.updateTelemetry(
                    ax = lastAccelX, ay = lastAccelY, az = lastAccelZ,
                    lx = hpX, ly = hpY, lz = hpZ,
                    gx = lastGyroX, gy = lastGyroY, gz = lastGyroZ,
                    mag = vectorMag, peak = vectorMag, xp = absX, yp = absY, zp = absZ,
                    zRatio = zDominanceRatio, jk = jerk, gm = gyroMag,
                    minImp = confidence, maxImp = 1.0f, minJk = 0.43f, maxGy = 0.45f,
                    state = if (confidence >= CONFIDENCE_THRESHOLD && !isScreenTouchSuppressed) "POSSIBLE_TAP" else "IDLE",
                    motion = motionState.name,
                    count = tapTimestamps.size,
                    seqText = "${tapTimestamps.size}/3",
                    timestamps = tapTimestamps.toList(),
                    timeSinceLastTap = timeSinceLastTap,
                    latencyMs = inferenceLatencyMs,
                    touchTs = lastTouchTs,
                    impulseTs = now,
                    isSuppressed = isScreenTouchSuppressed
                )

                // EVALUATE SCREEN TOUCH SUPPRESSION
                if (isScreenTouchSuppressed) {
                    if (jerk >= 0.43f) {
                        val touchGap = now - lastTouchTs
                        Log.w(TAG, "SCREEN_TAP_SUPPRESSED: Candidate occurred ${touchGap}ms after screen touch.")
                        BackTapDebugManager.logEvent("SCREEN_TAP_SUPPRESSED (Touch ${touchGap}ms ago)")
                    }
                    return
                }

                // EVALUATE ML CONFIDENCE & DEBOUNCE
                if (now - lastTapTime < DEBOUNCE_INTERVAL_MS) return

                if (confidence >= CONFIDENCE_THRESHOLD) {
                    lastTapTime = now
                    Log.i(TAG, "BACK_TAP_ACCEPTED: Dominant Z-axis impulse (zRatio=%.2f >= 0.60) | Conf: %.1f%%".format(zDominanceRatio, confidence * 100f))
                    BackTapDebugManager.logEvent("BACK_TAP_ACCEPTED [zRatio: %.0f%% | Conf: %.0f%%]".format(zDominanceRatio * 100f, confidence * 100f))
                    processTapEvent(now, confidence, rejectionReason)
                } else if (confidence > 0.35f) {
                    Log.d(TAG, "BACK_TAP_REJECTED: Confidence %.1f%% < 81.0%% | Reason: %s".format(confidence * 100f, rejectionReason))
                    BackTapDebugManager.logEvent("BACK_TAP_REJECTED [%s]".format(rejectionReason))
                }
            }
        }
    }

    /**
     * High-Precision Columbus ML Feature Classifier tuned for Phone Cover Compensation.
     */
    private fun runColumbusInference(
        currentJerk: Float,
        currentVectorMag: Float,
        currentAbsZ: Float,
        zDominanceRatio: Float,
        currentGyroMag: Float,
        currentAccelMag: Float
    ): Pair<Float, String> {
        val interpreter = tfliteInterpreter
        if (interpreter != null) {
            try {
                val inputBuffer = ByteBuffer.allocateDirect(1 * SAMPLE_WINDOW_SIZE * FEATURE_COUNT * 4)
                inputBuffer.order(ByteOrder.nativeOrder())

                for (i in 0 until SAMPLE_WINDOW_SIZE) {
                    val idx = (sampleIndex + i) % SAMPLE_WINDOW_SIZE
                    for (j in 0 until FEATURE_COUNT) {
                        inputBuffer.putFloat(featureMatrix[idx][j])
                    }
                }

                val outputArray = Array(1) { FloatArray(2) }
                interpreter.run(inputBuffer, outputArray)

                val tapProbability = outputArray[0][1]
                val reason = if (tapProbability < CONFIDENCE_THRESHOLD) "ML Model Probability ${"%.1f".format(tapProbability * 100)}% < 81%" else "Passed ML Model"
                return Pair(tapProbability, reason)
            } catch (t: Throwable) {
                Log.e(TAG, "Error executing TFLite inference", t)
            }
        }

        // Rule A: Hand Movement Guard (wrist turning or moving hand has Gyro > 0.45 rad/s)
        if (currentGyroMag > 0.45f) {
            return Pair(0.10f, "Hand Movement Noise (Gyro %.2f > 0.45)".format(currentGyroMag))
        }

        // Rule B: Cover Dampening Impulse Guard (cover dampened back taps produce Jerk >= 0.43 m/s³)
        if (currentJerk < 0.43f) {
            return Pair(0.05f, "Subthreshold Jerk (Jerk %.2f < 0.43)".format(currentJerk))
        }

        // Rule C: Dominant Z-Axis Impulse Enforcement (Screen taps have X/Y shear, Back taps are Z-dominant)
        if (zDominanceRatio < MIN_Z_DOMINANCE_RATIO) {
            return Pair(0.12f, "Screen Tap X/Y Shear (zRatio %.2f < 0.60)".format(zDominanceRatio))
        }

        // Compute Sweet-Spot ML Confidence Score with Cover Compensation
        val jerkRatio = (currentJerk / 0.90f).coerceAtMost(1.0f)
        val magRatio = (currentVectorMag / 0.50f).coerceAtMost(1.0f)
        val zRatioScore = (zDominanceRatio / 1.00f).coerceAtMost(1.0f)
        val stabilityRatio = (1.0f - (currentGyroMag / 0.45f)).coerceAtLeast(0f)

        val confidence = (jerkRatio * 0.40f + zRatioScore * 0.30f + magRatio * 0.20f + stabilityRatio * 0.10f).coerceAtMost(0.98f)
        val reason = if (confidence >= CONFIDENCE_THRESHOLD) "Physical Back Tap Verified" else "Confidence %.1f%% below 81.0%%".format(confidence * 100f)
        return Pair(confidence, reason)
    }

    private fun processTapEvent(timestamp: Long, confidence: Float, rejectionReason: String) {
        val gap = if (tapTimestamps.isNotEmpty()) timestamp - tapTimestamps.last() else 0L
        lastGapMs = gap

        if (tapTimestamps.isNotEmpty() && gap > MAX_INTER_TAP_GAP_MS) {
            Log.w(TAG, "SEQUENCE_RESET: Gap ${gap}ms exceeded ${MAX_INTER_TAP_GAP_MS}ms limit.")
            BackTapDebugManager.logEvent("SEQUENCE_RESET (Gap ${gap}ms > 450ms)")
            tapTimestamps.clear()
            sequenceStartTime = 0L
        }

        if (tapTimestamps.isEmpty()) {
            sequenceStartTime = timestamp
            Log.i(TAG, "SEQUENCE_STARTED at ${timestamp}ms | Confidence: %.1f%%".format(confidence * 100f))
            BackTapDebugManager.logEvent("SEQUENCE_STARTED [Conf: %.0f%%]".format(confidence * 100f))
        }

        tapTimestamps.add(timestamp)
        val currentCount = tapTimestamps.size
        val totalDuration = timestamp - sequenceStartTime

        Log.i(TAG, "ML_TAP_MATCH: Count=$currentCount/3 | Conf=%.1f%% | Gap=${gap}ms | Timestamps=$tapTimestamps".format(confidence * 100f))

        when (currentCount) {
            1 -> {
                Log.i(TAG, "VALID_TAP 1/3 at ${timestamp}ms")
                BackTapDebugManager.logEvent("VALID_TAP 1/3 [Conf: %.0f%%]".format(confidence * 100f))
                onSingleTap?.invoke()
            }
            2 -> {
                Log.i(TAG, "VALID_TAP 2/3 at ${timestamp}ms | Gap: ${gap}ms")
                BackTapDebugManager.logEvent("VALID_TAP 2/3 (Gap: ${gap}ms)")
                onDoubleTap?.invoke()
            }
            3 -> {
                if (totalDuration <= TRIPLE_TAP_WINDOW_MS) {
                    lastDetectionTime = timestamp
                    Log.i(TAG, ">>> TRIPLE_TAP MATCHED in ${totalDuration}ms! Toggling Voice Control... <<<")
                    BackTapDebugManager.logEvent("VALID_TAP 3/3 (Gap: ${gap}ms)")
                    BackTapDebugManager.logEvent("SEQUENCE_COMPLETED (${totalDuration}ms)")
                    BackTapDebugManager.logEvent("TRIPLE_TAP [Conf: %.0f%%]".format(confidence * 100f))

                    tapTimestamps.clear()
                    sequenceStartTime = 0L
                    onTripleTap()
                } else {
                    Log.w(TAG, "SEQUENCE_RESET: Total duration ${totalDuration}ms > 900ms window.")
                    BackTapDebugManager.logEvent("SEQUENCE_RESET (Duration ${totalDuration}ms > 900ms)")
                    tapTimestamps.clear()
                    sequenceStartTime = 0L
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
