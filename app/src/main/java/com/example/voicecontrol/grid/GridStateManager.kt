package com.example.voicecontrol.grid

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log

/**
 * Singleton managing active screen bounds, 3x3 region subdivision, and progressive zoom depth.
 */
object GridStateManager {

    private const val TAG = "GridStateManager"

    var isGridActive: Boolean = false
        private set

    var zoomDepth: Int = 0
        private set

    private var initialScreenBounds: RectF = RectF()
    var currentBounds: RectF = RectF()
        private set

    /**
     * Initializes grid state with current screen resolution dimensions.
     */
    fun initGrid(screenWidth: Float, screenHeight: Float) {
        initialScreenBounds = RectF(0f, 0f, screenWidth, screenHeight)
        currentBounds = RectF(initialScreenBounds)
        zoomDepth = 0
        isGridActive = true
        Log.i(TAG, "Grid initialized: ${screenWidth.toInt()}x${screenHeight.toInt()}")
    }

    /**
     * Resets grid zoom back to full screen.
     */
    fun resetGrid() {
        if (!isGridActive) return
        currentBounds = RectF(initialScreenBounds)
        zoomDepth = 0
        Log.i(TAG, "Grid reset to full screen bounds.")
    }

    /**
     * Deactivates and clears grid state.
     */
    fun deactivateGrid() {
        isGridActive = false
        zoomDepth = 0
        currentBounds = RectF()
        Log.i(TAG, "Grid deactivated.")
    }

    /**
     * Calculates the bounding RectF for cell number 1..9 inside current bounds.
     */
    fun getCellBounds(cellNumber: Int): RectF {
        val number = cellNumber.coerceIn(1, 9)
        val row = (number - 1) / 3
        val col = (number - 1) % 3

        val cellWidth = currentBounds.width() / 3f
        val cellHeight = currentBounds.height() / 3f

        val left = currentBounds.left + (col * cellWidth)
        val top = currentBounds.top + (row * cellHeight)
        val right = left + cellWidth
        val bottom = top + cellHeight

        return RectF(left, top, right, bottom)
    }

    /**
     * Zooms grid into selected sub-cell region (1..9).
     */
    fun zoomIntoCell(cellNumber: Int): RectF {
        val selectedBounds = getCellBounds(cellNumber)
        currentBounds = selectedBounds
        zoomDepth++
        Log.i(TAG, "Zoomed into cell #$cellNumber (Depth=$zoomDepth): $currentBounds")
        return currentBounds
    }

    /**
     * Returns the center coordinate (PointF) of the currently selected grid region.
     */
    fun getCenterCoordinate(): PointF {
        return PointF(currentBounds.centerX(), currentBounds.centerY())
    }
}
