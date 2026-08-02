package com.example.voicecontrol.taptap.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * Settings Manager for TapTap configuration (Actions, Gates, Sensitivity, and Feedback preferences).
 */
class TapTapSettings(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "taptap_preferences"

        const val KEY_TAPTAP_ENABLED = "taptap_enabled"
        const val KEY_DOUBLE_TAP_ACTION = "double_tap_action"
        const val KEY_TRIPLE_TAP_ACTION = "triple_tap_action"
        const val KEY_SENSITIVITY = "taptap_sensitivity"
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
        const val KEY_GATE_SCREEN_OFF = "gate_screen_off"
        const val KEY_GATE_CAMERA = "gate_camera"
        const val KEY_GATE_CALL = "gate_call"

        const val ACTION_TOGGLE_VOICE_CONTROL = "toggle_voice_control"
        const val ACTION_FLASHLIGHT = "flashlight"
        const val ACTION_SCREENSHOT = "screenshot"
        const val ACTION_LAUNCH_APP = "launch_app"
        const val ACTION_QUICK_SETTINGS = "quick_settings"
        const val ACTION_MEDIA_PLAY_PAUSE = "media_play_pause"
        const val ACTION_LOCK_SCREEN = "lock_screen"
    }

    var isTapTapEnabled: Boolean
        get() = prefs.getBoolean(KEY_TAPTAP_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_TAPTAP_ENABLED, value).apply()

    var doubleTapAction: String
        get() = prefs.getString(KEY_DOUBLE_TAP_ACTION, ACTION_TOGGLE_VOICE_CONTROL) ?: ACTION_TOGGLE_VOICE_CONTROL
        set(value) = prefs.edit().putString(KEY_DOUBLE_TAP_ACTION, value).apply()

    var tripleTapAction: String
        get() = prefs.getString(KEY_TRIPLE_TAP_ACTION, ACTION_SCREENSHOT) ?: ACTION_SCREENSHOT
        set(value) = prefs.edit().putString(KEY_TRIPLE_TAP_ACTION, value).apply()

    var isHapticFeedbackEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, value).apply()

    var isScreenOffGateEnabled: Boolean
        get() = prefs.getBoolean(KEY_GATE_SCREEN_OFF, true)
        set(value) = prefs.edit().putBoolean(KEY_GATE_SCREEN_OFF, value).apply()

    var isCameraGateEnabled: Boolean
        get() = prefs.getBoolean(KEY_GATE_CAMERA, true)
        set(value) = prefs.edit().putBoolean(KEY_GATE_CAMERA, value).apply()

    var isCallGateEnabled: Boolean
        get() = prefs.getBoolean(KEY_GATE_CALL, true)
        set(value) = prefs.edit().putBoolean(KEY_GATE_CALL, value).apply()
}
