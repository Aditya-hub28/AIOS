package com.example.voicecontrol.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.example.voicecontrol.manager.SpeechRecognitionListener
import com.example.voicecontrol.manager.SpeechRecognizerManager
import com.example.voicecontrol.util.FuzzyAppMatcher
import java.util.Locale

/**
 * CommandGrammarEngine encapsulates native Android SpeechRecognizer with a high-performance
 * command grammar normalization layer, dynamic app indexing, phonetic alias resolution,
 * and Levenshtein fuzzy app matching.
 */
class CommandGrammarEngine(private val context: Context) {

    companion object {
        private const val TAG = "CommandGrammarEngine"
        private const val TAG_APPS = "VOICE_APPS"
        private const val TAG_LEETCODE = "VOICE_LEETCODE"
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
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, 0)
            for (resolveInfo in resolveInfos) {
                val label = resolveInfo.loadLabel(packageManager).toString().trim()
                val pkgName = resolveInfo.activityInfo.packageName
                if (label.isNotBlank() && pkgName.isNotBlank()) {
                    appMap[pkgName] = label
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error indexing launcher activities", e)
        }

        installedAppMap = appMap
        Log.i(TAG, "Indexed ${installedAppMap.size} launchable applications for voice control.")
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
     * Normalizes spoken speech input and resolves phonetic aliases & LeetCode variations.
     */
    fun normalizeSpeechInput(rawInput: String): String {
        val trimmed = rawInput.trim().lowercase(Locale.getDefault())

        // 1. LeetCode Phonetic Normalization
        if (trimmed.contains("leetcode") || trimmed.contains("leet code") ||
            trimmed.contains("leetcod") || trimmed.contains("leat code") || trimmed.contains("lead code")) {
            Log.i(TAG_LEETCODE, "Original speech: \"$rawInput\" -> Normalized speech: \"leetcode\"")
            return rawInput.lowercase(Locale.getDefault())
                .replace("leet code", "leetcode")
                .replace("leat code", "leetcode")
                .replace("lead code", "leetcode")
                .replace("leetcod", "leetcode")
        }

        // 2. Common App Aliases Normalization
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

    fun isAvailable(): Boolean {
        return speechManager.isAvailable()
    }

    fun startListening(listener: SpeechRecognitionListener) {
        speechManager.startListening(listener)
    }

    fun stopListening() {
        speechManager.stopListening()
    }

    fun cancel() {
        speechManager.cancel()
    }

    fun destroy() {
        speechManager.destroy()
    }
}
