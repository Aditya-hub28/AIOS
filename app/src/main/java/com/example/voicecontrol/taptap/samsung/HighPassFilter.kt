package com.example.voicecontrol.taptap.samsung

class HighPassFilter(private val alpha: Float = 0.8f) {
    private var lastX = 0f
    private var lastY = 0f
    private var lastZ = 0f

    private var filteredX = 0f
    private var filteredY = 0f
    private var filteredZ = 0f

    fun filter(rawX: Float, rawY: Float, rawZ: Float): Point3f {
        filteredX = alpha * (filteredX + rawX - lastX)
        filteredY = alpha * (filteredY + rawY - lastY)
        filteredZ = alpha * (filteredZ + rawZ - lastZ)

        lastX = rawX
        lastY = rawY
        lastZ = rawZ

        return Point3f(filteredX, filteredY, filteredZ)
    }

    fun reset() {
        lastX = 0f; lastY = 0f; lastZ = 0f
        filteredX = 0f; filteredY = 0f; filteredZ = 0f
    }
}

class SamsungResample(private val targetIntervalNs: Long = 20_000_000L) {
    private var lastResampledTimestamp = 0L

    fun shouldSample(timestampNs: Long): Boolean {
        if (timestampNs - lastResampledTimestamp >= targetIntervalNs) {
            lastResampledTimestamp = timestampNs
            return true
        }
        return false
    }

    fun reset() {
        lastResampledTimestamp = 0L
    }
}
