package com.example.voicecontrol.taptap.samsung

data class Point3f(
    var x: Float = 0f,
    var y: Float = 0f,
    var z: Float = 0f
)

data class Sample(
    val point: Point3f,
    val timestamp: Long
)

data class Slope(
    val slopeX: Float = 0f,
    val slopeY: Float = 0f,
    val slopeZ: Float = 0f
)
