package com.example.voicecontrol.util

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.Locale

/**
 * Helper class for text-based UI clicking ("Tap <element name>").
 * Performs recursive Accessibility tree inspection, 4-tier matching priority evaluation,
 * clickable parent resolution, and coordinate fallback gesture tapping.
 */
object NodeSearchHelper {

    private const val TAG = "NodeSearchHelper"

    private data class NodeMatchCandidate(
        val node: AccessibilityNodeInfo,
        val score: Int,
        val matchedText: String
    )

    /**
     * Searches active window accessibility tree for target text and executes click action.
     * @param service Active VoiceAccessibilityService instance.
     * @param targetText Spoken element name to search and click.
     * @return True if a matching node was found and successfully clicked.
     */
    fun searchAndClick(service: AccessibilityService, targetText: String): Boolean {
        val rootNode = service.rootInActiveWindow ?: run {
            Log.w(TAG, "Unable to perform click: rootInActiveWindow is null.")
            return false
        }

        val cleanedTarget = targetText.trim().lowercase(Locale.getDefault())
        if (cleanedTarget.isBlank()) return false

        val candidates = mutableListOf<NodeMatchCandidate>()
        collectMatchingNodes(rootNode, cleanedTarget, candidates)

        if (candidates.isEmpty()) {
            Log.w(TAG, "No matching node found for target text: '$targetText'")
            try { rootNode.recycle() } catch (_: Exception) {}
            return false
        }

        // Sort candidates by highest match score
        candidates.sortByDescending { it.score }
        val bestMatch = candidates.first()

        Log.i(
            TAG,
            "Best match for '$targetText': Score=${bestMatch.score}, Text='${bestMatch.matchedText}', Class=${bestMatch.node.className}"
        )

        val success = executeNodeClick(service, bestMatch.node)

        // Recycle collected nodes safely
        candidates.forEach {
            try { it.node.recycle() } catch (_: Exception) {}
        }
        try { rootNode.recycle() } catch (_: Exception) {}

        return success
    }

    /**
     * Recursively traverses accessibility tree and assigns 4-tier match scores.
     */
    private fun collectMatchingNodes(
        node: AccessibilityNodeInfo?,
        target: String,
        candidates: MutableList<NodeMatchCandidate>
    ) {
        if (node == null) return

        try {
            val text = node.text?.toString()?.trim()
            val contentDesc = node.contentDescription?.toString()?.trim()
            val hintText = node.hintText?.toString()?.trim()

            var bestScore = 0
            var matchedString = ""

            // Priority 1: Exact Text Match
            if (!text.isNullOrBlank() && text.lowercase(Locale.getDefault()) == target) {
                bestScore = 100
                matchedString = text
            }
            // Priority 2: Exact Content Description Match
            else if (!contentDesc.isNullOrBlank() && contentDesc.lowercase(Locale.getDefault()) == target) {
                bestScore = 90
                matchedString = contentDesc
            }
            // Priority 1.5: Exact Hint Text Match (e.g. Search inputs)
            else if (!hintText.isNullOrBlank() && hintText.lowercase(Locale.getDefault()) == target) {
                bestScore = 85
                matchedString = hintText
            }
            // Priority 3: Partial Text Match
            else if (!text.isNullOrBlank() && text.lowercase(Locale.getDefault()).contains(target)) {
                bestScore = 70
                matchedString = text
            }
            // Priority 4: Partial Content Description Match
            else if (!contentDesc.isNullOrBlank() && contentDesc.lowercase(Locale.getDefault()).contains(target)) {
                bestScore = 60
                matchedString = contentDesc
            }
            // Priority 4.5: Partial Hint Text Match
            else if (!hintText.isNullOrBlank() && hintText.lowercase(Locale.getDefault()).contains(target)) {
                bestScore = 55
                matchedString = hintText
            }

            if (bestScore > 0 && node.isVisibleToUser) {
                candidates.add(NodeMatchCandidate(AccessibilityNodeInfo.obtain(node), bestScore, matchedString))
            }

            // Recurse children
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectMatchingNodes(child, target, candidates)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error traversing node", e)
        }
    }

    /**
     * Executes click action using 3-stage strategy (Direct Click ➔ Parent Click ➔ Gesture Tap Fallback).
     */
    private fun executeNodeClick(service: AccessibilityService, node: AccessibilityNodeInfo): Boolean {
        try {
            // Strategy A: Direct Node Click if node.isClickable
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    Log.i(TAG, "Successfully executed direct ACTION_CLICK on node.")
                    return true
                }
            }

            // Strategy B: Clickable Parent Traversal (up to 6 levels)
            var currentParent = node.parent
            var depth = 0
            while (currentParent != null && depth < 6) {
                if (currentParent.isClickable) {
                    val parentClicked = currentParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (parentClicked) {
                        Log.i(TAG, "Successfully executed ACTION_CLICK on clickable parent container (Depth=$depth).")
                        try { currentParent.recycle() } catch (_: Exception) {}
                        return true
                    }
                }
                val prevParent = currentParent
                currentParent = currentParent.parent
                try { prevParent.recycle() } catch (_: Exception) {}
                depth++
            }

            // Strategy C: Gesture Tap Fallback at node screen center coordinates
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (!rect.isEmpty) {
                val tapX = rect.centerX().toFloat()
                val tapY = rect.centerY().toFloat()
                Log.i(TAG, "No clickable node/parent handled action. Dispatching gesture tap fallback at ($tapX, $tapY).")
                return performGestureTap(service, tapX, tapY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing node click", e)
        }
        return false
    }

    /**
     * Dispatches a single tap gesture stroke at screen center coordinates.
     */
    private fun performGestureTap(service: AccessibilityService, x: Float, y: Float): Boolean {
        val tapPath = Path().apply {
            moveTo(x, y)
        }

        val gestureBuilder = GestureDescription.Builder()
        val strokeDescription = GestureDescription.StrokeDescription(tapPath, 0L, 50L)
        gestureBuilder.addStroke(strokeDescription)

        return service.dispatchGesture(gestureBuilder.build(), object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.i(TAG, "Successfully completed gesture tap at ($x, $y).")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Gesture tap at ($x, $y) was cancelled by system.")
            }
        }, null)
    }
}
