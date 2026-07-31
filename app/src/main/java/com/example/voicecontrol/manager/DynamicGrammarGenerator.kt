package com.example.voicecontrol.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import java.util.Locale

/**
 * Utility for scanning installed launchable applications and generating a dynamic
 * Vosk grammar JSON array containing "open <app>" phrases, common phonetic aliases,
 * system navigation commands, and Out-Of-Vocabulary "[unk]" tokens.
 */
object DynamicGrammarGenerator {

    private const val TAG = "VOSK_GRAMMAR"

    // Base system commands required by VoiceControl
    private val BASE_SYSTEM_COMMANDS = listOf(
        "home",
        "go home",
        "back",
        "recent apps",
        "recents",
        "swipe up",
        "swipe down",
        "swipe left",
        "swipe right",
        "show grid",
        "hide grid",
        "reset grid",
        "click here",
        "show numbers",
        "hide numbers",
        "tap search",
        "tap settings",
        "tap profile",
        "tap install",
        "tap send",
        "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"
    )

    /**
     * Scans installed applications, builds app command phrases with aliases,
     * merges system commands, and returns a formatted JSON array string for Vosk Recognizer.
     */
    fun generateGrammarJson(context: Context): String {
        val tStart = System.currentTimeMillis()
        val packageManager = context.packageManager

        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolveInfos = try {
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (e: Exception) {
            Log.e(TAG, "Error querying launcher intent activities", e)
            emptyList()
        }

        val installedAppLabels = mutableListOf<String>()
        val appPhrases = mutableSetOf<String>()

        for (resolveInfo in resolveInfos) {
            val rawLabel = resolveInfo.loadLabel(packageManager).toString()
            val normalizedLabel = normalizeAppName(rawLabel)

            if (normalizedLabel.isNotBlank()) {
                installedAppLabels.add(rawLabel)
                
                // Add direct "open <appName>" command
                appPhrases.add("open $normalizedLabel")

                // Generate common phonetic aliases
                val aliases = generateAliasesForApp(normalizedLabel)
                for (alias in aliases) {
                    appPhrases.add("open $alias")
                }
            }
        }

        // Combine all grammar elements into a distinct ordered list
        val fullGrammarList = mutableListOf<String>()
        fullGrammarList.addAll(appPhrases)
        fullGrammarList.addAll(BASE_SYSTEM_COMMANDS)
        fullGrammarList.add("[unk]") // Always end with Out-of-Vocabulary token

        val distinctGrammarList = fullGrammarList.distinct()

        // Format into JSON Array
        val jsonArray = JSONArray()
        for (phrase in distinctGrammarList) {
            jsonArray.put(phrase)
        }

        val jsonString = jsonArray.toString()
        val tEnd = System.currentTimeMillis()
        val jsonSizeBytes = jsonString.toByteArray(Charsets.UTF_8).size

        // --- VOSK_GRAMMAR LOGGING REPORT ---
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "📊 VOSK DYNAMIC GRAMMAR GENERATION REPORT")
        Log.i(TAG, "==========================================================")
        Log.i(TAG, "📱 Installed Apps Scanned  : ${installedAppLabels.size}")
        Log.i(TAG, "🗣️ Total Grammar Phrases   : ${distinctGrammarList.size}")
        Log.i(TAG, "📦 Grammar JSON Size       : $jsonSizeBytes bytes (${jsonSizeBytes / 1024} KB)")
        Log.i(TAG, "⏱️ Generation Duration     : ${tEnd - tStart} ms")
        Log.i(TAG, "==========================================================")

        return jsonString
    }

    /**
     * Normalizes application names by converting to lowercase and stripping non-alphanumeric symbols.
     */
    private fun normalizeAppName(rawName: String): String {
        return rawName.lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Generates phonetic variations and common conversational aliases for popular applications.
     */
    private fun generateAliasesForApp(normalizedApp: String): List<String> {
        val aliases = mutableListOf<String>()

        when {
            normalizedApp.contains("whatsapp") -> {
                aliases.add("whats app")
                aliases.add("whatsup")
                aliases.add("what s app")
            }
            normalizedApp.contains("linkedin") -> {
                aliases.add("linked in")
            }
            normalizedApp.contains("youtube") -> {
                aliases.add("you tube")
                aliases.add("yt")
            }
            normalizedApp.contains("instagram") -> {
                aliases.add("insta")
                aliases.add("insta gram")
            }
            normalizedApp.contains("chrome") -> {
                aliases.add("google chrome")
            }
            normalizedApp.contains("chatgpt") -> {
                aliases.add("chat gpt")
                aliases.add("gpt")
            }
            normalizedApp.contains("paytm") -> {
                aliases.add("pay tm")
            }
            normalizedApp.contains("facebook") -> {
                aliases.add("face book")
                aliases.add("fb")
            }
            normalizedApp.contains("snapchat") -> {
                aliases.add("snap chat")
                aliases.add("snap")
            }
            normalizedApp.contains("gmail") -> {
                aliases.add("g mail")
                aliases.add("google mail")
            }
            normalizedApp.contains("maps") -> {
                aliases.add("google maps")
            }
            normalizedApp.contains("photos") -> {
                aliases.add("google photos")
            }
            normalizedApp.contains("netflix") -> {
                aliases.add("net flix")
            }
            normalizedApp.contains("spotify") -> {
                aliases.add("spoti fy")
            }
        }

        // Also add space-separated multi-word app names without spaces if applicable
        if (normalizedApp.contains(" ")) {
            aliases.add(normalizedApp.replace(" ", ""))
        }

        return aliases
    }
}
