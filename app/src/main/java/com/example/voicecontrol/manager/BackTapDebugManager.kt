package com.example.voicecontrol.manager

import android.content.Context
import android.os.SystemClock
import com.example.voicecontrol.overlay.BackTapDebugOverlay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Telemetry snapshot data model for in-app developer debug dashboard.
 */
data class BackTapTelemetry(
    val accelX: Float = 0f, val accelY: Float = 0f, val accelZ: Float = 0f,
    val linX: Float = 0f, val linY: Float = 0f, val linZ: Float = 0f,
    val gyroX: Float = 0f, val gyroY: Float = 0f, val gyroZ: Float = 0f,
    val magnitude: Float = 0f,
    val peak: Float = 0f,
    val zPeak: Float = 0f,
    val jerk: Float = 0f,
    val gyroMag: Float = 0f,
    val stateName: String = "IDLE",
    val motionName: String = "PHONE_STILL",
    val tapCount: Int = 0,
    val lastGapMs: Long = 0L
)

/**
 * Singleton repository managing real-time Back Tap telemetry, live event feeds (last 200 items),
 * floating overlay toggling, and dashboard state.
 */
object BackTapDebugManager {

    private val _telemetry = MutableStateFlow(BackTapTelemetry())
    val telemetry: StateFlow<BackTapTelemetry> = _telemetry.asStateFlow()

    private val _eventLogs = MutableStateFlow<List<String>>(emptyList())
    val eventLogs: StateFlow<List<String>> = _eventLogs.asStateFlow()

    private val _isFloatingOverlayActive = MutableStateFlow(false)
    val isFloatingOverlayActive: StateFlow<Boolean> = _isFloatingOverlayActive.asStateFlow()

    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    private var lastUpdateUptime = 0L

    /**
     * Updates real-time telemetry values throttled to 15-20 FPS (~50ms window) for performance.
     */
    fun updateTelemetry(
        ax: Float, ay: Float, az: Float,
        lx: Float, ly: Float, lz: Float,
        gx: Float, gy: Float, gz: Float,
        mag: Float, peak: Float, zp: Float, jk: Float, gm: Float,
        state: String, motion: String, count: Int, gapMs: Long
    ) {
        val now = SystemClock.uptimeMillis()
        if (now - lastUpdateUptime < 50L) return
        lastUpdateUptime = now

        _telemetry.value = BackTapTelemetry(
            accelX = ax, accelY = ay, accelZ = az,
            linX = lx, linY = ly, linZ = lz,
            gyroX = gx, gyroY = gy, gyroZ = gz,
            magnitude = mag, peak = peak, zPeak = zp, jerk = jk, gyroMag = gm,
            stateName = state, motionName = motion, tapCount = count, lastGapMs = gapMs
        )
    }

    /**
     * Appends a timestamped event to the live event log feed (keeps last 200 events).
     */
    fun logEvent(event: String) {
        val timestamp = timeFormatter.format(Date())
        val formattedLog = "[$timestamp] $event"

        val currentList = _eventLogs.value.toMutableList()
        currentList.add(0, formattedLog) // Add newest event to top

        if (currentList.size > 200) {
            currentList.removeAt(currentList.lastIndex) // Maintain last 200 items limit
        }

        _eventLogs.value = currentList
    }

    /**
     * Wipes all live event logs cleanly.
     */
    fun clearLogs() {
        _eventLogs.value = emptyList()
    }

    /**
     * Toggles live floating overlay HUD window.
     */
    fun toggleFloatingOverlay(context: Context) {
        val newState = !_isFloatingOverlayActive.value
        _isFloatingOverlayActive.value = newState

        if (newState) {
            BackTapDebugOverlay.showOverlay(context)
        } else {
            BackTapDebugOverlay.hideOverlay()
        }
    }
}
