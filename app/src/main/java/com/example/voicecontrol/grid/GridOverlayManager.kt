package com.example.voicecontrol.grid

import android.accessibilityservice.AccessibilityService
import android.animation.ValueAnimator
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
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

/**
 * Manager handling rendering and animated updates of dynamic Grid Overlay (3x3 to 10x10) via WindowManager.
 */
object GridOverlayManager {

    private const val TAG = "GridOverlayManager"

    private var windowManager: WindowManager? = null
    private var gridCanvasView: GridCanvasView? = null

    /**
     * Custom Canvas View rendering responsive iPhone Voice Control style dynamic grid lines and badges.
     */
    private class GridCanvasView(context: Context) : View(context) {

        private val animatedBounds = RectF()
        private var boundsAnimator: ValueAnimator? = null

        private val gridLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#806200EE") // Primary Accent semi-transparent line
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D0BB86FC") // Bright accent border
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D9000000") // Semi-transparent dark pill background
            style = Paint.Style.FILL
        }

        private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40FFFFFF") // Subtle badge outline
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }

        fun updateBoundsAnimated(targetBounds: RectF, animate: Boolean = true) {
            if (animatedBounds.isEmpty || !animate) {
                animatedBounds.set(targetBounds)
                invalidate()
                return
            }

            boundsAnimator?.cancel()

            val startLeft = animatedBounds.left
            val startTop = animatedBounds.top
            val startRight = animatedBounds.right
            val startBottom = animatedBounds.bottom

            boundsAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 250L
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    val fraction = animator.animatedValue as Float
                    animatedBounds.left = startLeft + (targetBounds.left - startLeft) * fraction
                    animatedBounds.top = startTop + (targetBounds.top - startTop) * fraction
                    animatedBounds.right = startRight + (targetBounds.right - startRight) * fraction
                    animatedBounds.bottom = startBottom + (targetBounds.bottom - startBottom) * fraction
                    invalidate()
                }
                start()
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!GridStateManager.isGridActive) return

            val bounds = animatedBounds
            if (bounds.width() <= 0 || bounds.height() <= 0) return

            val cols = GridStateManager.cols
            val rows = GridStateManager.rows

            // 1. Calculate cellWidth & cellHeight dynamically
            val cellWidth = bounds.width() / cols.toFloat()
            val cellHeight = bounds.height() / rows.toFloat()

            // 2. Draw outer boundary box
            canvas.drawRect(bounds, borderPaint)

            // 3. Draw vertical grid lines
            for (col in 1 until cols) {
                val x = bounds.left + (col * cellWidth)
                canvas.drawLine(x, bounds.top, x, bounds.bottom, gridLinePaint)
            }

            // 4. Draw horizontal grid lines
            for (row in 1 until rows) {
                val y = bounds.top + (row * cellHeight)
                canvas.drawLine(bounds.left, y, bounds.right, y, gridLinePaint)
            }

            // 5. Responsive sizing calculations
            val minCellDim = min(cellWidth, cellHeight)
            val badgeRadius = minCellDim * 0.25f
            val baseTextSize = minCellDim * 0.4f

            badgeBorderPaint.strokeWidth = (badgeRadius * 0.08f).coerceAtLeast(1.5f)

            var number = 1

            for (row in 0 until rows) {
                for (col in 0 until cols) {
                    // Center point of each cell
                    val centerX = bounds.left + (col * cellWidth) + (cellWidth / 2f)
                    val centerY = bounds.top + (row * cellHeight) + (cellHeight / 2f)

                    // Adjust text size for 3-digit cell numbers to prevent overflow inside badge circle
                    val textSize = if (number >= 100) baseTextSize * 0.8f else baseTextSize
                    textPaint.textSize = textSize

                    // Perfect vertical center alignment calculation using FontMetrics
                    val fontMetrics = textPaint.fontMetrics
                    val textOffset = (fontMetrics.descent + fontMetrics.ascent) / 2f
                    val textY = centerY - textOffset

                    // Draw pill background circle and border
                    canvas.drawCircle(centerX, centerY, badgeRadius, badgeBgPaint)
                    canvas.drawCircle(centerX, centerY, badgeRadius, badgeBorderPaint)

                    // Draw text label centered
                    canvas.drawText(number.toString(), centerX, textY, textPaint)

                    number++
                }
            }
        }
    }

    /**
     * Displays the dynamic grid overlay window.
     */
    fun showGrid(service: AccessibilityService, customRows: Int? = null, customCols: Int? = null, rawCommand: String = "show grid") {
        try {
            val metrics: DisplayMetrics = service.resources.displayMetrics
            val width = metrics.widthPixels.toFloat()
            val height = metrics.heightPixels.toFloat()

            GridStateManager.initGrid(width, height, customRows, customCols, rawCommand)

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
            view.updateBoundsAnimated(GridStateManager.currentBounds, animate = false)
            Log.i(TAG, "Dynamic Grid overlay window successfully added.")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying dynamic grid overlay", e)
        }
    }

    /**
     * Updates and animates grid bounds after zooming, configuration changes, or resetting.
     */
    fun updateGrid(animate: Boolean = true) {
        gridCanvasView?.updateBoundsAnimated(GridStateManager.currentBounds, animate = animate)
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
