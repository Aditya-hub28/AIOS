package com.example.voicecontrol.manager

import android.accessibilityservice.AccessibilityService
import java.util.Locale

/**
 * Enum defining supported gesture navigation directions.
 */
enum class GestureType {
    SWIPE_UP,
    SWIPE_DOWN,
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
     * Intent to execute gesture navigation (Swipe Up, Swipe Down, Swipe Left, Swipe Right).
     * @param type GestureType enum indicating gesture direction.
     * @param label Human-readable description.
     */
    data class SwipeGesture(val type: GestureType, val label: String) : VoiceCommand

    /**
     * Intent to click a UI element by text or content description ("Tap Search", "Tap Install").
     * @param targetText Element text/description to match and click.
     */
    data class TapElement(val targetText: String) : VoiceCommand

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
    private val TAP_PREFIXES = listOf("tap", "click", "press", "select", "touch")

    /**
     * Parses a raw spoken text string and extracts the intent.
     */
    fun parse(rawText: String): VoiceCommand {
        val trimmedText = rawText.trim()
        if (trimmedText.isBlank()) {
            return VoiceCommand.Unknown(rawText)
        }

        val lowerText = trimmedText.lowercase(Locale.getDefault())

        // 1. Check for Text-Based Click voice commands ("Tap Search", "Click Settings")
        for (prefix in TAP_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                val targetText = trimmedText.substring(prefix.length).trim()
                if (targetText.isNotBlank()) {
                    return VoiceCommand.TapElement(targetText)
                }
            }
        }

        // 2. Check for Gesture Navigation voice commands (Swipe Up, Swipe Down, Swipe Left, Swipe Right)
        when (lowerText) {
            "swipe up", "swipe upward", "upward swipe", "scroll down", "down", "page down" -> {
                return VoiceCommand.SwipeGesture(GestureType.SWIPE_UP, "Swipe Up")
            }
            "swipe down", "swipe downward", "downward swipe", "scroll up", "up", "page up" -> {
                return VoiceCommand.SwipeGesture(GestureType.SWIPE_DOWN, "Swipe Down")
            }
            "swipe left", "swipe leftward", "leftward swipe", "next tab", "slide left" -> {
                return VoiceCommand.SwipeGesture(GestureType.SWIPE_LEFT, "Swipe Left")
            }
            "swipe right", "swipe rightward", "rightward swipe", "previous tab", "slide right" -> {
                return VoiceCommand.SwipeGesture(GestureType.SWIPE_RIGHT, "Swipe Right")
            }
        }

        // 3. Check for Global Action voice commands
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

        // 4. Check for App Opening voice commands
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
