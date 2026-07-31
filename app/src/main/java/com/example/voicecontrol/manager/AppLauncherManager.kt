package com.example.voicecontrol.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log

/**
 * Result of an app launch attempt.
 */
sealed interface AppLaunchResult {
    data class Success(val appName: String, val packageName: String) : AppLaunchResult
    data class NotFound(val targetAppName: String) : AppLaunchResult
    data class Error(val targetAppName: String, val errorMessage: String) : AppLaunchResult
}

/**
 * AppLauncherManager detects installed applications via PackageManager
 * and launches target apps matching spoken names.
 */
class AppLauncherManager(private val context: Context) {

    companion object {
        private const val TAG = "AppLauncherManager"
    }

    /**
     * Searches for an installed app matching [appName] and launches it.
     */
    fun launchApp(appName: String): AppLaunchResult {
        val packageManager = context.packageManager

        try {
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val resolveInfos: List<ResolveInfo> = packageManager.queryIntentActivities(mainIntent, 0)

            if (resolveInfos.isEmpty()) {
                Log.w(TAG, "No launcher applications found on device.")
                return AppLaunchResult.NotFound(appName)
            }

            // Data structure holding app label and package name
            data class AppCandidate(val label: String, val packageName: String)

            val installedApps = resolveInfos.mapNotNull { info ->
                val label = info.loadLabel(packageManager).toString().trim()
                val pkg = info.activityInfo.packageName
                if (label.isNotBlank() && pkg.isNotBlank()) AppCandidate(label, pkg) else null
            }

            val targetLower = appName.lowercase().trim()

            // 1. Exact match (case-insensitive)
            var matched = installedApps.firstOrNull { candidate ->
                candidate.label.lowercase() == targetLower
            }

            // 2. Starts with match (e.g., "Chrome" matches "Chrome" or "Chrome Beta")
            if (matched == null) {
                matched = installedApps.firstOrNull { candidate ->
                    candidate.label.lowercase().startsWith(targetLower)
                }
            }

            // 3. Contains match (e.g., "Chrome" matches "Google Chrome")
            if (matched == null) {
                matched = installedApps.firstOrNull { candidate ->
                    candidate.label.lowercase().contains(targetLower) ||
                            targetLower.contains(candidate.label.lowercase())
                }
            }

            // 4. Special alias dictionary matching (e.g., "Phone" -> "Dialer", "Photos" -> "Gallery")
            if (matched == null) {
                val aliasMatches = getAliasesFor(targetLower)
                matched = installedApps.firstOrNull { candidate ->
                    aliasMatches.any { alias -> candidate.label.lowercase().contains(alias) }
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
