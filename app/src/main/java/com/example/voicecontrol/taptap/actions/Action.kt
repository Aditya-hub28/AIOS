package com.example.voicecontrol.taptap.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.example.voicecontrol.manager.VoiceControlManager
import com.example.voicecontrol.service.VoiceAccessibilityService

interface TapTapAction {
    val id: String
    val name: String
    fun execute(context: Context): Boolean
}

/**
 * Toggles Voice Control Assistant ON or OFF.
 */
class ToggleVoiceControlAction : TapTapAction {
    override val id: String = "toggle_voice_control"
    override val name: String = "Toggle Voice Control"

    override fun execute(context: Context): Boolean {
        Log.i("TapTapAction", "Executing Toggle Voice Control Action")
        VoiceControlManager.toggleVoiceControl(context.applicationContext)
        return true
    }
}

/**
 * Toggles Camera Flashlight Torch.
 */
class FlashlightAction : TapTapAction {
    override val id: String = "flashlight"
    override val name: String = "Toggle Flashlight"
    private var isTorchOn = false

    override fun execute(context: Context): Boolean {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            val cameraId = cameraManager?.cameraIdList?.firstOrNull()
            if (cameraId != null) {
                isTorchOn = !isTorchOn
                cameraManager.setTorchMode(cameraId, isTorchOn)
                Toast.makeText(context, if (isTorchOn) "🔦 Flashlight ON" else "🔦 Flashlight OFF", Toast.LENGTH_SHORT).show()
                true
            } else false
        } catch (e: Exception) {
            Log.e("TapTapAction", "Error toggling torch", e)
            false
        }
    }
}

/**
 * Captures System Screenshot via Accessibility Service.
 */
class ScreenshotAction : TapTapAction {
    override val id: String = "screenshot"
    override val name: String = "Take Screenshot"

    override fun execute(context: Context): Boolean {
        val service = VoiceAccessibilityService.instance
        return if (service != null) {
            service.takeScreenshotAction()
            Toast.makeText(context, "📸 Taking Screenshot", Toast.LENGTH_SHORT).show()
            true
        } else {
            Toast.makeText(context, "❌ Enable Accessibility Service for Screenshots", Toast.LENGTH_SHORT).show()
            false
        }
    }
}

/**
 * Expands System Quick Settings / Notifications.
 */
class QuickSettingsAction : TapTapAction {
    override val id: String = "quick_settings"
    override val name: String = "Open Quick Settings"

    override fun execute(context: Context): Boolean {
        val service = VoiceAccessibilityService.instance
        return if (service != null) {
            service.openQuickSettings()
            true
        } else false
    }
}

/**
 * Toggles Media Play / Pause.
 */
class MediaAction : TapTapAction {
    override val id: String = "media_play_pause"
    override val name: String = "Media Play/Pause"

    override fun execute(context: Context): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
        val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        audioManager.dispatchMediaKeyEvent(downEvent)
        audioManager.dispatchMediaKeyEvent(upEvent)
        Toast.makeText(context, "⏯ Media Play/Pause", Toast.LENGTH_SHORT).show()
        return true
    }
}

/**
 * Locks Device Screen via Accessibility.
 */
class LockScreenAction : TapTapAction {
    override val id: String = "lock_screen"
    override val name: String = "Lock Screen"

    override fun execute(context: Context): Boolean {
        val service = VoiceAccessibilityService.instance
        return if (service != null) {
            service.performGlobalLockScreen()
            true
        } else false
    }
}

/**
 * Launches specified application package.
 */
class LaunchAppAction(private val packageName: String = "com.android.settings") : TapTapAction {
    override val id: String = "launch_app"
    override val name: String = "Launch App"

    override fun execute(context: Context): Boolean {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            true
        } else false
    }
}
