package com.example.voicecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.voicecontrol.manager.AccessibilityCommandManager
import com.example.voicecontrol.manager.GestureType

/**
 * Dedicated Accessibility Service for executing Android global actions (Home, Back, Recent Apps)
 * and iPhone-style gesture navigation commands (Swipe Up, Swipe Down, Swipe Left, Swipe Right)
 * using dynamic screen dimensions and dispatchGesture().
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            Log.i(TAG, "VoiceAccessibilityService connected to System Accessibility Framework.")
            AccessibilityCommandManager.registerService(this)
        } catch (e: Exception) {
            Log.e(TAG, "Error during onServiceConnected", e)
        }
    }

    /**
     * Performs an iPhone-style gesture navigation swipe (Swipe Up, Swipe Down, Swipe Left, Swipe Right)
     * using dynamic screen dimensions calculated from runtime resources.
     * @param gestureType Gesture direction enum.
     * @return True if gesture was successfully dispatched to system looper.
     */
    fun performSwipeGesture(gestureType: GestureType): Boolean {
        return try {
            val metrics: DisplayMetrics = resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()

            val startX: Float
            val startY: Float
            val endX: Float
            val endY: Float

            when (gestureType) {
                GestureType.SWIPE_UP -> {
                    // Upward finger swipe: bottom (75% height) to top (25% height) -> Feed moves down
                    startX = width * 0.5f
                    startY = height * 0.75f
                    endX = width * 0.5f
                    endY = height * 0.25f
                }
                GestureType.SWIPE_DOWN -> {
                    // Downward finger swipe: top (25% height) to bottom (75% height) -> Feed moves up
                    startX = width * 0.5f
                    startY = height * 0.25f
                    endX = width * 0.5f
                    endY = height * 0.75f
                }
                GestureType.SWIPE_LEFT -> {
                    // Leftward finger swipe: right (85% width) to left (15% width)
                    startX = width * 0.85f
                    startY = height * 0.5f
                    endX = width * 0.15f
                    endY = height * 0.5f
                }
                GestureType.SWIPE_RIGHT -> {
                    // Rightward finger swipe: left (15% width) to right (85% width)
                    startX = width * 0.15f
                    startY = height * 0.5f
                    endX = width * 0.85f
                    endY = height * 0.5f
                }
            }

            Log.i(TAG, "Dispatching $gestureType: ($startX, $startY) -> ($endX, $endY) [Screen: ${width.toInt()}x${height.toInt()}]")

            val swipePath = Path().apply {
                moveTo(startX, startY)
                lineTo(endX, endY)
            }

            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0L, 250L)
            gestureBuilder.addStroke(strokeDescription)

            val success = dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    super.onCompleted(gestureDescription)
                    Log.i(TAG, "Successfully completed gesture $gestureType")
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    super.onCancelled(gestureDescription)
                    Log.w(TAG, "Gesture $gestureType was cancelled by system")
                }
            }, null)

            success
        } catch (e: Exception) {
            Log.e(TAG, "Error performing gesture $gestureType", e)
            false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Kept lightweight to maximize responsiveness and prevent system event queue bottlenecks
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceAccessibilityService interrupted by system.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "VoiceAccessibilityService unbound.")
        AccessibilityCommandManager.unregisterService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VoiceAccessibilityService destroyed.")
        AccessibilityCommandManager.unregisterService()
    }
}
