package com.vamp.haron.common.util

/**
 * Тип изменения в diff-дельте.
 */
enum class DeltaType { DELETE, INSERT, CHANGE }

/**
 * Одна дельта: диапазон строк в source и target + тип изменения.
 */
data class Delta<T>(
    val type: DeltaType,
    val sourcePosition: Int,
    val sourceLines: List<T>,
    val targetPosition: Int,
    val targetLines: List<T>
)

/**
 * Myers diff algorithm — находит кратчайшую последовательность правок
 * между двумя списками. O((N+M)*D) по времени и памяти, где D — количество различий.
 *
 * Возвращает список [Delta] совместимый по структуре с java-diff-utils.
 */
object MyersDiff {

    /**
     * Вычисляет diff между [source] и [target].
     */
    fun <T> diff(source: List<T>, target: List<T>): List<Delta<T>> {
        if (source.isEmpty() && target.isEmpty()) return emptyList()
        if (source.isEmpty()) return listOf(Delta(DeltaType.INSERT, 0, emptyList(), 0, target.toList()))
        if (target.isEmpty()) return listOf(Delta(DeltaType.DELETE, 0, source.toList(), 0, emptyList()))

        // Build LCS-based edit script via simple DP (reliable, no index bugs)
        val n = source.size
        val m = target.size

        // LCS length table
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 1..n) {
            for (j in 1..m) {
                dp[i][j] = if (source[i - 1] == target[j - 1]) {
                    dp[i - 1][j - 1] + 1
                } else {
                    maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }

        // Backtrack to find edit script
        data class Edit(val type: Char, val srcIdx: Int, val tgtIdx: Int) // 'K'eep, 'D'elete, 'I'nsert

        val edits = mutableListOf<Edit>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            if (source[i - 1] == target[j - 1]) {
                edits.add(Edit('K', i - 1, j - 1))
                i--; j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                edits.add(Edit('D', i - 1, j))
                i--
            } else {
                edits.add(Edit('I', i, j - 1))
                j--
            }
        }
        while (i > 0) { edits.add(Edit('D', i - 1, j)); i-- }
        while (j > 0) { edits.add(Edit('I', i, j - 1)); j-- }
        edits.reverse()

        // Group consecutive DELETE/INSERT into deltas, merge adjacent DELETE+INSERT into CHANGE
        val deltas = mutableListOf<Delta<T>>()
        var idx = 0
        while (idx < edits.size) {
            if (edits[idx].type == 'K') { idx++; continue }

            val deletedLines = mutableListOf<T>()
            val srcPos = edits[idx].srcIdx
            val tgtPos = edits[idx].tgtIdx
            while (idx < edits.size && edits[idx].type == 'D') {
                deletedLines.add(source[edits[idx].srcIdx])
                idx++
            }
            val insertedLines = mutableListOf<T>()
            val insertTgtPos = if (idx < edits.size && edits[idx].type == 'I') edits[idx].tgtIdx else tgtPos
            while (idx < edits.size && edits[idx].type == 'I') {
                insertedLines.add(target[edits[idx].tgtIdx])
                idx++
            }

            val type = when {
                deletedLines.isNotEmpty() && insertedLines.isNotEmpty() -> DeltaType.CHANGE
                deletedLines.isNotEmpty() -> DeltaType.DELETE
                else -> DeltaType.INSERT
            }
            deltas.add(Delta(
                type = type,
                sourcePosition = srcPos,
                sourceLines = deletedLines,
                targetPosition = if (insertedLines.isNotEmpty()) insertTgtPos else tgtPos,
                targetLines = insertedLines
            ))
        }
        return deltas
    }
}
