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
     * Intent to display or configure dynamic Grid Overlay (optional customRows, customCols).
     */
    data class ShowGrid(val customRows: Int? = null, val customCols: Int? = null) : VoiceCommand

    /**
     * Intent to hide Grid Overlay.
     */
    object HideGrid : VoiceCommand

    /**
     * Intent to reset Grid Overlay zoom level and configuration.
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
 * Supports dynamic grid overlay sizing ("show grid with 7 rows", "show grid with 9 columns").
 */
object CommandParser {

    private val OPEN_PREFIXES = listOf("open", "launch", "start", "run", "go to")
    private val TAP_PREFIXES = listOf("tap", "tab", "top", "tub", "tip", "app", "cap", "tape", "click", "press", "select", "touch")

    private val SPOKEN_NUMBER_MAP = mapOf(
        "zero" to 0, "0" to 0,
        "one" to 1, "wun" to 1, "wan" to 1, "1" to 1,
        "two" to 2, "to" to 2, "too" to 2, "tu" to 2, "2" to 2,
        "three" to 3, "tree" to 3, "free" to 3, "tri" to 3, "3" to 3,
        "four" to 4, "for" to 4, "fore" to 4, "far" to 4, "4" to 4,
        "five" to 5, "faiv" to 5, "fiv" to 5, "5" to 5,
        "six" to 6, "siks" to 6, "sik" to 6, "6" to 6,
        "seven" to 7, "sevan" to 7, "sevin" to 7, "saven" to 7, "7" to 7,
        "eight" to 8, "ate" to 8, "ait" to 8, "eyt" to 8, "8" to 8,
        "nine" to 9, "nain" to 9, "nien" to 9, "nin" to 9, "9" to 9,
        "ten" to 10, "tin" to 10, "tan" to 10, "10" to 10,
        "eleven" to 11, "aleven" to 11, "11" to 11,
        "twelve" to 12, "twelv" to 12, "12" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18,
        "nineteen" to 19, "twenty" to 20,
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5
    )

    private val TENS_MAP = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fourty" to 40,
        "fifty" to 50, "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
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

        // 1. Check for Dynamic Grid Overlay commands
        val gridCmd = parseDynamicGridCommand(lowerText)
        if (gridCmd != null) {
            return gridCmd
        }

        // 2. Check for Show Numbers / Hide Numbers voice commands
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

        // 3. Check for Tap Number / Grid Cell voice commands ("Tap 5", "Tap 17", "22", "five")
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

        // 5. Check for Gesture Navigation voice commands
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
     * Helper to parse integer digits or spoken number words ("one" -> 1, "twenty five" -> 25, etc.).
     */
    fun parseSpokenNumber(text: String): Int? {
        val trimmed = text.trim().lowercase(Locale.getDefault())
        trimmed.toIntOrNull()?.let { return it }

        SPOKEN_NUMBER_MAP[trimmed]?.let { return it }
        TENS_MAP[trimmed]?.let { return it }

        val parts = trimmed.split(Regex("[\\s-]+"))
        if (parts.size == 2) {
            val ten = TENS_MAP[parts[0]]
            val unit = SPOKEN_NUMBER_MAP[parts[1]]
            if (ten != null && unit != null) {
                return ten + unit
            }
        }

        return null
    }

    /**
     * Parses dynamic grid phrases such as "show grid with 7 rows", "show grid with 9 columns", "change grid to 8 rows".
     */
    private fun parseDynamicGridCommand(lowerText: String): VoiceCommand? {
        when (lowerText) {
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

        val isGridPhrase = lowerText.contains("grid") || lowerText.contains("so grid") ||
                lowerText.contains("saw grid") || lowerText.contains("shoe grid") ||
                lowerText.contains("sho grid") || lowerText.contains("sow grid") ||
                lowerText.contains("show great") || lowerText.contains("show grit")
        if (!isGridPhrase) return null

        var extractedRows: Int? = null
        var extractedCols: Int? = null

        // Check for "X rows" or "row X" (including phonetic variations: rose, raws, raw, roes, ros, roz, roze, ross, rowses, rosses)
        val rowRegex = Regex("""(\d+|[a-z]+)\s+(?:rows?|row|rose|raws?|raw|roes|ros|roz|roze|ross|rowses|rosses)""")
        val rowMatch = rowRegex.find(lowerText)
        if (rowMatch != null) {
            extractedRows = parseSpokenNumber(rowMatch.groupValues[1])
        } else {
            val rowRegexAlt = Regex("""(?:rows?|row|rose|raws?|raw|roes|ros|roz|roze|ross|rowses|rosses)\s+(\d+|[a-z]+)""")
            val rowMatchAlt = rowRegexAlt.find(lowerText)
            if (rowMatchAlt != null) {
                extractedRows = parseSpokenNumber(rowMatchAlt.groupValues[1])
            }
        }

        // Check for "X columns" or "column X" (including phonetic variations: cols, col, calumns, collumns, colums, kals, callum, calum, kols, kollum, kollums, collum, collums, kullum, kullums)
        val colRegex = Regex("""(\d+|[a-z]+)\s+(?:columns?|column|cols?|col|calumns?|collumns?|colums?|kals|callum|calum|kols|kollums?|collums?|kullums?)""")
        val colMatch = colRegex.find(lowerText)
        if (colMatch != null) {
            extractedCols = parseSpokenNumber(colMatch.groupValues[1])
        } else {
            val colRegexAlt = Regex("""(?:columns?|column|cols?|col|calumns?|collumns?|colums?|kals|callum|calum|kols|kollums?|collums?|kullums?)\s+(\d+|[a-z]+)""")
            val colMatchAlt = colRegexAlt.find(lowerText)
            if (colMatchAlt != null) {
                extractedCols = parseSpokenNumber(colMatchAlt.groupValues[1])
            }
        }

        // Check for "X by Y" (e.g. "grid 7 by 9", "7 bi 9", "7 times 9")
        if (extractedRows == null && extractedCols == null) {
            val byRegex = Regex("""(\d+|[a-z]+)\s+(?:by|bi|buy|bye|times|x|into)\s+(\d+|[a-z]+)""")
            val byMatch = byRegex.find(lowerText)
            if (byMatch != null) {
                extractedRows = parseSpokenNumber(byMatch.groupValues[1])
                extractedCols = parseSpokenNumber(byMatch.groupValues[2])
            }
        }

        return VoiceCommand.ShowGrid(customRows = extractedRows, customCols = extractedCols)
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
