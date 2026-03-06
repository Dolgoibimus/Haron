package com.vamp.haron.common.util

import kotlin.math.max
import kotlin.math.min

object FuzzyMatch {

    /** Levenshtein edit distance. O(n*m) time, O(min(n,m)) space. */
    fun levenshtein(a: String, b: String): Int {
        val la = a.lowercase()
        val lb = b.lowercase()
        if (la == lb) return 0
        if (la.isEmpty()) return lb.length
        if (lb.isEmpty()) return la.length

        // Use shorter string for the row to save memory
        val (short, long) = if (la.length <= lb.length) la to lb else lb to la
        var prev = IntArray(short.length + 1) { it }
        var curr = IntArray(short.length + 1)

        for (i in 1..long.length) {
            curr[0] = i
            for (j in 1..short.length) {
                val cost = if (long[i - 1] == short[j - 1]) 0 else 1
                curr[j] = min(
                    min(curr[j - 1] + 1, prev[j] + 1),
                    prev[j - 1] + cost
                )
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[short.length]
    }

    /** Normalized similarity score 0..1 (1 = identical). */
    fun similarity(a: String, b: String): Float {
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1f
        return 1f - levenshtein(a, b).toFloat() / maxLen
    }

    /**
     * Find best match among candidates.
     * Priority: exact match > contains > startsWith > fuzzy (Levenshtein).
     * Returns null if no match above threshold.
     */
    fun findBestMatch(
        query: String,
        candidates: List<String>,
        threshold: Float = 0.5f
    ): String? {
        if (query.isBlank() || candidates.isEmpty()) return null
        val q = query.lowercase()

        // 1. Exact match
        candidates.firstOrNull { it.lowercase() == q }?.let { return it }

        // 2. Contains
        candidates.firstOrNull { it.lowercase().contains(q) }?.let { return it }

        // 3. StartsWith
        candidates.firstOrNull { it.lowercase().startsWith(q) }?.let { return it }

        // 4. Fuzzy — pick best score above threshold
        var bestMatch: String? = null
        var bestScore = 0f
        for (candidate in candidates) {
            val score = similarity(q, candidate.lowercase())
            if (score > bestScore) {
                bestScore = score
                bestMatch = candidate
            }
        }
        return if (bestScore >= threshold) bestMatch else null
    }
}
