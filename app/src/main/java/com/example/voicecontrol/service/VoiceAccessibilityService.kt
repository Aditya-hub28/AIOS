package com.example.voicecontrol.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.DisplayMetrics
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.voicecontrol.manager.AccessibilityCommandManager

/**
 * Dedicated Accessibility Service for executing Android global actions (Home, Back, Recent Apps)
 * and voice-controlled scrolling (Scroll Down, Scroll Up) across all apps.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "VoiceAccessibilityService connected to System Accessibility Framework.")
        AccessibilityCommandManager.registerService(this)
    }

    /**
     * Scrolls the currently active window or focused scrollable container.
     * @param isForward True for Scroll Down, False for Scroll Up.
     * @return True if scroll action or gesture was successfully performed.
     */
    fun performScroll(isForward: Boolean): Boolean {
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
                targetNode.recycle()
                rootNode.recycle()

                if (nodeScrolled) {
                    Log.i(TAG, "Successfully scrolled active node container.")
                    return true
                }
            } else {
                rootNode.recycle()
            }
        }

        // Fallback: Perform smooth swipe gesture for WebViews, Instagram Reels, Chrome, etc.
        Log.i(TAG, "No direct scrollable node found. Dispatching gesture swipe fallback.")
        return performSwipeGesture(isForward)
    }

    /**
     * Recursively traverses node tree to find the best scrollable container.
     */
    private fun findScrollableNode(node: AccessibilityNodeInfo?, isForward: Boolean): AccessibilityNodeInfo? {
        if (node == null) return null

        val targetAction = if (isForward) {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD
        }

        // Check if node supports scroll action
        if (node.isScrollable && node.actionList.contains(targetAction)) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findScrollableNode(child, isForward)
            if (result != null) {
                return result
            }
        }
        return null
    }

    /**
     * Performs a vertical swipe gesture as a universal fallback for custom views and WebViews.
     */
    private fun performSwipeGesture(isForward: Boolean): Boolean {
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

        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(startX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(swipePath, 0L, 250L)
        gestureBuilder.addStroke(strokeDescription)

        return dispatchGesture(gestureBuilder.build(), null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Window state events received
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
