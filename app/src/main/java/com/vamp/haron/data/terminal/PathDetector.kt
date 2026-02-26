package com.vamp.haron.data.terminal

data class DetectedPath(
    val path: String,
    val startIndex: Int,
    val endIndex: Int
)

object PathDetector {

    private val absolutePathRegex = Regex("""(/[^\s:*?"<>|]+)""")
    private val relativePathRegex = Regex("""(\./[^\s:*?"<>|]+)""")

    fun detectPaths(text: String): List<DetectedPath> {
        val results = mutableListOf<DetectedPath>()
        for (match in absolutePathRegex.findAll(text)) {
            // Filter out common false positives
            val path = match.value
            if (path.length > 2 && !path.endsWith("/")) {
                results.add(DetectedPath(path, match.range.first, match.range.last + 1))
            }
        }
        for (match in relativePathRegex.findAll(text)) {
            val path = match.value
            if (path.length > 2) {
                results.add(DetectedPath(path, match.range.first, match.range.last + 1))
            }
        }
        return results.sortedBy { it.startIndex }
    }
}
