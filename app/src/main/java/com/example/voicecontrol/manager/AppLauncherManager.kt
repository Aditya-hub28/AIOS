package com.example.voicecontrol.manager

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.util.Log
import com.example.voicecontrol.util.FuzzyAppMatcher
import java.util.Locale

/**
 * Result of an app launch attempt.
 */
sealed interface AppLaunchResult {
    data class Success(val appName: String, val packageName: String) : AppLaunchResult
    data class NotFound(val targetAppName: String) : AppLaunchResult
    data class Error(val targetAppName: String, val errorMessage: String) : AppLaunchResult
}

/**
 * AppLauncherManager detects installed applications via PackageManager,
 * applies exact, partial, LeetCode special normalization, alias, and Levenshtein fuzzy matching,
 * and launches target apps.
 */
class AppLauncherManager(private val context: Context) {

    companion object {
        private const val TAG = "AppLauncherManager"
        private const val TAG_LEETCODE = "VOICE_LEETCODE"

        // LeetCode speech normalization map
        private val LEETCODE_SPEECH_VARIATIONS = listOf(
            "leetcode",
            "leet code",
            "leetcod",
            "leat code",
            "lead code"
        )
    }

    /**
     * Searches for an installed app matching [appName] (with LeetCode & fuzzy tolerance) and launches it.
     */
    fun launchApp(appName: String): AppLaunchResult {
        val packageManager = context.packageManager
        val rawInput = appName.trim()
        val targetLower = rawInput.lowercase(Locale.getDefault())

        // --- SPECIAL LEETCODE MATCHING PIPELINE ---
        val isLeetCodeSpeech = LEETCODE_SPEECH_VARIATIONS.any { targetLower.contains(it) }
        if (isLeetCodeSpeech) {
            val normalizedSpeech = "leetcode"
            Log.i(TAG_LEETCODE, "==========================================================")
            Log.i(TAG_LEETCODE, "Original speech  : \"$rawInput\"")
            Log.i(TAG_LEETCODE, "Normalized speech: \"$normalizedSpeech\"")

            val leetCodePackage = "com.leetcode"
            val leetCodeIntent = packageManager.getLaunchIntentForPackage(leetCodePackage)

            if (leetCodeIntent != null) {
                leetCodeIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(leetCodeIntent)
                Log.i(TAG_LEETCODE, "Matched app       : LeetCode ($leetCodePackage)")
                Log.i(TAG_LEETCODE, "==========================================================")
                return AppLaunchResult.Success(appName = "LeetCode", packageName = leetCodePackage)
            } else {
                Log.w(TAG_LEETCODE, "Package 'com.leetcode' is not installed directly. Attempting name lookup...")
            }
        }

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(mainIntent, 0)

            // Data structure holding app label and package name
            data class AppCandidate(val label: String, val packageName: String)

            val installedApps = resolveInfos.mapNotNull { info ->
                val label = info.loadLabel(packageManager).toString().trim()
                val pkg = info.activityInfo.packageName
                if (label.isNotBlank() && pkg.isNotBlank()) AppCandidate(label, pkg) else null
            }

            if (installedApps.isEmpty()) {
                Log.w(TAG, "No launcher applications found on device.")
                return AppLaunchResult.NotFound(appName)
            }

            // 1. Exact match (case-insensitive)
            var matched = installedApps.firstOrNull { candidate ->
                candidate.label.lowercase(Locale.getDefault()) == targetLower
            }

            // 2. Starts with match (e.g., "Chrome" matches "Chrome" or "Chrome Beta")
            if (matched == null) {
                matched = installedApps.firstOrNull { candidate ->
                    candidate.label.lowercase(Locale.getDefault()).startsWith(targetLower)
                }
            }

            // 3. Contains match (e.g., "Chrome" matches "Google Chrome")
            if (matched == null) {
                matched = installedApps.firstOrNull { candidate ->
                    candidate.label.lowercase(Locale.getDefault()).contains(targetLower) ||
                            targetLower.contains(candidate.label.lowercase(Locale.getDefault()))
                }
            }

            // 4. Special alias dictionary matching
            if (matched == null) {
                val aliasMatches = getAliasesFor(targetLower)
                matched = installedApps.firstOrNull { candidate ->
                    aliasMatches.any { alias -> candidate.label.lowercase(Locale.getDefault()).contains(alias) }
                }
            }

            // 5. Levenshtein Fuzzy Matcher (e.g. "whatsap" -> "WhatsApp", "instgram" -> "Instagram")
            if (matched == null) {
                val candidateLabels = installedApps.map { it.label }
                val bestFuzzyLabel = FuzzyAppMatcher.findBestMatch(appName, candidateLabels)
                if (bestFuzzyLabel != null) {
                    matched = installedApps.firstOrNull { it.label == bestFuzzyLabel }
                }
            }

            if (matched != null) {
                val launchIntent = packageManager.getLaunchIntentForPackage(matched.packageName)
                if (launchIntent != null) {
                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(launchIntent)
                    Log.i(TAG, "Successfully launched app: ${matched.label} (${matched.packageName})")
                    return AppLaunchResult.Success(appName = matched.label, packageName = matched.packageName)
                } else {
                    Log.e(TAG, "Launch intent was null for package: ${matched.packageName}")
                    return AppLaunchResult.Error(appName, "Unable to launch ${matched.label}.")
                }
            }

            Log.w(TAG, "Application matching '$appName' not found.")
            return AppLaunchResult.NotFound(appName)

        } catch (e: Exception) {
            Log.e(TAG, "Error while attempting to launch app '$appName'", e)
            return AppLaunchResult.Error(appName, e.localizedMessage ?: "Failed to query or launch application.")
        }
    }

    /**
     * Map common generic voice terms to app label aliases.
     */
    private fun getAliasesFor(appNameLower: String): List<String> {
        return when (appNameLower) {
            "leetcode", "leet code", "leetcod", "leat code", "lead code" -> listOf("leetcode")
            "chrome" -> listOf("google chrome", "chrome")
            "whatsapp" -> listOf("whatsapp messenger", "whatsapp")
            "youtube" -> listOf("youtube")
            "gmail" -> listOf("gmail", "google mail")
            "maps" -> listOf("google maps", "maps")
            "photos" -> listOf("google photos", "gallery", "photos")
            "camera" -> listOf("camera")
            "phone" -> listOf("dialer", "phone", "call")
            "messages" -> listOf("messages", "messaging")
            "settings" -> listOf("settings")
            "calculator" -> listOf("calculator")
            "clock" -> listOf("clock", "alarm")
            else -> emptyList()
        }
    }
}
