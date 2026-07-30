package com.example.voicecontrol.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.voicecontrol.manager.AccessibilityCommandManager

/**
 * Dedicated Accessibility Service for executing Android global actions
 * (Home, Back, Recent Apps) and serving as the foundation for future UI automation.
 */
class VoiceAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "VoiceAccessibility"
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "VoiceAccessibilityService connected to System Accessibility Framework.")
        AccessibilityCommandManager.registerService(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Accessibility events received (available for future window content inspection)
    }

    override fun onInterrupt() {
        Log.w(TAG, "VoiceAccessibilityService interrupted by system.")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        Log.i(TAG, "VoiceAccessibilityService unbound.")
        AccessibilityCommandManager.unregisterService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "VoiceAccessibilityService destroyed.")
        AccessibilityCommandManager.unregisterService()
    }
}
