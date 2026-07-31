package com.example.voicecontrol.util

import android.util.Log
import java.util.Locale
import kotlin.math.min

/**
 * Utility executing Levenshtein distance string similarity matching for tolerant app launching.
 * Resolves phonetic misspellings (e.g. "whatsap" -> "WhatsApp", "instgram" -> "Instagram").
 */
object FuzzyAppMatcher {

    private const val TAG = "FuzzyAppMatcher"

    /**
     * Calculates the Levenshtein edit distance between two strings.
     */
    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase(Locale.getDefault())
        val b = s2.lowercase(Locale.getDefault())

        val costs = IntArray(b.length + 1)
        for (j in 0..b.length) {
            costs[j] = j
        }

        for (i in 1..a.length) {
            costs[0] = i
            var nw = i - 1
            for (j in 1..b.length) {
                val cj = min(
                    1 + min(costs[j], costs[j - 1]),
                    if (a[i - 1] == b[j - 1]) nw else nw + 1
                )
                nw = costs[j]
                costs[j] = cj
            }
        }
        return costs[b.length]
    }

    /**
     * Calculates similarity score between 0.0 (completely different) and 1.0 (exact match).
     */
    fun similarityScore(s1: String, s2: String): Float {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0f
        val distance = levenshteinDistance(s1, s2)
        return 1.0f - (distance.toFloat() / maxLen.toFloat())
    }

    /**
     * Finds the best matching candidate name from a list of installed app names given a target query.
     * @param targetAppName The app name extracted from speech (e.g. "instgram").
     * @param candidateNames List of installed application labels.
     * @param minSimilarity Minimum similarity threshold (default 0.70).
     * @return Best matching app label or null if below threshold.
     */
    fun findBestMatch(
        targetAppName: String,
        candidateNames: List<String>,
        minSimilarity: Float = 0.65f
    ): String? {
        val query = targetAppName.lowercase(Locale.getDefault()).trim()
        if (query.isBlank()) return null

        var bestMatch: String? = null
        var highestScore = 0f

        for (candidate in candidateNames) {
            val candidateNorm = candidate.lowercase(Locale.getDefault()).trim()

            // Exact match
            if (query == candidateNorm) {
                Log.i(TAG, "Exact match found for '$targetAppName' -> '$candidate'")
                return candidate
            }

            val score = similarityScore(query, candidateNorm)
            if (score > highestScore) {
                highestScore = score
                bestMatch = candidate
            }
        }

        if (highestScore >= minSimilarity && bestMatch != null) {
            Log.i(TAG, "Fuzzy match found: '$targetAppName' -> '$bestMatch' (Score: ${String.format("%.2f", highestScore)})")
            return bestMatch
        } else {
            Log.w(TAG, "No fuzzy match above threshold ($minSimilarity) for '$targetAppName'. Highest score: ${String.format("%.2f", highestScore)}")
            return null
        }
    }
}
