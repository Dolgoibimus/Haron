package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.toFileSize

@Composable
fun BreadcrumbBar(
    displayPath: String,
    currentPath: String = "",
    safVolumeLabel: String = "",
    folderSize: Long = 0L,
    storageTotalSize: Long = 0L,
    onSegmentClick: (String) -> Unit = {},
    onSizeClick: () -> Unit = {},
    cloudBreadcrumbs: List<Pair<String, String>> = emptyList(),
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(displayPath) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    val isSaf = currentPath.startsWith("content://")
    val isCloud = currentPath.startsWith("cloud://")

    // Разбиваем displayPath на сегменты
    val segments = displayPath.trim('/').split('/').filter { it.isNotEmpty() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSaf) {
                // SAF root label
                val rootLabel = safVolumeLabel.ifEmpty { stringResource(R.string.sd_card_default) }
                Text(
                    text = rootLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (segments.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.clickable {
                        // Navigate to SAF tree root — rebuild URI from currentPath
                        val treeRoot = extractSafTreeRoot(currentPath)
                        if (treeRoot != null) onSegmentClick(treeRoot)
                    }
                )

                // SAF path segments
                segments.forEachIndexed { index, segment ->
                    Text(
                        text = " › ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val isLast = index == segments.lastIndex
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLast) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = if (!isLast) {
                            Modifier.clickable {
                                val targetPath = buildSafBreadcrumbPath(currentPath, segments, index)
                                if (targetPath != null) onSegmentClick(targetPath)
                            }
                        } else {
                            Modifier
                        }
                    )
                }
            } else if (isCloud && cloudBreadcrumbs.isNotEmpty()) {
                // Cloud path with breadcrumb stack — all segments are clickable
                cloudBreadcrumbs.forEachIndexed { index, (name, cloudPath) ->
                    if (index > 0) {
                        Text(
                            text = " › ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    val isLast = index == cloudBreadcrumbs.lastIndex
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLast) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = if (!isLast) {
                            Modifier.clickable { onSegmentClick(cloudPath) }
                        } else {
                            Modifier
                        }
                    )
                }
            } else if (isCloud) {
                // Cloud path without breadcrumb stack — fallback: root only clickable
                val cloudScheme = currentPath.removePrefix("cloud://").substringBefore('/')
                val cloudRoot = "cloud://$cloudScheme/root"
                val providerLabel = segments.firstOrNull() ?: ""
                val cloudSegments = segments.drop(1)

                Text(
                    text = providerLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cloudSegments.isEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onSegmentClick(cloudRoot) }
                )
                cloudSegments.forEachIndexed { index, segment ->
                    Text(" › ", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(segment, style = MaterialTheme.typography.labelMedium, color = if (index == cloudSegments.lastIndex) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // Regular file system — original logic
                Text(
                    text = stringResource(R.string.storage),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (segments.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.clickable {
                        onSegmentClick(HaronConstants.ROOT_PATH)
                    }
                )

                segments.forEachIndexed { index, segment ->
                    Text(
                        text = " › ",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val isLast = index == segments.lastIndex
                    val segmentPath = HaronConstants.ROOT_PATH + "/" +
                        segments.take(index + 1).joinToString("/")
                    Text(
                        text = segment,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLast) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = if (!isLast) {
                            Modifier.clickable { onSegmentClick(segmentPath) }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (storageTotalSize > 0 && folderSize > 0)
                "${folderSize.toFileSize()} / ${storageTotalSize.toFileSize()}"
            else
                folderSize.toFileSize(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            modifier = Modifier.clickable { onSizeClick() }
        )
    }
}

/**
 * Extract the SAF tree root URI from a document URI.
 * content://authority/tree/XXXX-XXXX%3A/document/XXXX-XXXX%3Afolder%2Fsub
 * → content://authority/tree/XXXX-XXXX%3A/document/XXXX-XXXX%3A
 */
private fun extractSafTreeRoot(currentPath: String): String? {
    if (!currentPath.startsWith("content://")) return null
    return try {
        val uri = android.net.Uri.parse(currentPath)
        val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val treeUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            uri, treeDocId
        )
        treeUri.toString()
    } catch (_: Exception) {
        null
    }
}

/**
 * Build a SAF document URI for a specific breadcrumb segment.
 * Uses the tree URI and reconstructs the document ID with path segments.
 */
private fun buildSafBreadcrumbPath(
    currentPath: String,
    segments: List<String>,
    targetIndex: Int
): String? {
    if (!currentPath.startsWith("content://")) return null
    return try {
        val uri = android.net.Uri.parse(currentPath)
        val treeDocId = android.provider.DocumentsContract.getTreeDocumentId(uri)
        val parts = treeDocId.split(":")
        val volumeId = parts[0]
        val treePath = if (parts.size > 1) parts[1] else ""
        val targetPath = if (treePath.isEmpty()) {
            segments.take(targetIndex + 1).joinToString("/")
        } else {
            treePath.trimEnd('/') + "/" + segments.take(targetIndex + 1).joinToString("/")
        }
        val targetDocId = "$volumeId:$targetPath"
        val targetUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(
            uri, targetDocId
        )
        targetUri.toString()
    } catch (_: Exception) {
        null
    }
}
