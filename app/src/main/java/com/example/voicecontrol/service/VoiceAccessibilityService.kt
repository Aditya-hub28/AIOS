package com.example.voicecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.voicecontrol.manager.AccessibilityCommandManager
import com.example.voicecontrol.manager.GestureType
import com.example.voicecontrol.util.NodeSearchHelper

/**
 * Dedicated Accessibility Service for executing Android global actions (Home, Back, Recent Apps),
 * text-based UI clicking ("Tap <element name>"), and controlled gesture navigation across all apps.
 * Hardened against crashes to maintain continuous system stability.
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
     * Finds and clicks a UI element by text or content description ("Tap Search", "Tap Install").
     * @param targetText Spoken element name to match and click.
     * @return True if a matching element was found and clicked.
     */
    fun performClickByText(targetText: String): Boolean {
        return try {
            Log.i(TAG, "Executing performClickByText for target: '$targetText'")
            NodeSearchHelper.searchAndClick(this, targetText)
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click by text '$targetText'", e)
            false
        }
    }

    /**
     * Performs gesture navigation or controlled node-based scrolling.
     * Prioritizes AccessibilityNodeInfo node actions (ACTION_SCROLL_FORWARD / ACTION_SCROLL_BACKWARD)
     * for page-by-page scrolling without skipping items.
     * @param gestureType Gesture direction enum.
     * @return True if node action or gesture swipe was successfully executed.
     */
    fun performSwipeGesture(gestureType: GestureType): Boolean {
        return try {
            when (gestureType) {
                GestureType.SWIPE_UP -> {
                    // 1. First attempt page-by-page controlled node action scroll forward
                    val nodeScrolled = performNodeActionScroll(isForward = true)
                    if (nodeScrolled) {
                        Log.i(TAG, "Controlled vertical scroll (Swipe Up -> ACTION_SCROLL_FORWARD) succeeded via AccessibilityNodeInfo.")
                        return true
                    }
                    // 2. Fallback: Short controlled vertical swipe (60% -> 40% height)
                    Log.i(TAG, "No scrollable node action available. Executing tuned short vertical swipe fallback (Swipe Up).")
                    performVerticalSwipeFallback(isForward = true)
                }
                GestureType.SWIPE_DOWN -> {
                    // 1. First attempt page-by-page controlled node action scroll backward
                    val nodeScrolled = performNodeActionScroll(isForward = false)
                    if (nodeScrolled) {
                        Log.i(TAG, "Controlled vertical scroll (Swipe Down -> ACTION_SCROLL_BACKWARD) succeeded via AccessibilityNodeInfo.")
                        return true
                    }
                    // 2. Fallback: Short controlled vertical swipe (40% -> 60% height)
                    Log.i(TAG, "No scrollable node action available. Executing tuned short vertical swipe fallback (Swipe Down).")
                    performVerticalSwipeFallback(isForward = false)
                }
                GestureType.SWIPE_LEFT -> {
                    performHorizontalSwipe(isLeft = true)
                }
                GestureType.SWIPE_RIGHT -> {
                    performHorizontalSwipe(isLeft = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing swipe gesture $gestureType", e)
            false
        }
    }

    /**
     * Traverses the active window node tree to find and execute controlled node action scrolling.
     */
    private fun performNodeActionScroll(isForward: Boolean): Boolean {
        val rootNode = rootInActiveWindow ?: return false
        try {
            val targetNode = findScrollableNode(rootNode, isForward)
            if (targetNode != null) {
                val action = if (isForward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val success = targetNode.performAction(action)
                try { targetNode.recycle() } catch (_: Exception) {}
                try { rootNode.recycle() } catch (_: Exception) {}

                if (success) {
                    return true
                }
            } else {
                try { rootNode.recycle() } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing node action scroll", e)
        }
        return false
    }

    /**
     * Recursively traverses node tree to find the active vertical scrollable container.
     * Explicitly filters out horizontal containers (HorizontalScrollView, ViewPager).
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo?, isForward: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null

        try {
            val targetAction = if (isForward) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
            }

            val className = node.className?.toString() ?: ""
            val isHorizontal = className.contains("HorizontalScrollView", ignoreCase = true) ||
                    className.contains("ViewPager", ignoreCase = true) ||
                    className.contains("Horizontal", ignoreCase = true)

            // Only match vertical scrollable views (exclude horizontal carousels/tabs)
            if (node.isScrollable && !isHorizontal && node.actionList.contains(targetAction)) {
                return node
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    val result = findScrollableNode(child, isForward)
                    if (result != null) {
                        return result
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during node traversal", e)
        }
        return null
    }

    /**
     * Performs a short, controlled vertical swipe gesture (20% height, 350ms duration)
     * as a fallback when no direct scrollable AccessibilityNodeInfo is available.
     */
    private fun performVerticalSwipeFallback(isForward: Boolean): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width * 0.5f
        val startY: Float
        val endY: Float

        if (isForward) {
            // Short controlled stroke upward: 60% height to 40% height (prevents momentum fling)
            startY = height * 0.60f
            endY = height * 0.40f
        } else {
            // Short controlled stroke downward: 40% height to 60% height (prevents momentum fling)
            startY = height * 0.40f
            endY = height * 0.60f
        }

        return dispatchSwipePath(startX, startY, startX, endY, duration = 350L)
    }

    /**
     * Performs a horizontal swipe gesture (Swipe Left / Swipe Right) using dynamic screen dimensions.
     */
    private fun performHorizontalSwipe(isLeft: Boolean): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val centerY = height * 0.5f
        val startX: Float
        val endX: Float

        if (isLeft) {
            // Swipe Left: Finger moves right (85% width) to left (15% width)
            startX = width * 0.85f
            endX = width * 0.15f
        } else {
            // Swipe Right: Finger moves left (15% width) to right (85% width)
            startX = width * 0.15f
            endX = width * 0.85f
        }

        return dispatchSwipePath(startX, centerY, endX, centerY, duration = 250L)
    }

    /**
     * Low-level helper building and dispatching GestureDescription stroke paths.
     */
    private fun dispatchSwipePath(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long): Boolean {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0L, duration)
        gestureBuilder.addStroke(strokeDescription)

        return dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "Completed fallback swipe gesture stroke.")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Fallback swipe gesture was cancelled by system.")
            }
        }, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Lightweight event handler
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
