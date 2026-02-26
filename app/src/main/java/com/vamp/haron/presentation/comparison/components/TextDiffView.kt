package com.vamp.haron.presentation.comparison.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.domain.model.DiffLine
import com.vamp.haron.domain.model.DiffLineType
import com.vamp.haron.domain.model.TextDiffResult

@Composable
fun TextDiffView(
    diff: TextDiffResult,
    modifier: Modifier = Modifier
) {
    val leftState = rememberLazyListState()
    val rightState = rememberLazyListState()

    // Synchronize scroll
    LaunchedEffect(Unit) {
        snapshotFlow { leftState.firstVisibleItemIndex to leftState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (rightState.firstVisibleItemIndex != index ||
                    rightState.firstVisibleItemScrollOffset != offset
                ) {
                    rightState.scrollToItem(index, offset)
                }
            }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { rightState.firstVisibleItemIndex to rightState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (leftState.firstVisibleItemIndex != index ||
                    leftState.firstVisibleItemScrollOffset != offset
                ) {
                    leftState.scrollToItem(index, offset)
                }
            }
    }

    Row(modifier = modifier.fillMaxSize()) {
        // Left panel
        DiffColumn(
            lines = diff.leftLines,
            state = leftState,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
        VerticalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
        // Right panel
        DiffColumn(
            lines = diff.rightLines,
            state = rightState,
            modifier = Modifier.weight(1f).fillMaxHeight()
        )
    }
}

@Composable
private fun DiffColumn(
    lines: List<DiffLine>,
    state: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val hScroll = rememberScrollState()
    LazyColumn(
        state = state,
        modifier = modifier
    ) {
        itemsIndexed(lines) { _, line ->
            val bgColor = when (line.type) {
                DiffLineType.ADDED -> Color(0x3300C853)
                DiffLineType.REMOVED -> Color(0x33FF1744)
                DiffLineType.MODIFIED -> Color(0x33FFD600)
                DiffLineType.UNCHANGED -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bgColor)
                    .horizontalScroll(hScroll)
            ) {
                // Line number
                Text(
                    text = line.lineNumber?.toString() ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.width(36.dp).padding(horizontal = 4.dp)
                )
                // Content
                Text(
                    text = line.text,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    ),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    softWrap = false,
                    maxLines = 1
                )
            }
        }
    }
}
