package com.example.voicecontrol.grid

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log

/**
 * Singleton managing active screen bounds, 6x6 (36 cells) region subdivision, and recursive zoom depth.
 */
object GridStateManager {

    private const val TAG = "GridStateManager"

    const val GRID_ROWS = 6
    const val GRID_COLS = 6
    const val TOTAL_CELLS = 36
    const val MAX_ZOOM_DEPTH = 2

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
        Log.i(TAG, "6x6 Grid initialized: ${screenWidth.toInt()}x${screenHeight.toInt()}")
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
     * Calculates the bounding RectF for cell number 1..36 inside current bounds.
     */
    fun getCellBounds(cellNumber: Int): RectF {
        val number = cellNumber.coerceIn(1, TOTAL_CELLS)
        val row = (number - 1) / GRID_ROWS
        val col = (number - 1) % GRID_COLS

        val cellWidth = currentBounds.width() / GRID_COLS.toFloat()
        val cellHeight = currentBounds.height() / GRID_ROWS.toFloat()

        val left = currentBounds.left + (col * cellWidth)
        val top = currentBounds.top + (row * cellHeight)
        val right = left + cellWidth
        val bottom = top + cellHeight

        return RectF(left, top, right, bottom)
    }

    /**
     * Zooms grid into selected sub-cell region (1..36).
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
