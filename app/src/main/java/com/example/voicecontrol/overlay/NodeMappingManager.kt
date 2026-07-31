package com.example.voicecontrol.overlay

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Data holder for mapped clickable nodes on screen.
 */
data class ClickableNodeTarget(
    val number: Int,
    val node: AccessibilityNodeInfo,
    val bounds: Rect,
    val label: String
)

/**
 * Singleton manager mapping integer badge numbers (1, 2, 3...) to visible clickable AccessibilityNodeInfo nodes.
 */
object NodeMappingManager {

    private const val TAG = "NodeMappingManager"
    private val numberMap = ConcurrentHashMap<Int, ClickableNodeTarget>()

    /**
     * Scans active window node tree, extracts visible clickable elements, deduplicates bounds,
     * and generates number 1..N mapping.
     */
    fun scanAndMapNodes(rootNode: AccessibilityNodeInfo?): List<ClickableNodeTarget> {
        clearMapping()
        if (rootNode == null) return emptyList()

        val rawNodes = mutableListOf<AccessibilityNodeInfo>()
        collectClickableNodes(rootNode, rawNodes)

        var currentNumber = 1
        val mappedList = mutableListOf<ClickableNodeTarget>()
        val existingBounds = mutableListOf<Rect>()

        for (node in rawNodes) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            // Ignore empty bounds or hidden nodes
            if (rect.isEmpty || rect.width() <= 5 || rect.height() <= 5) continue

            // Deduplicate heavily overlapping bounds (within 15px)
            var isDuplicate = false
            for (eb in existingBounds) {
                if (abs(eb.left - rect.left) < 15 && abs(eb.top - rect.top) < 15 &&
                    abs(eb.right - rect.right) < 15 && abs(eb.bottom - rect.bottom) < 15
                ) {
                    isDuplicate = true
                    break
                }
            }

            if (isDuplicate) continue

            existingBounds.add(rect)

            val labelText = node.text?.toString()
                ?: node.contentDescription?.toString()
                ?: node.className?.toString()?.substringAfterLast('.')
                ?: "Element"

            val target = ClickableNodeTarget(
                number = currentNumber,
                node = AccessibilityNodeInfo.obtain(node),
                bounds = rect,
                label = labelText
            )

            numberMap[currentNumber] = target
            mappedList.add(target)
            currentNumber++

            if (currentNumber > 99) break // Cap max overlays to 99 for readability
        }

        Log.i(TAG, "Successfully scanned and mapped ${mappedList.size} visible clickable elements.")
        return mappedList
    }

    /**
     * Retrieves mapped target by number.
     */
    fun getTargetForNumber(number: Int): ClickableNodeTarget? {
        return numberMap[number]
    }

    /**
     * Clears all stored node mappings and recycles cached AccessibilityNodeInfo instances.
     */
    fun clearMapping() {
        numberMap.values.forEach {
            try { it.node.recycle() } catch (_: Exception) {}
        }
        numberMap.clear()
    }

    /**
     * Recursively traverses accessibility tree to find visible clickable elements.
     */
    private fun collectClickableNodes(
        node: AccessibilityNodeInfo?,
        outList: MutableList<AccessibilityNodeInfo>
    ) {
        if (node == null) return

        try {
            if (node.isVisibleToUser) {
                val isClickable = node.isClickable ||
                        node.isCheckable ||
                        node.actionList.contains(AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK)

                // Check if element or parent is clickable
                val isChildTextOrIcon = !node.text.isNullOrBlank() || !node.contentDescription.isNullOrBlank()
                val parentIsClickable = node.parent?.isClickable == true

                if (isClickable || (isChildTextOrIcon && parentIsClickable)) {
                    outList.add(node)
                }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    collectClickableNodes(child, outList)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error collecting clickable nodes", e)
        }
    }
}
