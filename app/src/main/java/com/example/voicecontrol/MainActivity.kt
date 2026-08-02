package com.example.voicecontrol

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.voicecontrol.ui.VoiceControlScreen
import com.example.voicecontrol.ui.VoiceViewModel
import com.example.voicecontrol.ui.theme.VoiceControlTheme

/**
 * Main Activity entry point for the VoiceControl application.
 * Intercepts hardware Volume Up double press to toggle Voice Control.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: VoiceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        com.example.voicecontrol.manager.VoiceControlManager.init(applicationContext)
        setContent {
            VoiceControlTheme {
                VoiceControlScreen(viewModel = viewModel)
            }
        }
    }

    /**
     * Intercepts hardware key events.
     * Detects double Volume Up key press within 1000ms to toggle Voice Control ON/OFF.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (viewModel.onVolumeUpPressed(hasPermission)) {
                return true // Double press detected and consumed cleanly
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean {
        if (ev != null) {
            com.example.voicecontrol.util.ScreenTouchTracker.onScreenTouch()
        }
        return super.dispatchTouchEvent(ev)
    }
}
