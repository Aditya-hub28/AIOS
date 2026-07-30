package com.example.voicecontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.voicecontrol.manager.AccessibilityCommandManager
import com.example.voicecontrol.manager.AppLaunchResult
import com.example.voicecontrol.manager.AppLauncherManager
import com.example.voicecontrol.manager.CommandParser
import com.example.voicecontrol.manager.SpeechRecognitionListener
import com.example.voicecontrol.manager.SpeechRecognizerManager
import com.example.voicecontrol.manager.VoiceCommand
import com.example.voicecontrol.service.VoiceControlService
import com.example.voicecontrol.state.VoiceUiState
import com.example.voicecontrol.util.VibrationUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel managing business logic for continuous voice recognition, app launching,
 * global accessibility actions, hardware key double-press toggle, and foreground service lifecycle.
 */
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    private val speechManager = SpeechRecognizerManager(application.applicationContext)
    private val appLauncherManager = AppLauncherManager(application.applicationContext)

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val _isVoiceControlActive = MutableStateFlow(true)
    val isVoiceControlActive: StateFlow<Boolean> = _isVoiceControlActive.asStateFlow()

    val isAccessibilityServiceConnected: StateFlow<Boolean> = AccessibilityCommandManager.isServiceConnected

    // Timing tracking for Double Volume Up press detection & debounce
    private var lastVolumeUpTime: Long = 0L
    private var lastToggleTime: Long = 0L

    // Duplicate command prevention tracking
    private var lastExecutedCommand: String = ""
    private var lastExecutedTime: Long = 0L

    // Coroutine job for continuous listening auto-restart
    private var autoRestartJob: Job? = null

    // Holds the latest partial or final recognized text
    private var currentText: String = ""

    /**
     * Opens Android System Accessibility Settings.
     */
    fun openAccessibilitySettings() {
        AccessibilityCommandManager.openAccessibilitySettings(getApplication<Application>().applicationContext)
    }

    /**
     * Handles hardware Volume Up key press.
     * Returns true if a double press was detected within 1000ms and consumed.
     */
    fun onVolumeUpPressed(hasPermission: Boolean): Boolean {
        val currentTime = System.currentTimeMillis()

        // Cooldown check: ignore if toggled within last 1000ms
        if (currentTime - lastToggleTime < 1000L) {
            return false
        }

        // Check if second press occurred within 1000ms of first press
        if (currentTime - lastVolumeUpTime <= 1000L && lastVolumeUpTime > 0L) {
            lastToggleTime = currentTime
            lastVolumeUpTime = 0L

            toggleVoiceControl(hasPermission)
            return true
        } else {
            lastVolumeUpTime = currentTime
            return false
        }
    }

    /**
     * Toggles Voice Control state ON or OFF with haptic vibration feedback.
     */
    fun toggleVoiceControl(hasPermission: Boolean) {
        val context = getApplication<Application>().applicationContext
        VibrationUtils.vibrate(context, 100L)

        val newActiveState = !_isVoiceControlActive.value
        _isVoiceControlActive.value = newActiveState

        if (newActiveState) {
            // Enable Voice Control & Start Foreground Service Notification
            VoiceControlService.start(context)
            if (hasPermission) {
                startListening()
            } else {
                _uiState.value = VoiceUiState.Error(
                    message = "Voice Control Active. Microphone permission required.",
                    isPermissionError = true
                )
            }
        } else {
            // Disable Voice Control & Stop Foreground Service
            autoRestartJob?.cancel()
            speechManager.cancel()
            VoiceControlService.stop(context)
            _uiState.value = VoiceUiState.Disabled
        }
    }

    /**
     * Called when the user clicks the main microphone action button.
     */
    fun onMicButtonClicked(hasPermission: Boolean) {
        if (!_isVoiceControlActive.value) {
            toggleVoiceControl(hasPermission)
            return
        }

        when (_uiState.value) {
            is VoiceUiState.Listening -> {
                stopListening()
            }
            else -> {
                if (hasPermission) {
                    startListening()
                } else {
                    _uiState.value = VoiceUiState.Error(
                        message = "Microphone permission is required to use Voice Control.",
                        isPermissionError = true
                    )
                }
            }
        }
    }

    /**
     * Handles the outcome of runtime permission requests.
     */
    fun onPermissionResult(isGranted: Boolean) {
        val context = getApplication<Application>().applicationContext
        if (isGranted) {
            _isVoiceControlActive.value = true
            VoiceControlService.start(context)
            startListening()
        } else {
            _uiState.value = VoiceUiState.Error(
                message = "Microphone permission was denied. Please grant permission in App Settings.",
                isPermissionError = true
            )
        }
    }

    /**
     * Starts the speech recognizer service.
     */
    fun startListening() {
        autoRestartJob?.cancel()
        if (!_isVoiceControlActive.value) return

        if (!speechManager.isAvailable()) {
            _uiState.value = VoiceUiState.Error(
                message = "Speech recognition service is not available on this device."
            )
            return
        }

        _uiState.value = VoiceUiState.Listening(rmsdB = 0f, partialText = currentText)

        speechManager.startListening(object : SpeechRecognitionListener {
            override fun onReadyForSpeech() {
                if (_isVoiceControlActive.value) {
                    _uiState.value = VoiceUiState.Listening(rmsdB = 0f, partialText = currentText)
                }
            }

            override fun onBeginningOfSpeech() {
                if (_isVoiceControlActive.value) {
                    _uiState.value = VoiceUiState.Listening(rmsdB = 2f, partialText = currentText)
                }
            }

            override fun onRmsChanged(rmsdB: Float) {
                if (!_isVoiceControlActive.value) return
                val currentState = _uiState.value
                val existingPartial = if (currentState is VoiceUiState.Listening) {
                    currentState.partialText
                } else {
                    currentText
                }
                _uiState.value = VoiceUiState.Listening(rmsdB = rmsdB, partialText = existingPartial)
            }

            override fun onPartialResults(partialText: String) {
                if (!_isVoiceControlActive.value) return
                currentText = partialText
                _uiState.value = VoiceUiState.Listening(rmsdB = 5f, partialText = partialText)
            }

            override fun onEndOfSpeech() {
                if (_isVoiceControlActive.value) {
                    _uiState.value = VoiceUiState.Processing
                }
            }

            override fun onResults(recognizedText: String) {
                if (!_isVoiceControlActive.value) return
                currentText = recognizedText
                handleRecognizedSpeech(recognizedText)
                scheduleContinuousAutoRestart()
            }

            override fun onError(errorMessage: String) {
                if (!_isVoiceControlActive.value) return
                _uiState.value = VoiceUiState.Error(message = errorMessage)
                scheduleContinuousAutoRestart()
            }
        })
    }

    /**
     * Schedules automatic restart of speech recognition after a safe delay (continuous loop).
     */
    private fun scheduleContinuousAutoRestart() {
        if (!_isVoiceControlActive.value) return
        autoRestartJob?.cancel()
        autoRestartJob = viewModelScope.launch {
            delay(750L)
            if (_isVoiceControlActive.value) {
                startListening()
            }
        }
    }

    /**
     * Evaluates recognized speech string to check for actionable voice commands.
     * Includes duplicate command debounce to prevent repeated executions.
     */
    private fun handleRecognizedSpeech(recognizedText: String) {
        val now = System.currentTimeMillis()
        if (recognizedText.equals(lastExecutedCommand, ignoreCase = true) && (now - lastExecutedTime) < 1800L) {
            return
        }

        lastExecutedCommand = recognizedText
        lastExecutedTime = now

        when (val command = CommandParser.parse(recognizedText)) {
            is VoiceCommand.GlobalAction -> {
                val actionExecuted = AccessibilityCommandManager.executeGlobalAction(command.actionId)
                if (actionExecuted) {
                    _uiState.value = VoiceUiState.Success(
                        recognizedText = "Action: ${command.actionName}"
                    )
                } else {
                    _uiState.value = VoiceUiState.Error(
                        message = "Accessibility Service is not enabled. Tap 'Enable Accessibility' below to turn on Voice Control in System Settings."
                    )
                }
            }
            is VoiceCommand.OpenApp -> {
                val targetApp = command.appName
                _uiState.value = VoiceUiState.LaunchingApp(appName = targetApp)

                when (val result = appLauncherManager.launchApp(targetApp)) {
                    is AppLaunchResult.Success -> {
                        _uiState.value = VoiceUiState.Success(
                            recognizedText = "Opening ${result.appName}..."
                        )
                    }
                    is AppLaunchResult.NotFound -> {
                        _uiState.value = VoiceUiState.Error(
                            message = "App '${result.targetAppName}' is not installed on this device."
                        )
                    }
                    is AppLaunchResult.Error -> {
                        _uiState.value = VoiceUiState.Error(
                            message = result.errorMessage
                        )
                    }
                }
            }
            is VoiceCommand.Unknown -> {
                _uiState.value = VoiceUiState.Success(recognizedText = recognizedText)
            }
        }
    }

    /**
     * Manually stops listening.
     */
    fun stopListening() {
        autoRestartJob?.cancel()
        _uiState.value = VoiceUiState.Processing
        speechManager.stopListening()
    }

    /**
     * Clears recognized text and resets UI to Idle or Disabled.
     */
    fun clearText() {
        currentText = ""
        _uiState.value = if (_isVoiceControlActive.value) VoiceUiState.Idle else VoiceUiState.Disabled
    }

    /**
     * Dismisses error state and resets back to Idle or Disabled.
     */
    fun resetError() {
        _uiState.value = if (_isVoiceControlActive.value) VoiceUiState.Idle else VoiceUiState.Disabled
    }

    override fun onCleared() {
        super.onCleared()
        autoRestartJob?.cancel()
        speechManager.destroy()
    }
}
