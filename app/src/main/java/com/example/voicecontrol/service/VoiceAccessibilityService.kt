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

/**
 * Dedicated Accessibility Service for executing Android global actions (Home, Back, Recent Apps)
 * and gesture navigation commands (Scroll Down, Scroll Up, Swipe Left, Swipe Right) across all apps.
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
     * Executes gesture navigation commands (Scroll Down, Scroll Up, Swipe Left, Swipe Right).
     * @param type GestureType enum indicating direction.
     * @return True if gesture or node action was successfully dispatched.
     */
    fun performGestureNavigation(type: GestureType): Boolean {
        return try {
            when (type) {
                GestureType.SCROLL_DOWN -> performScrollNodeOrGesture(isForward = true)
                GestureType.SCROLL_UP -> performScrollNodeOrGesture(isForward = false)
                GestureType.SWIPE_LEFT -> performHorizontalSwipe(isLeft = true)
                GestureType.SWIPE_RIGHT -> performHorizontalSwipe(isLeft = false)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing gesture navigation $type", e)
            false
        }
    }

    /**
     * Scrolls active container via node action first, falling back to dynamic vertical gesture swipe.
     */
    private fun performScrollNodeOrGesture(isForward: Boolean): Boolean {
        val rootNode = rootInActiveWindow
        if (rootNode != null) {
            val targetNode = findScrollableNode(rootNode, isForward)
            if (targetNode != null) {
                val action = if (isForward) {
                    AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                } else {
                    AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                }
                val nodeScrolled = targetNode.performAction(action)
                try { targetNode.recycle() } catch (_: Exception) {}
                try { rootNode.recycle() } catch (_: Exception) {}

                if (nodeScrolled) {
                    Log.i(TAG, "Successfully scrolled active node container via node action.")
                    return true
                }
            } else {
                try { rootNode.recycle() } catch (_: Exception) {}
            }
        }

        Log.i(TAG, "No direct scrollable node action performed. Dispatching dynamic vertical gesture fallback.")
        return performVerticalSwipe(isForward)
    }

    /**
     * Performs a vertical swipe gesture using dynamic screen dimensions.
     */
    private fun performVerticalSwipe(isForward: Boolean): Boolean {
        val metrics: DisplayMetrics = resources.displayMetrics
        val width = metrics.widthPixels.toFloat()
        val height = metrics.heightPixels.toFloat()

        val startX = width / 2f
        val startY: Float
        val endY: Float

        if (isForward) {
            // Scroll Down: Swipe UP from bottom (75% height) to top (25% height)
            startY = height * 0.75f
            endY = height * 0.25f
        } else {
            // Scroll Up: Swipe DOWN from top (25% height) to bottom (75% height)
            startY = height * 0.25f
            endY = height * 0.75f
        }

        return dispatchSwipeGesture(startX, startY, startX, endY)
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

        return dispatchSwipeGesture(startX, centerY, endX, centerY)
    }

    /**
     * Low-level helper building and dispatching GestureDescription stroke paths.
     */
    private fun dispatchSwipeGesture(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0L, 250L)
        gestureBuilder.addStroke(strokeDescription)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    /**
     * Recursively traverses node tree to find the best scrollable container.
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo?, isForward: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null

        try {
            val targetAction = if (isForward) {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
            } else {
                AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
            }

            if (node.isScrollable && node.actionList.contains(targetAction)) {
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
