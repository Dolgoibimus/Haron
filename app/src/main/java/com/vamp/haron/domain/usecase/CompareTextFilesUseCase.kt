package com.vamp.haron.domain.usecase

import com.vamp.haron.common.util.DeltaType
import com.vamp.haron.common.util.MyersDiff
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
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
            EcosystemLogger.d(HaronConstants.TAG, "CompareTextFilesUseCase: comparing left=$leftPath, right=$rightPath")
            val leftLines = File(leftPath).readLines()
            val rightLines = File(rightPath).readLines()

            val deltas = MyersDiff.diff(leftLines, rightLines)

            val left = mutableListOf<DiffLine>()
            val right = mutableListOf<DiffLine>()
            var addedCount = 0
            var removedCount = 0
            var modifiedCount = 0

            var leftIdx = 0
            var rightIdx = 0

            for (delta in deltas) {
                // Unchanged lines before this delta
                while (leftIdx < delta.sourcePosition) {
                    left.add(DiffLine(leftIdx + 1, leftLines[leftIdx], DiffLineType.UNCHANGED))
                    right.add(DiffLine(rightIdx + 1, rightLines[rightIdx], DiffLineType.UNCHANGED))
                    leftIdx++
                    rightIdx++
                }

                when (delta.type) {
                    DeltaType.DELETE -> {
                        delta.sourceLines.forEachIndexed { i, line ->
                            left.add(DiffLine(leftIdx + i + 1, line, DiffLineType.REMOVED))
                            right.add(DiffLine(null, "", DiffLineType.REMOVED))
                        }
                        removedCount += delta.sourceLines.size
                        leftIdx += delta.sourceLines.size
                    }
                    DeltaType.INSERT -> {
                        delta.targetLines.forEachIndexed { i, line ->
                            left.add(DiffLine(null, "", DiffLineType.ADDED))
                            right.add(DiffLine(rightIdx + i + 1, line, DiffLineType.ADDED))
                        }
                        addedCount += delta.targetLines.size
                        rightIdx += delta.targetLines.size
                    }
                    DeltaType.CHANGE -> {
                        val maxLen = maxOf(delta.sourceLines.size, delta.targetLines.size)
                        for (i in 0 until maxLen) {
                            val srcLine = delta.sourceLines.getOrNull(i)
                            val tgtLine = delta.targetLines.getOrNull(i)
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
                        leftIdx += delta.sourceLines.size
                        rightIdx += delta.targetLines.size
                    }
                }
            }

            // Remaining unchanged lines
            while (leftIdx < leftLines.size && rightIdx < rightLines.size) {
                left.add(DiffLine(leftIdx + 1, leftLines[leftIdx], DiffLineType.UNCHANGED))
                right.add(DiffLine(rightIdx + 1, rightLines[rightIdx], DiffLineType.UNCHANGED))
                leftIdx++
                rightIdx++
            }

            EcosystemLogger.d(HaronConstants.TAG, "CompareTextFilesUseCase: complete, added=$addedCount, removed=$removedCount, modified=$modifiedCount")
            TextDiffResult(
                leftLines = left,
                rightLines = right,
                addedCount = addedCount,
                removedCount = removedCount,
                modifiedCount = modifiedCount
            )
        }
}
