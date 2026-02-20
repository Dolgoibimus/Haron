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
import androidx.compose.ui.unit.dp
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.common.util.toFileSize

@Composable
fun BreadcrumbBar(
    displayPath: String,
    folderSize: Long = 0L,
    onSegmentClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    LaunchedEffect(displayPath) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Разбиваем displayPath на сегменты
    // displayPath = "/Documents/Photos" или "/"
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
            // Корневой сегмент "Хранилище"
            Text(
                text = "Хранилище",
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

            // Остальные сегменты
            segments.forEachIndexed { index, segment ->
                Text(
                    text = " › ",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val isLast = index == segments.lastIndex
                // Собираем полный путь до этого сегмента
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
        if (folderSize > 0L) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = folderSize.toFileSize(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1
            )
        }
    }
}
