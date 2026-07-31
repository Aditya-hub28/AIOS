package com.example.voicecontrol.grid

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Manager handling rendering and updates of full-screen 3x3 Grid Overlay via WindowManager.
 */
object GridOverlayManager {

    private const val TAG = "GridOverlayManager"

    private var windowManager: WindowManager? = null
    private var gridCanvasView: GridCanvasView? = null

    /**
     * Custom Canvas View rendering 3x3 grid lines and cell numbers (1..9).
     */
    private class GridCanvasView(context: Context) : View(context) {

        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6200EE") // Material 3 Primary Accent
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#BB86FC")
            style = Paint.Style.STROKE
            strokeWidth = 6f
        }

        private val circleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DD000000") // Semi-transparent dark background
            style = Paint.Style.FILL
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 36f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!GridStateManager.isGridActive) return

            val bounds = GridStateManager.currentBounds
            val cellW = bounds.width() / 3f
            val cellH = bounds.height() / 3f

            // 1. Draw outer boundary box around active region
            canvas.drawRect(bounds, borderPaint)

            // 2. Draw vertical grid lines
            canvas.drawLine(bounds.left + cellW, bounds.top, bounds.left + cellW, bounds.bottom, linePaint)
            canvas.drawLine(bounds.left + (cellW * 2), bounds.top, bounds.left + (cellW * 2), bounds.bottom, linePaint)

            // 3. Draw horizontal grid lines
            canvas.drawLine(bounds.left, bounds.top + cellH, bounds.right, bounds.top + cellH, linePaint)
            canvas.drawLine(bounds.left, bounds.top + (cellH * 2), bounds.right, bounds.top + (cellH * 2), linePaint)

            // 4. Draw number badges (1..9) at center of each 3x3 cell
            val circleRadius = 26f
            var number = 1

            for (row in 0..2) {
                for (col in 0..2) {
                    val centerX = bounds.left + (col * cellW) + (cellW / 2f)
                    val centerY = bounds.top + (row * cellH) + (cellH / 2f)

                    // Draw dark background circle for high visibility
                    canvas.drawCircle(centerX, centerY, circleRadius, circleBgPaint)

                    // Draw text number centered
                    val textOffset = (textPaint.descent() + textPaint.ascent()) / 2f
                    canvas.drawText(number.toString(), centerX, centerY - textOffset, textPaint)

                    number++
                }
            }
        }
    }

    /**
     * Displays the grid overlay window.
     */
    fun showGrid(service: AccessibilityService) {
        try {
            val metrics: DisplayMetrics = service.resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()

            GridStateManager.initGrid(width, height)

            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            windowManager = wm

            if (gridCanvasView != null) {
                hideGrid()
            }

            val view = GridCanvasView(service)

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            wm.addView(view, params)
            gridCanvasView = view
            Log.i(TAG, "Grid overlay window successfully added.")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying grid overlay", e)
        }
    }

    /**
     * Invalidates and redraws the grid canvas view after zooming or resetting.
     */
    fun updateGrid() {
        gridCanvasView?.invalidate()
    }

    /**
     * Removes and hides the grid overlay window.
     */
    fun hideGrid() {
        try {
            if (gridCanvasView != null && windowManager != null) {
                windowManager?.removeViewImmediate(gridCanvasView)
                gridCanvasView = null
                Log.i(TAG, "Grid overlay window removed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding grid overlay", e)
        } finally {
            GridStateManager.deactivateGrid()
        }
    }

    /**
     * Returns true if grid overlay is currently visible.
     */
    fun isGridVisible(): Boolean = GridStateManager.isGridActive
}
