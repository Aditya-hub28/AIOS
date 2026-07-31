package com.example.voicecontrol.manager

import android.accessibilityservice.AccessibilityService
import java.util.Locale

/**
 * Sealed class representing parsed voice intents.
 */
sealed interface VoiceCommand {
    /**
     * Intent to open/launch an installed application.
     * @param appName Target application name extracted from spoken command.
     */
    data class OpenApp(val appName: String) : VoiceCommand

    /**
     * Intent to execute an Android System Global Action via Accessibility Service.
     * @param actionId AccessibilityService.GLOBAL_ACTION_* integer ID.
     * @param actionName Human-readable action label (e.g. "Home", "Back", "Recent Apps").
     */
    data class GlobalAction(val actionId: Int, val actionName: String) : VoiceCommand

    /**
     * Intent to scroll the active window up or down.
     * @param isForward True for Scroll Down (forward), False for Scroll Up (backward).
     * @param commandName Human-readable command description.
     */
    data class Scroll(val isForward: Boolean, val commandName: String) : VoiceCommand

    /**
     * Unrecognized or unhandled voice command.
     * @param rawText Full original spoken text.
     */
    data class Unknown(val rawText: String) : VoiceCommand
}

/**
 * CommandParser parses recognized text into structured VoiceCommand instances.
 */
object CommandParser {

    private val OPEN_PREFIXES = listOf("open", "launch", "start", "run", "go to")

    /**
     * Parses a raw spoken text string and extracts the intent.
     */
    fun parse(rawText: String): VoiceCommand {
        val trimmedText = rawText.trim()
        if (trimmedText.isBlank()) {
            return VoiceCommand.Unknown(rawText)
        }

        val lowerText = trimmedText.lowercase(Locale.getDefault())

        // 1. Check for Scrolling voice commands
        when (lowerText) {
            "scroll down", "down", "scroll page down", "page down", "swipe down", "next page" -> {
                return VoiceCommand.Scroll(isForward = true, commandName = "Scroll Down")
            }
            "scroll up", "up", "scroll page up", "page up", "swipe up", "previous page" -> {
                return VoiceCommand.Scroll(isForward = false, commandName = "Scroll Up")
            }
        }

        // 2. Check for Global Action voice commands
        when (lowerText) {
            "go home", "home", "go to home", "open home", "take me home" -> {
                return VoiceCommand.GlobalAction(
                    actionId = AccessibilityService.GLOBAL_ACTION_HOME,
                    actionName = "Home"
                )
            }
            "back", "go back", "navigate back", "press back" -> {
                return VoiceCommand.GlobalAction(
                    actionId = AccessibilityService.GLOBAL_ACTION_BACK,
                    actionName = "Back"
                )
            }
            "recent apps", "recents", "recent", "open recents", "show recent apps", "overview" -> {
                return VoiceCommand.GlobalAction(
                    actionId = AccessibilityService.GLOBAL_ACTION_RECENTS,
                    actionName = "Recent Apps"
                )
            }
        }

        // 3. Check for App Opening voice commands
        for (prefix in OPEN_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                val extractedName = trimmedText.substring(prefix.length).trim()
                val cleanedName = cleanAppName(extractedName)
                if (cleanedName.isNotBlank()) {
                    return VoiceCommand.OpenApp(cleanedName)
                }
            }
        }

        return VoiceCommand.Unknown(trimmedText)
    }

    /**
     * Cleans up common trailing filler words (e.g., "WhatsApp app" -> "WhatsApp").
     */
    private fun cleanAppName(name: String): String {
        var clean = name.trim()
        if (clean.endsWith(" app", ignoreCase = true)) {
            clean = clean.substring(0, clean.length - 4).trim()
        } else if (clean.endsWith(" application", ignoreCase = true)) {
            clean = clean.substring(0, clean.length - 12).trim()
        }
        return clean
    }
}
