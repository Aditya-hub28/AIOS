package com.example.voicecontrol.taptap.samsung

import android.hardware.SensorEvent
import kotlin.math.abs
import kotlin.math.sqrt

class SensorEventProcessor(
    private val onTapDetected: (isTriple: Boolean) -> Unit
) {
    private val filter = HighPassFilter(0.85f)
    private val resampler = SamsungResample(20_000_000L) // 50Hz
    private val sampleQueue = DataQueue<Sample>(50)

    private var lastTapTime = 0L
    private var tapCount = 0

    fun processEvent(event: SensorEvent) {
        val timestamp = event.timestamp
        if (!resampler.shouldSample(timestamp)) return

        val rawX = event.values[0]
        val rawY = event.values[1]
        val rawZ = event.values[2]

        val filteredPoint = filter.filter(rawX, rawY, rawZ)
        sampleQueue.add(Sample(filteredPoint, timestamp))

        val vectorMag = sqrt(filteredPoint.x * filteredPoint.x + filteredPoint.y * filteredPoint.y + filteredPoint.z * filteredPoint.z)
        val zRatio = if (vectorMag > 0.01f) abs(filteredPoint.z) / vectorMag else 0f

        val nowMs = System.currentTimeMillis()
        if (vectorMag >= 0.40f && zRatio >= 0.75f) {
            if (nowMs - lastTapTime > 80L) {
                if (nowMs - lastTapTime > 500L) {
                    tapCount = 0
                }
                tapCount++
                lastTapTime = nowMs

                if (tapCount == 2) {
                    onTapDetected(false)
                } else if (tapCount == 3) {
                    onTapDetected(true)
                    tapCount = 0
                }
            }
        }
    }

    fun reset() {
        filter.reset()
        resampler.reset()
        sampleQueue.clear()
        tapCount = 0
        lastTapTime = 0L
    }
}
