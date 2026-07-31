package com.example.voicecontrol.grid

import android.graphics.PointF
import android.graphics.RectF
import android.util.Log

/**
 * Singleton managing active screen bounds, dynamic grid dimensions (3x3 to 10x10), and recursive zoom depth.
 */
object GridStateManager {

    private const val TAG = "GRID"

    const val DEFAULT_ROWS = 6
    const val DEFAULT_COLS = 6
    const val MIN_GRID_DIM = 3
    const val MAX_GRID_DIM = 10
    const val MAX_ZOOM_DEPTH = 2

    var isGridActive: Boolean = false
        private set

    var zoomDepth: Int = 0
        private set

    var rows: Int = DEFAULT_ROWS
        private set

    var cols: Int = DEFAULT_COLS
        private set

    val totalCells: Int
        get() = rows * cols

    private var initialScreenBounds: RectF = RectF()
    var currentBounds: RectF = RectF()
        private set

    /**
     * Updates rows and columns configuration while maintaining values for unsupplied parameters.
     * Dimensions are constrained within 3..10 range.
     */
    fun configureGrid(newRows: Int? = null, newCols: Int? = null, rawCommand: String = "configure grid") {
        if (newRows != null && newRows > 0) {
            rows = newRows.coerceIn(MIN_GRID_DIM, MAX_GRID_DIM)
        }
        if (newCols != null && newCols > 0) {
            cols = newCols.coerceIn(MIN_GRID_DIM, MAX_GRID_DIM)
        }
        // Always reset grid bounds to full screen display area
        if (!initialScreenBounds.isEmpty) {
            currentBounds = RectF(initialScreenBounds)
        }
        zoomDepth = 0
        logGridState(rawCommand)
    }

    /**
     * Initializes grid state with current screen resolution dimensions and optional custom dimensions.
     */
    fun initGrid(screenWidth: Float, screenHeight: Float, newRows: Int? = null, newCols: Int? = null, rawCommand: String = "init grid") {
        initialScreenBounds = RectF(0f, 0f, screenWidth, screenHeight)
        currentBounds = RectF(initialScreenBounds)
        zoomDepth = 0
        isGridActive = true

        if (newRows != null || newCols != null) {
            if (newRows != null && newRows > 0) rows = newRows.coerceIn(MIN_GRID_DIM, MAX_GRID_DIM)
            if (newCols != null && newCols > 0) cols = newCols.coerceIn(MIN_GRID_DIM, MAX_GRID_DIM)
        }
        logGridState(rawCommand)
    }

    /**
     * Resets grid zoom back to full screen AND resets configuration to default 6x6.
     */
    fun resetGrid(rawCommand: String = "reset grid") {
        if (!isGridActive) return
        currentBounds = RectF(initialScreenBounds)
        zoomDepth = 0
        rows = DEFAULT_ROWS
        cols = DEFAULT_COLS
        logGridState(rawCommand)
    }

    /**
     * Deactivates and clears grid state.
     */
    fun deactivateGrid() {
        isGridActive = false
        zoomDepth = 0
        currentBounds = RectF()
        Log.i(TAG, "GRID:\nRows: $rows\nColumns: $cols\nCommand: deactivateGrid")
    }

    /**
     * Calculates the bounding RectF for cell number 1..totalCells inside current bounds.
     */
    fun getCellBounds(cellNumber: Int): RectF {
        val number = cellNumber.coerceIn(1, totalCells)
        val row = (number - 1) / cols
        val col = (number - 1) % cols

        val cellWidth = currentBounds.width() / cols.toFloat()
        val cellHeight = currentBounds.height() / rows.toFloat()

        val left = currentBounds.left + (col * cellWidth)
        val top = currentBounds.top + (row * cellHeight)
        val right = left + cellWidth
        val bottom = top + cellHeight

        return RectF(left, top, right, bottom)
    }

    /**
     * Zooms grid into selected sub-cell region (1..totalCells).
     */
    fun zoomIntoCell(cellNumber: Int): RectF {
        val selectedBounds = getCellBounds(cellNumber)
        currentBounds = selectedBounds
        zoomDepth++
        logGridState("zoomIntoCell #$cellNumber (Depth=$zoomDepth)")
        return currentBounds
    }

    /**
     * Returns the center coordinate (PointF) of the currently selected grid region.
     */
    fun getCenterCoordinate(): PointF {
        return PointF(currentBounds.centerX(), currentBounds.centerY())
    }

    /**
     * Logs current grid configuration strictly formatted per requirement 10.
     */
    fun logGridState(command: String) {
        Log.i(TAG, "GRID:\nRows:\n$rows\nColumns:\n$cols\nCommand:\n$command")
    }
}
