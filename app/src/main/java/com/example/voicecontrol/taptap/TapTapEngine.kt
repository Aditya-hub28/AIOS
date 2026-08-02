package com.example.voicecontrol.taptap

import android.content.Context
import android.util.Log
import com.example.voicecontrol.taptap.actions.FlashlightAction
import com.example.voicecontrol.taptap.actions.LaunchAppAction
import com.example.voicecontrol.taptap.actions.LockScreenAction
import com.example.voicecontrol.taptap.actions.MediaAction
import com.example.voicecontrol.taptap.actions.QuickSettingsAction
import com.example.voicecontrol.taptap.actions.ScreenshotAction
import com.example.voicecontrol.taptap.actions.TapTapAction
import com.example.voicecontrol.taptap.actions.ToggleVoiceControlAction
import com.example.voicecontrol.taptap.sensors.TapTapSensor
import com.example.voicecontrol.taptap.settings.TapTapSettings

/**
 * Master Engine for the TapTap section.
 * Manages sensor lifecycle, preference configuration, and action execution for Double & Triple taps.
 */
object TapTapEngine {

    private const val TAG = "TapTapEngine"

    private var tapSensor: TapTapSensor? = null
    private lateinit var settings: TapTapSettings

    private val availableActions: Map<String, TapTapAction> = mapOf(
        TapTapSettings.ACTION_TOGGLE_VOICE_CONTROL to ToggleVoiceControlAction(),
        TapTapSettings.ACTION_FLASHLIGHT to FlashlightAction(),
        TapTapSettings.ACTION_SCREENSHOT to ScreenshotAction(),
        TapTapSettings.ACTION_QUICK_SETTINGS to QuickSettingsAction(),
        TapTapSettings.ACTION_MEDIA_PLAY_PAUSE to MediaAction(),
        TapTapSettings.ACTION_LOCK_SCREEN to LockScreenAction(),
        TapTapSettings.ACTION_LAUNCH_APP to LaunchAppAction()
    )

    fun init(context: Context) {
        val appContext = context.applicationContext
        settings = TapTapSettings(appContext)

        if (tapSensor == null) {
            tapSensor = TapTapSensor(
                context = appContext,
                onDoubleTap = { executeDoubleTapAction(appContext) },
                onTripleTap = { executeTripleTapAction(appContext) }
            )
            tapSensor?.start()
            Log.i(TAG, "TapTapEngine initialized and started.")
        }
    }

    fun start() {
        tapSensor?.start()
    }

    fun stop() {
        tapSensor?.stop()
    }

    private fun executeDoubleTapAction(context: Context) {
        val actionId = settings.doubleTapAction
        Log.i(TAG, "Executing Double Tap Action: $actionId")
        val action = availableActions[actionId] ?: ToggleVoiceControlAction()
        action.execute(context)
    }

    private fun executeTripleTapAction(context: Context) {
        val actionId = settings.tripleTapAction
        Log.i(TAG, "Executing Triple Tap Action: $actionId")
        val action = availableActions[actionId] ?: ScreenshotAction()
        action.execute(context)
    }
}
