package com.example.voicecontrol.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Manager handling rendering and positioning of number badge overlays over active applications using WindowManager.
 */
object NumberOverlayManager {

    private const val TAG = "NumberOverlayManager"

    private var windowManager: WindowManager? = null
    private var containerOverlayView: FrameLayout? = null
    private var isShowingOverlays = false

    /**
     * Renders badge overlays for mapped clickable targets on screen.
     */
    fun showOverlays(service: AccessibilityService, targets: List<ClickableNodeTarget>) {
        try {
            hideOverlays()

            if (targets.isEmpty()) {
                Log.w(TAG, "No targets to display overlay badges for.")
                return
            }

            val wm = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            windowManager = wm

            val container = FrameLayout(service)

            // Setup WindowManager LayoutParams for Accessibility Overlay
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

            // Create individual number badge pill views
            for (target in targets) {
                val badgeView = createBadgeView(service, target.number)

                val badgeParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    leftMargin = (target.bounds.left - 12).coerceAtLeast(8)
                    topMargin = (target.bounds.top - 12).coerceAtLeast(8)
                }

                container.addView(badgeView, badgeParams)
            }

            wm.addView(container, params)
            containerOverlayView = container
            isShowingOverlays = true
            Log.i(TAG, "Successfully added number overlay window with ${targets.size} badges.")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing number overlays", e)
        }
    }

    /**
     * Removes and destroys all active number badge overlays.
     */
    fun hideOverlays() {
        try {
            if (containerOverlayView != null && windowManager != null) {
                windowManager?.removeViewImmediate(containerOverlayView)
                containerOverlayView = null
                Log.i(TAG, "Successfully removed number overlay window.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding number overlays", e)
        } finally {
            isShowingOverlays = false
        }
    }

    /**
     * Returns true if number overlays are currently rendered on screen.
     */
    fun isOverlaysVisible(): Boolean = isShowingOverlays

    /**
     * Creates a small, high-contrast, rounded Material 3 badge pill view.
     */
    private fun createBadgeView(context: Context, number: Int): View {
        val textView = TextView(context).apply {
            text = number.toString()
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(dpToPx(context, 6), dpToPx(context, 2), dpToPx(context, 6), dpToPx(context, 2))
            gravity = Gravity.CENTER
        }

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(context, 10).toFloat()
            setColor(Color.parseColor("#6200EE")) // High contrast primary accent pill background
            setStroke(dpToPx(context, 1), Color.WHITE) // High contrast white border
        }

        textView.background = backgroundDrawable
        return textView
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
