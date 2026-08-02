package com.example.voicecontrol.taptap.gates

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.PowerManager
import android.telephony.TelephonyManager

/**
 * Base Interface for TapTap Gate Condition Monitors.
 * If any registered Gate returns true, tap detection is blocked.
 */
interface TapTapGate {
    fun isBlocked(context: Context): Boolean
}

class ScreenStateGate : TapTapGate {
    override fun isBlocked(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return !pm.isInteractive
    }
}

class CameraGate : TapTapGate {
    private var isCameraActive = false

    fun init(context: Context) {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        cameraManager?.registerAvailabilityCallback(object : CameraManager.AvailabilityCallback() {
            override fun onCameraUnavailable(cameraId: String) {
                isCameraActive = true
            }

            override fun onCameraAvailable(cameraId: String) {
                isCameraActive = false
            }
        }, null)
    }

    override fun isBlocked(context: Context): Boolean {
        return isCameraActive
    }
}

class TelephonyGate : TapTapGate {
    override fun isBlocked(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager ?: return false
        return tm.callState != TelephonyManager.CALL_STATE_IDLE
    }
}
