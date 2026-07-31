package com.example.voicecontrol.util

import android.os.SystemClock
import android.util.Log

/**
 * Singleton repository tracking screen touch and keyboard typing timestamps across MainActivity and AccessibilityService.
 * Suppresses false back tap candidates caused by front screen touches and keypad haptic vibrations.
 */
object ScreenTouchTracker {

    private const val TAG = "ScreenTouchTracker"
    private const val TOUCH_SUPPRESSION_WINDOW_MS = 400L
    private const val KEYBOARD_TYPING_LOCKOUT_MS = 600L // 600ms lockout window for keyboard typing haptics

    @Volatile
    var lastScreenTouchTimestamp: Long = 0L
        private set

    @Volatile
    var lastKeyboardTypingTimestamp: Long = 0L
        private set

    /**
     * Called on screen touch events from MainActivity dispatchTouchEvent or AccessibilityService.
     */
    fun onScreenTouch() {
        lastScreenTouchTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * Called on keyboard keypresses, text edits, and IME accessibility events.
     */
    fun onKeyboardTyping() {
        val now = SystemClock.elapsedRealtime()
        lastKeyboardTypingTimestamp = now
        lastScreenTouchTimestamp = now
    }

    /**
     * Checks if a back tap candidate timestamp falls within the 400ms screen touch lockout window.
     */
    fun isTouchSuppressed(candidateTimestamp: Long): Boolean {
        val gap = candidateTimestamp - lastScreenTouchTimestamp
        val isSuppressed = gap in 0L..TOUCH_SUPPRESSION_WINDOW_MS
        if (isSuppressed) {
            Log.w(TAG, "SCREEN_TAP_SUPPRESSED: Back tap candidate within ${gap}ms of screen touch.")
        }
        return isSuppressed
    }

    /**
     * Checks if typing/keypad activity occurred within the 600ms keyboard lockout window.
     */
    fun isKeyboardTypingSuppressed(candidateTimestamp: Long): Boolean {
        val gap = candidateTimestamp - lastKeyboardTypingTimestamp
        val isSuppressed = gap in 0L..KEYBOARD_TYPING_LOCKOUT_MS
        if (isSuppressed) {
            Log.w(TAG, "KEYBOARD_HAPTIC_SUPPRESSED: Back tap candidate within ${gap}ms of keypad typing.")
        }
        return isSuppressed
    }
}
