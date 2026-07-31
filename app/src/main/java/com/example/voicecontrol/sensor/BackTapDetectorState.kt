package com.example.voicecontrol.sensor

/**
 * State Machine Enum for Back Tap Detection.
 */
enum class BackTapDetectorState {
    IDLE,
    POSSIBLE_TAP,
    VALID_TAP,
    TRIPLE_TAP
}
