package com.example.voicecontrol.model

import com.example.voicecontrol.manager.GestureType

/**
 * Enum defining command categories parsed by CommandGrammarEngine.
 */
enum class GrammarCategory {
    APP_COMMAND,
    TAP_COMMAND,
    SYSTEM_COMMAND,
    GESTURE_COMMAND,
    GRID_COMMAND,
    TEXT_COMMAND,
    UNKNOWN
}

/**
 * Enum defining specific grid overlay action types.
 */
enum class GridActionType {
    SHOW_GRID,
    HIDE_GRID,
    RESET_GRID,
    CLICK_HERE,
    SHOW_NUMBERS,
    HIDE_NUMBERS,
    TAP_NUMBER
}

/**
 * Sealed interface representing structured voice command intents.
 */
sealed interface GrammarIntent {

    /**
     * Intent to open/launch an installed application.
     */
    data class OpenApp(val appName: String, val packageName: String? = null) : GrammarIntent

    /**
     * Intent to dynamically tap a UI element by text or content description.
     */
    data class TapText(val targetText: String) : GrammarIntent

    /**
     * Intent to type arbitrary text into active focused text field ("type hello world").
     */
    data class TypeText(val textToType: String) : GrammarIntent

    /**
     * Intent to execute an Android System Global Action via Accessibility Service.
     */
    data class SystemAction(val actionId: Int, val actionName: String) : GrammarIntent

    /**
     * Intent to execute gesture navigation (Swipe Up, Swipe Down, Swipe Left, Swipe Right).
     */
    data class SwipeGesture(val type: GestureType, val label: String) : GrammarIntent

    /**
     * Intent for Grid or Number Overlay interaction with optional dynamic rows and columns.
     */
    data class GridAction(
        val type: GridActionType,
        val badgeNumber: Int? = null,
        val customRows: Int? = null,
        val customCols: Int? = null
    ) : GrammarIntent

    /**
     * Debug intent to log all discovered launchable applications to Logcat under VOICE_APPS.
     */
    object ListApps : GrammarIntent

    /**
     * Unrecognized or unhandled voice command.
     */
    data class Unknown(val rawText: String) : GrammarIntent
}

/**
 * Data model representing the output of CommandGrammarEngine parsing.
 * @param intent Structured intent model.
 * @param category Command category enum.
 * @param matchedPhrase Human-readable matched phrase or normalized speech string.
 * @param confidence Confidence score float between 0.0f and 1.0f.
 */
data class GrammarResult(
    val intent: GrammarIntent,
    val category: GrammarCategory,
    val matchedPhrase: String,
    val confidence: Float
)
