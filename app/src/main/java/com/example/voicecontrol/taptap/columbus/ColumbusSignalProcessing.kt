package com.example.voicecontrol.taptap.columbus

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-Pass 3-Component Filter for 3D accelerometer sensor vectors.
 */
class Highpass3Component(private val para: Float = 0.8f) {
    private var lastX = 0f; private var lastY = 0f; private var lastZ = 0f
    private var hpX = 0f; private var hpY = 0f; private var hpZ = 0f

    fun update(x: Float, y: Float, z: Float): FloatArray {
        hpX = para * (hpX + x - lastX)
        hpY = para * (hpY + y - lastY)
        hpZ = para * (hpZ + z - lastZ)

        lastX = x; lastY = y; lastZ = z
        return floatArrayOf(hpX, hpY, hpZ)
    }

    fun reset() {
        lastX = 0f; lastY = 0f; lastZ = 0f
        hpX = 0f; hpY = 0f; hpZ = 0f
    }
}

/**
 * Resample helper for 50Hz constant rate sampling.
 */
class ColumbusResample(private val targetIntervalNs: Long = 20_000_000L) {
    private var lastTimestampNs = 0L

    fun update(timestampNs: Long): Boolean {
        if (timestampNs - lastTimestampNs >= targetIntervalNs) {
            lastTimestampNs = timestampNs
            return true
        }
        return false
    }

    fun reset() {
        lastTimestampNs = 0L
    }
}

/**
 * Peak Detector for dynamic Jerk derivative thresholds.
 */
class PeakDetector(
    private val minPeakThreshold: Float = 0.35f,
    private val minZDominance: Float = 0.70f
) {
    fun isPeak(hpX: Float, hpY: Float, hpZ: Float, jerk: Float): Boolean {
        val mag = sqrt(hpX * hpX + hpY * hpY + hpZ * hpZ)
        val zRatio = if (mag > 0.01f) abs(hpZ) / mag else 0f
        return jerk >= minPeakThreshold && zRatio >= minZDominance
    }
}
