package com.example.voicecontrol.sensor

/**
 * Data class representing a single timestamped sensor telemetry snapshot.
 * Stores raw and calculated values for rolling 500-sample buffer dumps and CSV logging.
 */
data class SensorSample(
    val timestamp: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val linX: Float,
    val linY: Float,
    val linZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float,
    val magnitude: Float,
    val zPeak: Float,
    val jerk: Float,
    val gyroMag: Float,
    val stateName: String,
    val motionName: String
) {
    /**
     * Formats sample into CSV log format required by Requirement 7.
     */
    fun toCsvString(): String =
        "DEBUG_DATA: time=$timestamp, accelX=%.2f, accelY=%.2f, accelZ=%.2f, linZ=%.2f, jerk=%.2f, gyro=%.2f".format(
            accelX, accelY, accelZ, linZ, jerk, gyroMag
        )
}
