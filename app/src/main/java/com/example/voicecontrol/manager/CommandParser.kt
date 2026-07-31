package com.example.voicecontrol.manager

import android.accessibilityservice.AccessibilityService
import java.util.Locale

/**
 * Enum defining supported gesture navigation types.
 */
enum class GestureType {
    SCROLL_DOWN,
    SCROLL_UP,
    SWIPE_LEFT,
    SWIPE_RIGHT
}

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
     * Intent to execute gesture navigation (Scroll Down, Scroll Up, Swipe Left, Swipe Right).
     * @param type GestureType enum indicating gesture direction.
     * @param label Human-readable description.
     */
    data class GestureNav(val type: GestureType, val label: String) : VoiceCommand

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

        // 1. Check for Gesture Navigation voice commands (Scroll & Swipe)
        when (lowerText) {
            "scroll down", "down", "scroll page down", "page down" -> {
                return VoiceCommand.GestureNav(GestureType.SCROLL_DOWN, "Scroll Down")
            }
            "scroll up", "up", "scroll page up", "page up" -> {
                return VoiceCommand.GestureNav(GestureType.SCROLL_UP, "Scroll Up")
            }
            "swipe left", "swipe leftward", "next tab", "slide left", "swipe next" -> {
                return VoiceCommand.GestureNav(GestureType.SWIPE_LEFT, "Swipe Left")
            }
            "swipe right", "swipe rightward", "previous tab", "slide right", "swipe back" -> {
                return VoiceCommand.GestureNav(GestureType.SWIPE_RIGHT, "Swipe Right")
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
