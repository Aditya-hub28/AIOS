package com.example.voicecontrol.grid

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log

/**
 * Controller processing 6x6 grid overlay commands (Show Grid, Hide Grid, Reset Grid, Click Here, Tap Cell 1..36).
 */
object GridCommandProcessor {

    private const val TAG = "GridCommandProcessor"

    /**
     * Shows initial full-screen 6x6 grid overlay.
     */
    fun showGrid(service: AccessibilityService): Boolean {
        GridOverlayManager.showGrid(service)
        return true
    }

    /**
     * Hides grid overlay.
     */
    fun hideGrid(): Boolean {
        GridOverlayManager.hideGrid()
        return true
    }

    /**
     * Resets grid zoom to full screen layout.
     */
    fun resetGrid(): Boolean {
        if (!GridOverlayManager.isGridVisible()) return false
        GridStateManager.resetGrid()
        GridOverlayManager.updateGrid(animate = true)
        Log.i(TAG, "Reset grid to initial full screen layout.")
        return true
    }

    /**
     * Zooms into selected 6x6 cell (1..36) or clicks center if max zoom depth reached.
     */
    fun selectCell(service: AccessibilityService, cellNumber: Int): Boolean {
        if (!GridOverlayManager.isGridVisible()) {
            showGrid(service)
        }

        if (GridStateManager.zoomDepth >= GridStateManager.MAX_ZOOM_DEPTH) {
            // Max zoom depth reached -> Perform gesture click at center of selected cell & close grid
            val cellBounds = GridStateManager.getCellBounds(cellNumber)
            val tapX = cellBounds.centerX()
            val tapY = cellBounds.centerY()

            Log.i(TAG, "Max zoom depth reached for cell #$cellNumber. Performing gesture tap at ($tapX, $tapY).")
            val success = performGestureTap(service, tapX, tapY)
            hideGrid()
            return success
        } else {
            // Recursively zoom into selected cell region
            GridStateManager.zoomIntoCell(cellNumber)
            GridOverlayManager.updateGrid(animate = true)
            Log.i(TAG, "Selected cell #$cellNumber -> Zoom depth ${GridStateManager.zoomDepth}")
            return true
        }
    }

    /**
     * Executes gesture tap at center of active grid region and closes grid.
     */
    fun clickHere(service: AccessibilityService): Boolean {
        if (!GridOverlayManager.isGridVisible()) {
            Log.w(TAG, "Unable to execute Click Here: Grid is not active.")
            return false
        }

        val center = GridStateManager.getCenterCoordinate()
        Log.i(TAG, "Executing Click Here at coordinates (${center.x}, ${center.y})")

        val success = performGestureTap(service, center.x, center.y)
        hideGrid()
        return success
    }

    /**
     * Helper executing a touch tap gesture stroke at target screen coordinates.
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
                Log.i(TAG, "Completed grid gesture tap at ($x, $y).")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.w(TAG, "Grid gesture tap at ($x, $y) was cancelled by system.")
            }
        }, null)
    }
}
