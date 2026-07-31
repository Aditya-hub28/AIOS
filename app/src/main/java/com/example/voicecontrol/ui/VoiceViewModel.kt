package com.example.voicecontrol.ui

import android.app.Application
import android.util.Log
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
 * ViewModel managing business logic for sub-500ms voice recognition, real-time command execution,
 * voice-controlled scrolling, app launching, global actions, and foreground service lifecycle.
 */
class VoiceViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PERF_TAG = "PERF"
    }

    private val speechManager = SpeechRecognizerManager(application.applicationContext)
    private val appLauncherManager = AppLauncherManager(application.applicationContext)

    private val _uiState = MutableStateFlow<VoiceUiState>(VoiceUiState.Idle)
    val uiState: StateFlow<VoiceUiState> = _uiState.asStateFlow()

    private val _isVoiceControlActive = MutableStateFlow(true)
    val isVoiceControlActive: StateFlow<Boolean> = _isVoiceControlActive.asStateFlow()

    val isAccessibilityServiceConnected: StateFlow<Boolean> = AccessibilityCommandManager.isServiceConnected

    // Timing tracking for Double Volume Up press detection
    private var lastVolumeUpTime: Long = 0L
    private var lastToggleTime: Long = 0L

    // Duplicate command prevention tracking (500ms window)
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

        if (currentTime - lastToggleTime < 1000L) {
            return false
        }

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

                // Real-time sub-500ms command evaluation on partial speech results
                evaluateAndExecuteCommand(partialText, isPartial = true)
            }

            override fun onEndOfSpeech() {
                if (_isVoiceControlActive.value) {
                    _uiState.value = VoiceUiState.Processing
                }
            }

            override fun onResults(recognizedText: String) {
                if (!_isVoiceControlActive.value) return
                currentText = recognizedText
                evaluateAndExecuteCommand(recognizedText, isPartial = false)
                scheduleContinuousAutoRestart(immediate = false)
            }

            override fun onError(errorMessage: String) {
                if (!_isVoiceControlActive.value) return
                _uiState.value = VoiceUiState.Error(message = errorMessage)
                scheduleContinuousAutoRestart(immediate = false)
            }
        })
    }

    /**
     * Schedules automatic restart of speech recognition for continuous listening.
     */
    private fun scheduleContinuousAutoRestart(immediate: Boolean = false) {
        if (!_isVoiceControlActive.value) return
        autoRestartJob?.cancel()
        autoRestartJob = viewModelScope.launch {
            if (!immediate) {
                delay(300L) // Fast 300ms delay between cycles
            }
            if (_isVoiceControlActive.value) {
                startListening()
            }
        }
    }

    /**
     * Evaluates recognized speech (partial or final) and executes commands instantly (< 500ms).
     * Includes precision [PERF] timestamp logging at every stage.
     */
    private fun evaluateAndExecuteCommand(recognizedText: String, isPartial: Boolean) {
        val now = System.currentTimeMillis()

        // Debounce exact same command within 600ms
        if (recognizedText.equals(lastExecutedCommand, ignoreCase = true) && (now - lastExecutedTime) < 600L) {
            return
        }

        val command = CommandParser.parse(recognizedText)
        if (command is VoiceCommand.Unknown) {
            if (!isPartial) {
                _uiState.value = VoiceUiState.Success(recognizedText = recognizedText)
            }
            return
        }

        // --- PERFORMANCE LOGGING PIPELINE ---
        val t1_recognitionComplete = System.currentTimeMillis()
        Log.i(PERF_TAG, "[PERF] 1. Recognition Complete (${if (isPartial) "Partial" else "Final"}): $t1_recognitionComplete ms | Text: \"$recognizedText\"")

        val t2_commandParsed = System.currentTimeMillis()
        Log.i(PERF_TAG, "[PERF] 2. Command Parsed: $t2_commandParsed ms | Command: $command")

        val t3_executionStarted = System.currentTimeMillis()
        Log.i(PERF_TAG, "[PERF] 3. Command Execution Started: $t3_executionStarted ms")

        lastExecutedCommand = recognizedText
        lastExecutedTime = now

        // Cancel speech recognizer immediately to avoid silence timeout wait
        speechManager.cancel()

        when (command) {
            is VoiceCommand.Scroll -> {
                val t4_triggered = System.currentTimeMillis()
                Log.i(PERF_TAG, "[PERF] 4. Accessibility Action Triggered: $t4_triggered ms | Action: ${command.commandName}")

                val success = AccessibilityCommandManager.performScroll(command.isForward)
                val t5_completed = System.currentTimeMillis()
                val totalLatency = t5_completed - t1_recognitionComplete

                Log.i(PERF_TAG, "[PERF] 5. Action Completed: $t5_completed ms | Total Latency: $totalLatency ms")

                if (success) {
                    _uiState.value = VoiceUiState.Success(recognizedText = "Action: ${command.commandName}")
                } else {
                    _uiState.value = VoiceUiState.Error(
                        message = "Unable to scroll. Ensure Accessibility Service is enabled."
                    )
                }
            }
            is VoiceCommand.GlobalAction -> {
                val t4_triggered = System.currentTimeMillis()
                Log.i(PERF_TAG, "[PERF] 4. Accessibility Action Triggered: $t4_triggered ms | Action: ${command.actionName}")

                val actionExecuted = AccessibilityCommandManager.executeGlobalAction(command.actionId)
                val t5_completed = System.currentTimeMillis()
                val totalLatency = t5_completed - t1_recognitionComplete

                Log.i(PERF_TAG, "[PERF] 5. Action Completed: $t5_completed ms | Total Latency: $totalLatency ms")

                if (actionExecuted) {
                    _uiState.value = VoiceUiState.Success(recognizedText = "Action: ${command.actionName}")
                } else {
                    _uiState.value = VoiceUiState.Error(
                        message = "Accessibility Service is not enabled. Tap 'Enable' above to turn on in System Settings."
                    )
                }
            }
            is VoiceCommand.OpenApp -> {
                val targetApp = command.appName
                _uiState.value = VoiceUiState.LaunchingApp(appName = targetApp)

                val t4_triggered = System.currentTimeMillis()
                Log.i(PERF_TAG, "[PERF] 4. App Launch Triggered: $t4_triggered ms | App: $targetApp")

                val result = appLauncherManager.launchApp(targetApp)
                val t5_completed = System.currentTimeMillis()
                val totalLatency = t5_completed - t1_recognitionComplete

                Log.i(PERF_TAG, "[PERF] 5. Action Completed: $t5_completed ms | Total Latency: $totalLatency ms")

                when (result) {
                    is AppLaunchResult.Success -> {
                        _uiState.value = VoiceUiState.Success(recognizedText = "Opening ${result.appName}...")
                    }
                    is AppLaunchResult.NotFound -> {
                        _uiState.value = VoiceUiState.Error(message = "App '${result.targetAppName}' is not installed on this device.")
                    }
                    is AppLaunchResult.Error -> {
                        _uiState.value = VoiceUiState.Error(message = result.errorMessage)
                    }
                }
            }
            is VoiceCommand.Unknown -> {}
        }

        // Instantly restart continuous listening
        scheduleContinuousAutoRestart(immediate = true)
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
