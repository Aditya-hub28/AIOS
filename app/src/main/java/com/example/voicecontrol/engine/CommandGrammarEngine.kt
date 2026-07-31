package com.example.voicecontrol.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.Log
import com.example.voicecontrol.manager.GestureType
import com.example.voicecontrol.manager.SpeechRecognitionListener
import com.example.voicecontrol.manager.SpeechRecognizerManager
import com.example.voicecontrol.model.GrammarCategory
import com.example.voicecontrol.model.GrammarIntent
import com.example.voicecontrol.model.GrammarResult
import com.example.voicecontrol.model.GridActionType
import com.example.voicecontrol.util.FuzzyAppMatcher
import java.util.Locale

/**
 * CommandGrammarEngine parses raw speech input into structured, typed GrammarIntent objects
 * with confidence scoring, phonetic alias resolution, Levenshtein fuzzy matching,
 * and detailed Logcat diagnostics under tag GRAMMAR.
 */
class CommandGrammarEngine(private val context: Context) {

    companion object {
        private const val TAG_GRAMMAR = "GRAMMAR"
        private const val TAG_APPS = "VOICE_APPS"

        private val TAP_PREFIXES = listOf("tap", "click", "press", "select", "touch")
        private val TYPE_PREFIXES = listOf("type", "enter", "input", "write")
        private val OPEN_PREFIXES = listOf("open", "launch", "start", "run", "go to")

        private val LEETCODE_SPEECH_VARIATIONS = listOf(
            "leetcode",
            "leet code",
            "leetcod",
            "leat code",
            "lead code"
        )
    }

    private val speechManager = SpeechRecognizerManager(context)
    private var installedAppMap = mapOf<String, String>()

    init {
        indexInstalledApps()
    }

    /**
     * Scans installed launchable applications and builds a packageName -> appLabel map.
     */
    fun indexInstalledApps(): Map<String, String> {
        val packageManager = context.packageManager
        val appMap = mutableMapOf<String, String>()

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        try {
            val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in resolveInfos) {
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val pkgName = resolveInfo.activityInfo.packageName
                if (label.isNotBlank() && pkgName.isNotBlank()) {
                    appMap[pkgName] = label
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_GRAMMAR, "Error indexing launcher activities", e)
        }

        installedAppMap = appMap
        Log.i(TAG_GRAMMAR, "Indexed ${installedAppMap.size} launchable applications.")
        return installedAppMap
    }

    /**
     * Logs all installed launchable applications under tag VOICE_APPS.
     */
    fun logAllInstalledApps(): Int {
        val appMap = indexInstalledApps()
        Log.i(TAG_APPS, "==========================================================")
        Log.i(TAG_APPS, "📱 DISCOVERED LAUNCHABLE APPLICATIONS (${appMap.size} APPS)")
        Log.i(TAG_APPS, "==========================================================")
        for ((pkgName, appLabel) in appMap) {
            Log.i(TAG_APPS, "VOICE_APPS: Package Name: $pkgName | App Label: $appLabel")
        }
        Log.i(TAG_APPS, "==========================================================")
        return appMap.size
    }

    /**
     * Converts raw speech text into a structured GrammarResult.
     */
    fun parseGrammarIntent(rawText: String): GrammarResult {
        val trimmedRaw = rawText.trim()
        if (trimmedRaw.isBlank()) {
            return GrammarResult(
                intent = GrammarIntent.Unknown(rawText),
                category = GrammarCategory.UNKNOWN,
                matchedPhrase = rawText,
                confidence = 0.0f
            )
        }

        val normalizedText = normalizeSpeechInput(trimmedRaw)
        val lowerText = normalizedText.lowercase(Locale.getDefault())

        // --- 1. DEBUG LIST APPS COMMAND ---
        when (lowerText) {
            "list apps", "show apps", "all apps", "list all apps", "log apps" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.ListApps,
                    category = GrammarCategory.SYSTEM_COMMAND,
                    matchedPhrase = lowerText,
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
        }

        // --- 2. GRID COMMANDS ---
        val parsedCmd = CommandParser.parse(rawText)
        if (parsedCmd is VoiceCommand.ShowGrid) {
            val result = GrammarResult(
                intent = GrammarIntent.GridAction(
                    type = GridActionType.SHOW_GRID,
                    customRows = parsedCmd.customRows,
                    customCols = parsedCmd.customCols
                ),
                category = GrammarCategory.GRID_COMMAND,
                matchedPhrase = lowerText,
                confidence = 1.0f
            )
            logGrammarReport(rawText, normalizedText, result)
            return result
        } else if (parsedCmd is VoiceCommand.HideGrid) {
            val result = GrammarResult(
                intent = GrammarIntent.GridAction(type = GridActionType.HIDE_GRID),
                category = GrammarCategory.GRID_COMMAND,
                matchedPhrase = lowerText,
                confidence = 1.0f
            )
            logGrammarReport(rawText, normalizedText, result)
            return result
        } else if (parsedCmd is VoiceCommand.ResetGrid) {
            val result = GrammarResult(
                intent = GrammarIntent.GridAction(type = GridActionType.RESET_GRID),
                category = GrammarCategory.GRID_COMMAND,
                matchedPhrase = lowerText,
                confidence = 1.0f
            )
            logGrammarReport(rawText, normalizedText, result)
            return result
        } else if (parsedCmd is VoiceCommand.ClickHere) {
            val result = GrammarResult(
                intent = GrammarIntent.GridAction(type = GridActionType.CLICK_HERE),
                category = GrammarCategory.GRID_COMMAND,
                matchedPhrase = lowerText,
                confidence = 1.0f
            )
            logGrammarReport(rawText, normalizedText, result)
            return result
        }
            "show numbers", "show number", "display numbers", "numbers on", "show badge numbers" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.GridAction(GridActionType.SHOW_NUMBERS),
                    category = GrammarCategory.GRID_COMMAND,
                    matchedPhrase = lowerText,
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
            "hide numbers", "hide number", "remove numbers", "numbers off", "clear numbers" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.GridAction(GridActionType.HIDE_NUMBERS),
                    category = GrammarCategory.GRID_COMMAND,
                    matchedPhrase = lowerText,
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
        }

        // Tap Number or Badge Cell Command ("Tap 5", "5")
        val numberFromTap = parseTapNumberCommand(lowerText, trimmedRaw)
        if (numberFromTap != null) {
            val result = GrammarResult(
                intent = GrammarIntent.GridAction(GridActionType.TAP_NUMBER, badgeNumber = numberFromTap),
                category = GrammarCategory.GRID_COMMAND,
                matchedPhrase = "tap #$numberFromTap",
                confidence = 0.95f
            )
            logGrammarReport(rawText, normalizedText, result)
            return result
        }

        // --- 3. TEXT COMMANDS ("type hello world") ---
        for (prefix in TYPE_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                val textToType = trimmedRaw.substring(prefix.length).trim()
                if (textToType.isNotBlank()) {
                    val result = GrammarResult(
                        intent = GrammarIntent.TypeText(textToType),
                        category = GrammarCategory.TEXT_COMMAND,
                        matchedPhrase = "type $textToType",
                        confidence = 0.95f
                    )
                    logGrammarReport(rawText, normalizedText, result)
                    return result
                }
            }
        }

        // --- 4. TAP COMMANDS ("tap search", "tap communities", "tap chats") ---
        for (prefix in TAP_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                val targetText = trimmedRaw.substring(prefix.length).trim()
                if (targetText.isNotBlank()) {
                    val result = GrammarResult(
                        intent = GrammarIntent.TapText(targetText),
                        category = GrammarCategory.TAP_COMMAND,
                        matchedPhrase = "tap $targetText",
                        confidence = 0.95f
                    )
                    logGrammarReport(rawText, normalizedText, result)
                    return result
                }
            }
        }

        // --- 5. GESTURE NAVIGATION COMMANDS ---
        when (lowerText) {
            "swipe up", "swipe upward", "upward swipe", "scroll down", "down", "page down" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SwipeGesture(GestureType.SWIPE_UP, "Swipe Up"),
                    category = GrammarCategory.GESTURE_COMMAND,
                    matchedPhrase = "swipe up",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
            "swipe down", "swipe downward", "downward swipe", "scroll up", "up", "page up" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SwipeGesture(GestureType.SWIPE_DOWN, "Swipe Down"),
                    category = GrammarCategory.GESTURE_COMMAND,
                    matchedPhrase = "swipe down",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
            "swipe left", "swipe leftward", "leftward swipe", "next tab", "slide left" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SwipeGesture(GestureType.SWIPE_LEFT, "Swipe Left"),
                    category = GrammarCategory.GESTURE_COMMAND,
                    matchedPhrase = "swipe left",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
            "swipe right", "swipe rightward", "rightward swipe", "previous tab", "slide right" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SwipeGesture(GestureType.SWIPE_RIGHT, "Swipe Right"),
                    category = GrammarCategory.GESTURE_COMMAND,
                    matchedPhrase = "swipe right",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
        }

        // --- 6. SYSTEM COMMANDS ---
        when (lowerText) {
            "home", "go home", "go to home", "open home", "take me home" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SystemAction(AccessibilityService.GLOBAL_ACTION_HOME, "Home"),
                    category = GrammarCategory.SYSTEM_COMMAND,
                    matchedPhrase = "home",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
            "back", "go back", "navigate back", "press back", "previous" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SystemAction(AccessibilityService.GLOBAL_ACTION_BACK, "Back"),
                    category = GrammarCategory.SYSTEM_COMMAND,
                    matchedPhrase = "back",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
            "recent apps", "recents", "recent", "open recents", "show recent apps", "app switcher", "overview" -> {
                val result = GrammarResult(
                    intent = GrammarIntent.SystemAction(AccessibilityService.GLOBAL_ACTION_RECENTS, "Recent Apps"),
                    category = GrammarCategory.SYSTEM_COMMAND,
                    matchedPhrase = "recent apps",
                    confidence = 1.0f
                )
                logGrammarReport(rawText, normalizedText, result)
                return result
            }
        }

        // --- 7. APP COMMANDS ("open whatsapp", "open leetcode", "open instagram") ---
        var appTargetName: String? = null
        for (prefix in OPEN_PREFIXES) {
            if (lowerText.startsWith("$prefix ")) {
                appTargetName = trimmedRaw.substring(prefix.length).trim()
                break
            }
        }
        if (appTargetName == null && !lowerText.contains(" ") && lowerText.length > 2) {
            appTargetName = trimmedRaw
        }

        if (appTargetName != null) {
            val appMatchResult = resolveAppMatch(appTargetName)
            if (appMatchResult != null) {
                logGrammarReport(rawText, normalizedText, appMatchResult)
                return appMatchResult
            }
        }

        val fallbackUnknown = GrammarResult(
            intent = GrammarIntent.Unknown(trimmedRaw),
            category = GrammarCategory.UNKNOWN,
            matchedPhrase = trimmedRaw,
            confidence = 0.0f
        )
        logGrammarReport(rawText, normalizedText, fallbackUnknown)
        return fallbackUnknown
    }

    /**
     * Resolves application name matching, phonetic alias dictionary, and Levenshtein similarity.
     */
    private fun resolveAppMatch(appNameQuery: String): GrammarResult? {
        val queryLower = appNameQuery.lowercase(Locale.getDefault())

        // 1. Special LeetCode Resolution
        if (LEETCODE_SPEECH_VARIATIONS.any { queryLower.contains(it) }) {
            return GrammarResult(
                intent = GrammarIntent.OpenApp(appName = "LeetCode", packageName = "com.leetcode"),
                category = GrammarCategory.APP_COMMAND,
                matchedPhrase = "open leetcode",
                confidence = 0.95f
            )
        }

        // 2. Installed App Lookup
        data class Candidate(val label: String, val pkg: String)
        val candidates = installedAppMap.map { (pkg, label) -> Candidate(label, pkg) }

        // Exact Label Match
        var matched = candidates.firstOrNull { it.label.lowercase(Locale.getDefault()) == queryLower }
        if (matched != null) {
            return GrammarResult(
                intent = GrammarIntent.OpenApp(appName = matched.label, packageName = matched.pkg),
                category = GrammarCategory.APP_COMMAND,
                matchedPhrase = "open ${matched.label}",
                confidence = 1.0f
            )
        }

        // Starts-With Match
        matched = candidates.firstOrNull { it.label.lowercase(Locale.getDefault()).startsWith(queryLower) }
        if (matched != null) {
            return GrammarResult(
                intent = GrammarIntent.OpenApp(appName = matched.label, packageName = matched.pkg),
                category = GrammarCategory.APP_COMMAND,
                matchedPhrase = "open ${matched.label}",
                confidence = 0.85f
            )
        }

        // Levenshtein Fuzzy Matcher ("whatsap" -> WhatsApp, "instgram" -> Instagram)
        val candidateLabels = candidates.map { it.label }
        val bestFuzzyLabel = FuzzyAppMatcher.findBestMatch(appNameQuery, candidateLabels, minSimilarity = 0.65f)
        if (bestFuzzyLabel != null) {
            matched = candidates.firstOrNull { it.label == bestFuzzyLabel }
            if (matched != null) {
                val score = FuzzyAppMatcher.similarityScore(appNameQuery, matched.label)
                return GrammarResult(
                    intent = GrammarIntent.OpenApp(appName = matched.label, packageName = matched.pkg),
                    category = GrammarCategory.APP_COMMAND,
                    matchedPhrase = "open ${matched.label}",
                    confidence = score
                )
            }
        }

        return null
    }

    /**
     * Normalizes spoken speech input and resolves phonetic aliases & LeetCode variations.
     */
    fun normalizeSpeechInput(rawInput: String): String {
        val trimmed = rawInput.trim().lowercase(Locale.getDefault())

        if (LEETCODE_SPEECH_VARIATIONS.any { trimmed.contains(it) }) {
            return trimmed
                .replace("leet code", "leetcode")
                .replace("leat code", "leetcode")
                .replace("lead code", "leetcode")
                .replace("leetcod", "leetcode")
        }

        return trimmed
            .replace("whats app", "whatsapp")
            .replace("whatsup", "whatsapp")
            .replace("you tube", "youtube")
            .replace("linked in", "linkedin")
            .replace("chat gpt", "chatgpt")
            .replace("google chrome", "chrome")
            .replace("pay tm", "paytm")
            .replace("face book", "facebook")
            .replace("snap chat", "snapchat")
            .replace("google mail", "gmail")
            .replace("google maps", "maps")
            .replace("google photos", "photos")
    }

    /**
     * Parses tap number patterns ("tap 5", "click 12", "5").
     */
    private fun parseTapNumberCommand(lowerText: String, trimmedText: String): Int? {
        trimmedText.toIntOrNull()?.let { return it }

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
                targetNumString.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    /**
     * Prints detailed Logcat report under tag GRAMMAR.
     */
    private fun logGrammarReport(rawText: String, normalizedText: String, result: GrammarResult) {
        Log.i(TAG_GRAMMAR, "==========================================================")
        Log.i(TAG_GRAMMAR, "GRAMMAR: Raw Text        : \"$rawText\"")
        Log.i(TAG_GRAMMAR, "GRAMMAR: Normalized Text : \"$normalizedText\"")
        Log.i(TAG_GRAMMAR, "GRAMMAR: Category        : ${result.category}")
        Log.i(TAG_GRAMMAR, "GRAMMAR: Matched Intent  : ${result.intent}")
        Log.i(TAG_GRAMMAR, "GRAMMAR: Confidence      : ${String.format("%.2f", result.confidence)}")
        Log.i(TAG_GRAMMAR, "==========================================================")
    }

    fun isAvailable(): Boolean = speechManager.isAvailable()
    fun startListening(listener: SpeechRecognitionListener) = speechManager.startListening(listener)
    fun stopListening() = speechManager.stopListening()
    fun cancel() = speechManager.cancel()
    fun destroy() = speechManager.destroy()
}
