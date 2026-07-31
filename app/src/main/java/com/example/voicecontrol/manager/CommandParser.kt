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
     * Intent to display number overlays over all clickable elements on screen.
     */
    object ShowNumbers : VoiceCommand

    /**
     * Intent to remove and hide all number overlays.
     */
    object HideNumbers : VoiceCommand

    /**
     * Intent to click element associated with a badge number ("Tap 5", "5").
     * @param number Mapped badge number integer.
     */
    data class TapNumber(val number: Int) : VoiceCommand

    /**
     * Intent to display full-screen 3x3 Grid Overlay.
     */
    object ShowGrid : VoiceCommand

    /**
     * Intent to hide full-screen 3x3 Grid Overlay.
     */
    object HideGrid : VoiceCommand

    /**
     * Intent to reset Grid Overlay zoom level.
     */
    object ResetGrid : VoiceCommand

    /**
     * Intent to perform gesture click at center of active Grid region.
     */
    object ClickHere : VoiceCommand

    /**
     * Unrecognized or unhandled voice command.
     * @param rawText Full original spoken text.
     */
    data class Unknown(val rawText: String) : VoiceCommand
}

/**
 * CommandParser parses recognized text into structured VoiceCommand instances.
 * Includes extensive fuzzy speech variations for Show Grid ("so grid", "saw grid", "shoe grid", "show grit").
 */
object CommandParser {

    private val OPEN_PREFIXES = listOf("open", "launch", "start", "run", "go to")
    private val TAP_PREFIXES = listOf("tap", "tab", "top", "tub", "tip", "app", "cap", "tape", "click", "press", "select", "touch")

    private val SPOKEN_NUMBER_MAP = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "to" to 2, "too" to 2,
        "three" to 3, "four" to 4, "for" to 4, "fore" to 4,
        "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "ate" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20,
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5
    )

    /**
     * Parses a raw spoken text string and extracts the intent.
     */
    fun parse(rawText: String): VoiceCommand {
        val trimmedText = rawText.trim()
        if (trimmedText.isBlank()) {
            return VoiceCommand.Unknown(rawText)
        }

        val lowerText = trimmedText.lowercase(Locale.getDefault())

        // 1. Check for Grid Overlay commands (including phonetic fuzzy variations: "so grid", "saw grid", "shoe grid", "show grit", "sure grid")
        when (lowerText) {
            "show grid", "show the grid", "display grid", "grid on", "open grid",
            "so grid", "saw grid", "shoe grid", "sho grid", "sow grid", "show great", "show grit", "show gre", "show gridd", "show gridr", "show the greid", "show grd", "so great", "so grit", "show red", "so red", "sure grid" -> {
                return VoiceCommand.ShowGrid
            }
            "hide grid", "hide the grid", "remove grid", "grid off", "close grid",
            "hi grid", "height grid", "high grid", "hide great", "hide grit" -> {
                return VoiceCommand.HideGrid
            }
            "reset grid", "reset the grid", "clear grid zoom", "full grid",
            "re set grid", "recet grid", "reset great" -> {
                return VoiceCommand.ResetGrid
            }
            "click here", "press here", "tap here" -> {
                return VoiceCommand.ClickHere
            }
        }

        // 2. Check for Show Numbers / Hide Numbers voice commands (including "so numbers", "saw numbers")
        when (lowerText) {
            "show numbers", "show number", "display numbers", "numbers on", "show badge numbers",
            "so numbers", "so number", "saw numbers", "shoe numbers" -> {
                return VoiceCommand.ShowNumbers
            }
            "hide numbers", "hide number", "remove numbers", "numbers off", "clear numbers",
            "hi numbers", "height numbers" -> {
                return VoiceCommand.HideNumbers
            }
        }

        // 3. Check for Tap Number / Grid Cell voice commands ("Tap 5", "Tap number 5", "Click 12", or pure digits "5")
        val numberFromTap = parseTapNumberCommand(lowerText, trimmedText)
        if (numberFromTap != null) {
            return VoiceCommand.TapNumber(numberFromTap)
        }

        // 4. Check for Text-Based Click voice commands ("Tap Search", "Click Settings")
        for (prefix in TAP_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                val targetText = trimmedText.substring(prefix.length).trim()
                if (targetText.isNotBlank()) {
                    return VoiceCommand.TapElement(targetText)
                }
            }
        }

        // 5. Check for Gesture Navigation voice commands (Swipe Up, Swipe Down, Swipe Left, Swipe Right)
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

        // 6. Check for Global Action voice commands
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

        // 7. Check for App Opening voice commands
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
     * Helper to parse integer digits or spoken number words ("one" -> 1, "two" -> 2, etc.).
     */
    private fun parseSpokenNumber(text: String): Int? {
        val trimmed = text.trim()
        trimmed.toIntOrNull()?.let { return it }
        return SPOKEN_NUMBER_MAP[trimmed.lowercase(Locale.getDefault())]
    }

    /**
     * Parses tap number patterns such as "tap 5", "tab 5", "click 12", "number 3", "5", "five", "7", "seven".
     */
    private fun parseTapNumberCommand(lowerText: String, trimmedText: String): Int? {
        // 1. Direct number check without saying "tap" (e.g. "5", "five", "1", "one", "7", "seven", "9", "nine")
        parseSpokenNumber(lowerText)?.let { return it }

        // 2. Number preceded by any tap prefix ("tap 5", "tab 5", "top five", "tap number 3", "tab #4")
        for (prefix in TAP_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                val remainder = lowerText.substring(prefix.length).trim()
                val targetNumString = if (remainder.startsWith("number ")) {
                    remainder.substring("number ".length).trim()
                } else if (remainder.startsWith("#")) {
                    remainder.substring(1).trim()
                } else {
                    remainder
                }
                parseSpokenNumber(targetNumString)?.let { return it }
            }
        }

        if (lowerText.startsWith("number ")) {
            val numStr = lowerText.substring("number ".length).trim()
            parseSpokenNumber(numStr)?.let { return it }
        }

        return null
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
