package com.example.voicecontrol.manager

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import com.example.voicecontrol.service.VoiceAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager routing global action, scroll, text click, overlay, grid, and gesture execution requests to the active VoiceAccessibilityService.
 * Extensible for future accessibility features (OCR, AI agent).
 */
object AccessibilityCommandManager {

    private const val TAG = "AccessibilityCommand"

    private var serviceInstance: VoiceAccessibilityService? = null

    private val _isServiceConnected = MutableStateFlow(false)
    val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

    /**
     * Called by VoiceAccessibilityService upon connection.
     */
    fun registerService(service: VoiceAccessibilityService) {
        serviceInstance = service
        _isServiceConnected.value = true
        Log.i(TAG, "VoiceAccessibilityService registered successfully.")
    }

    /**
     * Called by VoiceAccessibilityService upon disconnection or destruction.
     */
    fun unregisterService() {
        serviceInstance = null
        _isServiceConnected.value = false
        Log.i(TAG, "VoiceAccessibilityService unregistered.")
    }

    // --- GRID OVERLAY SYSTEM METHODS ---
    fun showGrid(): Boolean = serviceInstance?.showGrid() ?: false
    fun hideGrid(): Boolean = serviceInstance?.hideGrid() ?: false
    fun resetGrid(): Boolean = serviceInstance?.resetGrid() ?: false
    fun clickHere(): Boolean = serviceInstance?.clickHere() ?: false
    fun selectGridCell(cellNumber: Int): Boolean = serviceInstance?.selectGridCell(cellNumber) ?: false

    /**
     * Displays number badge overlays over all clickable UI elements on screen.
     */
    fun showNumbers(): Boolean {
        val service = serviceInstance
        if (service != null) {
            val result = service.showNumberOverlays()
            Log.i(TAG, "Executed showNumbers(): $result")
            return result
        } else {
            Log.w(TAG, "Unable to show numbers: VoiceAccessibilityService is not connected.")
            return false
        }
    }

    /**
     * Hides and removes all number badge overlays.
     */
    fun hideNumbers(): Boolean {
        val service = serviceInstance
        if (service != null) {
            val result = service.hideNumberOverlays()
            Log.i(TAG, "Executed hideNumbers(): $result")
            return result
        } else {
            Log.w(TAG, "Unable to hide numbers: VoiceAccessibilityService is not connected.")
            return false
        }
    }

    /**
     * Clicks the element corresponding to mapped badge number ("Tap 5").
     */
    fun tapNumber(number: Int): Boolean {
        val service = serviceInstance
        if (service != null) {
            val result = service.tapNumber(number)
            Log.i(TAG, "Executed tapNumber($number): $result")
            return result
        } else {
            Log.w(TAG, "Unable to tap number $number: VoiceAccessibilityService is not connected.")
            return false
        }
    }

    /**
     * Executes an Android global action (e.g. GLOBAL_ACTION_HOME, GLOBAL_ACTION_BACK, GLOBAL_ACTION_RECENTS).
     * @return true if action was successfully dispatched to system.
     */
    fun executeGlobalAction(action: Int): Boolean {
        val service = serviceInstance
        if (service != null) {
            val result = service.performGlobalAction(action)
            Log.i(TAG, "Executed global action code $action: $result")
            return result
        } else {
            Log.w(TAG, "Unable to execute action $action: VoiceAccessibilityService is not connected.")
            return false
        }
    }

    /**
     * Finds and clicks a UI element by text or content description ("Tap Search", "Tap Install").
     */
    fun performClickByText(targetText: String): Boolean {
        val service = serviceInstance
        if (service != null) {
            val result = service.performClickByText(targetText)
            Log.i(TAG, "Executed performClickByText('$targetText'): $result")
            return result
        } else {
            Log.w(TAG, "Unable to perform click for '$targetText': VoiceAccessibilityService is not connected.")
            return false
        }
    }

    /**
     * Executes iPhone-style gesture navigation (Swipe Up, Swipe Down, Swipe Left, Swipe Right).
     */
    fun performSwipeGesture(gestureType: GestureType): Boolean {
        val service = serviceInstance
        if (service != null) {
            val result = service.performSwipeGesture(gestureType)
            Log.i(TAG, "Executed performSwipeGesture($gestureType): $result")
            return result
        } else {
            Log.w(TAG, "Unable to perform gesture $gestureType: VoiceAccessibilityService is not connected.")
            return false
        }
    }

    /**
     * Launches System Accessibility Settings so the user can enable Voice Control service.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open Accessibility Settings", e)
        }
    }
}
