package com.example.voicecontrol.util

import android.os.SystemClock
import android.util.Log

/**
 * Singleton repository tracking screen touch timestamps across MainActivity and AccessibilityService.
 * Used to suppress false back tap candidates occurring within 250ms of a real screen touch.
 */
object ScreenTouchTracker {

    private const val TAG = "ScreenTouchTracker"
    private const val TOUCH_SUPPRESSION_WINDOW_MS = 250L

    @Volatile
    var lastScreenTouchTimestamp: Long = 0L
        private set

    /**
     * Called on MotionEvent.ACTION_DOWN from MainActivity dispatchTouchEvent or AccessibilityService.
     */
    fun onScreenTouch() {
        lastScreenTouchTimestamp = SystemClock.elapsedRealtime()
    }

    /**
     * Checks if a back tap candidate timestamp falls within the 250ms screen touch lockout window.
     */
    fun isTouchSuppressed(candidateTimestamp: Long): Boolean {
        val gap = candidateTimestamp - lastScreenTouchTimestamp
        val isSuppressed = gap in 0L..TOUCH_SUPPRESSION_WINDOW_MS
        if (isSuppressed) {
            Log.w(TAG, "SCREEN_TAP_SUPPRESSED: Back tap candidate within ${gap}ms of screen touch.")
        }
        return isSuppressed
    }
}
