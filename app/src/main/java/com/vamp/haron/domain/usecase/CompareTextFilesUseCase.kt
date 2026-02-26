package com.vamp.haron.domain.usecase

import com.github.difflib.DiffUtils
import com.github.difflib.patch.DeltaType
import com.vamp.haron.domain.model.DiffLine
import com.vamp.haron.domain.model.DiffLineType
import com.vamp.haron.domain.model.TextDiffResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

class CompareTextFilesUseCase @Inject constructor() {

    suspend operator fun invoke(leftPath: String, rightPath: String): TextDiffResult =
        withContext(Dispatchers.IO) {
            val leftLines = File(leftPath).readLines()
            val rightLines = File(rightPath).readLines()

            val patch = DiffUtils.diff(leftLines, rightLines)

            val left = mutableListOf<DiffLine>()
            val right = mutableListOf<DiffLine>()
            var addedCount = 0
            var removedCount = 0
            var modifiedCount = 0

            var leftIdx = 0
            var rightIdx = 0

            for (delta in patch.deltas) {
                // Unchanged lines before this delta
                while (leftIdx < delta.source.position) {
                    left.add(DiffLine(leftIdx + 1, leftLines[leftIdx], DiffLineType.UNCHANGED))
                    right.add(DiffLine(rightIdx + 1, rightLines[rightIdx], DiffLineType.UNCHANGED))
                    leftIdx++
                    rightIdx++
                }

                when (delta.type) {
                    DeltaType.DELETE -> {
                        delta.source.lines.forEachIndexed { i, line ->
                            left.add(DiffLine(leftIdx + i + 1, line, DiffLineType.REMOVED))
                            right.add(DiffLine(null, "", DiffLineType.REMOVED))
                        }
                        removedCount += delta.source.lines.size
                        leftIdx += delta.source.lines.size
                    }
                    DeltaType.INSERT -> {
                        delta.target.lines.forEachIndexed { i, line ->
                            left.add(DiffLine(null, "", DiffLineType.ADDED))
                            right.add(DiffLine(rightIdx + i + 1, line, DiffLineType.ADDED))
                        }
                        addedCount += delta.target.lines.size
                        rightIdx += delta.target.lines.size
                    }
                    DeltaType.CHANGE -> {
                        val maxLen = maxOf(delta.source.lines.size, delta.target.lines.size)
                        for (i in 0 until maxLen) {
                            val srcLine = delta.source.lines.getOrNull(i)
                            val tgtLine = delta.target.lines.getOrNull(i)
                            left.add(
                                DiffLine(
                                    if (srcLine != null) leftIdx + i + 1 else null,
                                    srcLine ?: "",
                                    DiffLineType.MODIFIED
                                )
                            )
                            right.add(
                                DiffLine(
                                    if (tgtLine != null) rightIdx + i + 1 else null,
                                    tgtLine ?: "",
                                    DiffLineType.MODIFIED
                                )
                            )
                        }
                        modifiedCount += maxLen
                        leftIdx += delta.source.lines.size
                        rightIdx += delta.target.lines.size
                    }
                    else -> {}
                }
            }

            // Remaining unchanged lines
            while (leftIdx < leftLines.size && rightIdx < rightLines.size) {
                left.add(DiffLine(leftIdx + 1, leftLines[leftIdx], DiffLineType.UNCHANGED))
                right.add(DiffLine(rightIdx + 1, rightLines[rightIdx], DiffLineType.UNCHANGED))
                leftIdx++
                rightIdx++
            }

            TextDiffResult(
                leftLines = left,
                rightLines = right,
                addedCount = addedCount,
                removedCount = removedCount,
                modifiedCount = modifiedCount
            )
        }
}
