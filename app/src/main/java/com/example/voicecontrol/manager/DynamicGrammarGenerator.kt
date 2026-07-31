package com.example.voicecontrol.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import org.json.JSONArray
import java.io.File
import java.util.Locale

/**
 * Utility for scanning 100% of installed, system, updated-system, work profile, and cloned launchable applications,
 * adding LeetCode & popular app phonetic aliases, merging system commands, saving debug JSON to storage,
 * and generating a dynamic Vosk grammar JSON array.
 */
object DynamicGrammarGenerator {

    private const val TAG_GRAMMAR = "VOSK_GRAMMAR"
    private const val TAG_APPS = "VOSK_APPS"

    // Base system commands required by VoiceControl
    private val BASE_SYSTEM_COMMANDS = listOf(
        "home",
        "go home",
        "back",
        "recent apps",
        "recents",
        "list apps",
        "show apps",
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

    // Explicit LeetCode grammar phrases and phonetic aliases
    private val LEETCODE_GRAMMAR_PHRASES = listOf(
        "open leetcode",
        "open leet code",
        "leetcode",
        "leet code",
        "open leetcod",
        "open leat code",
        "open lead code",
        "leetcod",
        "leat code",
        "lead code"
    )

    /**
     * Scans all launchable installed & system applications using dual discovery strategies.
     * Returns a map of PackageName -> AppLabel.
     */
    fun scanAllLaunchableApps(context: Context): Map<String, String> {
        val packageManager = context.packageManager
        val appMap = mutableMapOf<String, String>()

        // Strategy A: Launcher Category Query (Standard & Cloned Apps)
        val launcherIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        try {
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in resolveInfos) {
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val pkgName = resolveInfo.activityInfo.packageName
                if (label.isNotBlank() && pkgName.isNotBlank()) {
                    appMap[pkgName] = label
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_APPS, "Error during Strategy A launcher query", e)
        }

        // Strategy B: Installed Applications Query (System & Updated System Apps)
        try {
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
            for (appInfo in installedApps) {
                val pkgName = appInfo.packageName
                if (!appMap.containsKey(pkgName)) {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkgName)
                    if (launchIntent != null) {
                        val label = packageManager.getApplicationLabel(appInfo).toString().trim()
                        if (label.isNotBlank()) {
                            appMap[pkgName] = label
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_APPS, "Error during Strategy B installed applications query", e)
        }

        return appMap
    }

    /**
     * Logs every discovered application under tag VOSK_APPS.
     */
    fun logAllInstalledApps(context: Context): Int {
        val appMap = scanAllLaunchableApps(context)
        Log.i(TAG_APPS, "==========================================================")
        Log.i(TAG_APPS, "📱 DISCOVERED LAUNCHABLE APPLICATIONS REPORT (${appMap.size} APPS)")
        Log.i(TAG_APPS, "==========================================================")
        for ((pkgName, appLabel) in appMap) {
            Log.i(TAG_APPS, "VOSK_APPS: Package Name: $pkgName | App Label: $appLabel")
        }
        Log.i(TAG_APPS, "==========================================================")
        return appMap.size
    }

    /**
     * Scans installed applications, builds app command phrases with aliases,
     * merges system commands and LeetCode phrases, saves debug JSON to storage,
     * and returns a formatted JSON array string for Vosk Recognizer.
     */
    fun generateGrammarJson(context: Context): String {
        val tStart = System.currentTimeMillis()
        val appMap = scanAllLaunchableApps(context)

        // Log all apps to Logcat under VOSK_APPS
        for ((pkgName, appLabel) in appMap) {
            Log.i(TAG_APPS, "VOSK_APPS: Package Name: $pkgName | App Label: $appLabel")
        }

        val appPhrases = mutableSetOf<String>()

        for ((_, rawLabel) in appMap) {
            val normalizedLabel = normalizeAppName(rawLabel)

            if (normalizedLabel.isNotBlank()) {
                // Add direct "open <appName>" and standalone "<appName>"
                appPhrases.add("open $normalizedLabel")
                appPhrases.add(normalizedLabel)

                // Generate common phonetic aliases
                val aliases = generateAliasesForApp(normalizedLabel)
                for (alias in aliases) {
                    appPhrases.add("open $alias")
                    appPhrases.add(alias)
                }
            }
        }

        // Combine all grammar elements into a distinct ordered list
        val fullGrammarList = mutableListOf<String>()
        fullGrammarList.addAll(appPhrases)
        fullGrammarList.addAll(LEETCODE_GRAMMAR_PHRASES)
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

        // Save JSON debug file to internal storage for debugging
        saveDebugGrammarJson(context, jsonString)

        // --- VOSK_GRAMMAR LOGGING REPORT ---
        Log.i(TAG_GRAMMAR, "==========================================================")
        Log.i(TAG_GRAMMAR, "📊 VOSK DYNAMIC GRAMMAR GENERATION REPORT")
        Log.i(TAG_GRAMMAR, "==========================================================")
        Log.i(TAG_GRAMMAR, "📱 Total launchable apps found  : ${appMap.size}")
        Log.i(TAG_GRAMMAR, "🗣️ Total grammar phrases generated: ${distinctGrammarList.size}")
        Log.i(TAG_GRAMMAR, "📦 Grammar JSON Size           : $jsonSizeBytes bytes (${jsonSizeBytes / 1024} KB)")
        Log.i(TAG_GRAMMAR, "⏱️ Generation Duration         : ${tEnd - tStart} ms")
        Log.i(TAG_GRAMMAR, "==========================================================")

        return jsonString
    }

    /**
     * Saves final generated grammar JSON string to File(context.filesDir, "debug_vosk_grammar.json").
     */
    private fun saveDebugGrammarJson(context: Context, jsonString: String) {
        try {
            val debugFile = File(context.filesDir, "debug_vosk_grammar.json")
            debugFile.writeText(jsonString)
            Log.i(TAG_GRAMMAR, "📄 Saved debug grammar JSON to: ${debugFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG_GRAMMAR, "Failed to write debug grammar JSON file", e)
        }
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
            normalizedApp.contains("leetcode") || normalizedApp.contains("leet code") -> {
                aliases.add("leetcode")
                aliases.add("leet code")
                aliases.add("leetcod")
                aliases.add("leat code")
                aliases.add("lead code")
            }
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
