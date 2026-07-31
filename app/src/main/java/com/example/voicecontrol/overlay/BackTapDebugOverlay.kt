package com.example.voicecontrol.overlay

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * On-Screen Live Debug HUD Overlay for Back Tap Sensor telemetry.
 * Displays real-time Accelerometer, Linear Acceleration, Gyroscope, Jerk, Z-Peak,
 * State Machine transitions, Motion Classifications, and Tap Timings directly on screen.
 */
object BackTapDebugOverlay {

    private const val TAG = "BackTapDebugOverlay"

    private var windowManager: WindowManager? = null
    private var hudView: DebugHudCanvasView? = null
    var isDebugOverlayVisible: Boolean = false
        private set

    // Telemetry State
    var stateName: String = "IDLE"
    var motionName: String = "PHONE_STILL"
    var tapCount: Int = 0
    var lastGapMs: Long = 0L

    var accelX: Float = 0f; var accelY: Float = 0f; var accelZ: Float = 0f
    var linX: Float = 0f; var linY: Float = 0f; var linZ: Float = 0f
    var gyroX: Float = 0f; var gyroY: Float = 0f; var gyroZ: Float = 0f

    var magnitude: Float = 0f
    var zPeak: Float = 0f
    var jerk: Float = 0f
    var gyroMag: Float = 0f

    var minImpulse: Float = 0.30f
    var maxImpulse: Float = 5.00f
    var minJerk: Float = 0.35f
    var maxGyro: Float = 1.50f

    /**
     * Custom HUD view rendering live sensor telemetry.
     */
    private class DebugHudCanvasView(context: Context) : View(context) {

        private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E6121212") // Semi-transparent dark card
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF00E676") // Neon green accent border
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FF00E676")
            textSize = 28f
            isFakeBoldText = true
        }

        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
        }

        private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FFFFD600") // Yellow state highlight
            textSize = 24f
            isFakeBoldText = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val width = 520f
            val height = 480f
            val left = 20f
            val top = 80f
            val rect = RectF(left, top, left + width, top + height)

            // Draw HUD card background & border
            canvas.drawRoundRect(rect, 16f, 16f, bgPaint)
            canvas.drawRoundRect(rect, 16f, 16f, borderPaint)

            var y = top + 36f
            canvas.drawText("=== BACK TAP DEBUG HUD ===", left + 20f, y, headerPaint)

            y += 32f
            canvas.drawText("STATE: $stateName [$motionName]", left + 20f, y, highlightPaint)

            y += 28f
            canvas.drawText("TAPS: $tapCount / 3  (Gap: ${lastGapMs}ms)", left + 20f, y, textPaint)

            y += 30f
            canvas.drawText("ACCEL: X:%.2f Y:%.2f Z:%.2f".format(accelX, accelY, accelZ), left + 20f, y, textPaint)

            y += 28f
            canvas.drawText("LIN ACCEL: X:%.2f Y:%.2f Z:%.2f".format(linX, linY, linZ), left + 20f, y, textPaint)

            y += 28f
            canvas.drawText("GYRO: X:%.2f Y:%.2f Z:%.2f".format(gyroX, gyroY, gyroZ), left + 20f, y, textPaint)

            y += 32f
            canvas.drawText("MAG: %.2f m/s² | zPEAK: %.2f m/s²".format(magnitude, zPeak), left + 20f, y, textPaint)

            y += 28f
            canvas.drawText("JERK: %.2f m/s³ | GYRO MAG: %.2f".format(jerk, gyroMag), left + 20f, y, textPaint)

            y += 34f
            canvas.drawText("THRESHOLDS: Imp:%.2f..%.2f | Jerk:>=%.2f".format(minImpulse, maxImpulse, minJerk), left + 20f, y, textPaint)

            y += 28f
            canvas.drawText("MaxGyro: <%.2f rad/s".format(maxGyro), left + 20f, y, textPaint)
        }
    }

    /**
     * Shows the live Back Tap debug overlay window.
     */
    fun showOverlay(context: Context) {
        try {
            if (isDebugOverlayVisible) return

            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
            windowManager = wm

            val view = DebugHudCanvasView(context)

            val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

            val params = WindowManager.LayoutParams(
                560,
                540,
                windowType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            wm.addView(view, params)
            hudView = view
            isDebugOverlayVisible = true
            Log.i(TAG, "BackTapDebugOverlay displayed on screen.")
        } catch (e: Exception) {
            Log.e(TAG, "Error displaying BackTapDebugOverlay", e)
        }
    }

    /**
     * Updates live telemetry metrics and invalidates canvas for re-drawing.
     */
    fun updateTelemetry(
        state: String,
        motion: String,
        taps: Int,
        gapMs: Long,
        ax: Float, ay: Float, az: Float,
        lx: Float, ly: Float, lz: Float,
        gx: Float, gy: Float, gz: Float,
        mag: Float, zp: Float, jk: Float, gm: Float,
        minImp: Float, maxImp: Float, minJk: Float, maxGy: Float
    ) {
        stateName = state
        motionName = motion
        tapCount = taps
        lastGapMs = gapMs

        accelX = ax; accelY = ay; accelZ = az
        linX = lx; linY = ly; linZ = lz
        gyroX = gx; gyroY = gy; gyroZ = gz

        magnitude = mag
        zPeak = zp
        jerk = jk
        gyroMag = gm

        minImpulse = minImp
        maxImpulse = maxImp
        minJerk = minJk
        maxGyro = maxGy

        hudView?.postInvalidate()
    }

    /**
     * Hides and removes the live debug overlay window.
     */
    fun hideOverlay() {
        try {
            if (hudView != null && windowManager != null) {
                windowManager?.removeViewImmediate(hudView)
                hudView = null
                isDebugOverlayVisible = false
                Log.i(TAG, "BackTapDebugOverlay removed.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding BackTapDebugOverlay", e)
        }
    }
}
