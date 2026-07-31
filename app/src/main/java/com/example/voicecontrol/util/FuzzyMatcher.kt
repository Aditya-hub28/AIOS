package com.example.voicecontrol.util

/**
 * Utility class providing Levenshtein distance based fuzzy matching.
 * Threshold is configurable; the default is a maximum edit distance of 2.
 */
object FuzzyMatcher {
    /**
     * Computes the Levenshtein distance between two strings.
     */
    fun distance(a: String, b: String): Int {
        val m = a.length
        val n = b.length
        if (m == 0) return n
        if (n == 0) return m
        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0..m) dp[i][0] = i
        for (j in 0..n) dp[0][j] = j
        for (i in 1..m) {
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,          // deletion
                    dp[i][j - 1] + 1,          // insertion
                    dp[i - 1][j - 1] + cost    // substitution
                )
            }
        }
        return dp[m][n]
    }

    /**
     * Returns true if the distance between [candidate] and [target] is within [maxDistance].
     */
    fun isFuzzyMatch(candidate: String, target: String, maxDistance: Int = 2): Boolean {
        return distance(candidate, target) <= maxDistance
    }
}
