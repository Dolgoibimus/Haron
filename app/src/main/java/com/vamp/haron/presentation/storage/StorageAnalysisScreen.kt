package com.vamp.haron.presentation.storage

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.usecase.StorageCategory

private val categoryColors = listOf(
    Color(0xFF4CAF50), // green — Фото
    Color(0xFF2196F3), // blue — Видео
    Color(0xFFFF9800), // orange — Музыка
    Color(0xFF9C27B0), // purple — Документы
    Color(0xFF795548), // brown — Архивы
    Color(0xFFE91E63), // pink — APK
    Color(0xFF607D8B)  // grey — Прочее
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageAnalysisScreen(
    onBack: () -> Unit,
    viewModel: StorageAnalysisViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.startScan()
    }
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Анализ памяти") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startScan() },
                        enabled = !state.isLoading
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.selectedFiles.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { viewModel.deleteSelectedFiles() },
                    containerColor = MaterialTheme.colorScheme.error
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Удалить выбранные",
                        tint = MaterialTheme.colorScheme.onError
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val analysis = state.analysis

            // Pie chart
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    StoragePieChart(
                        categories = analysis.categories,
                        totalSize = analysis.usedSize,
                        modifier = Modifier.size(180.dp)
                    )
                }
            }

            // Storage summary text
            item {
                Text(
                    text = "Занято ${analysis.usedSize.toFileSize()} из ${analysis.totalSize.toFileSize()}, " +
                            "свободно ${analysis.freeSize.toFileSize()}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    fontWeight = FontWeight.Medium
                )
            }

            // Scanning progress
            if (state.isLoading) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Сканирование... ${analysis.scannedFiles} файлов" +
                                    if (analysis.currentPath.isNotEmpty()) " — ${analysis.currentPath}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                        )
                    }
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
            }

            // Categories
            val categories = analysis.categories
            items(categories, key = { it.name }) { category ->
                val colorIndex = categories.indexOf(category).coerceIn(0, categoryColors.lastIndex)
                val color = categoryColors[colorIndex]
                val isExpanded = state.expandedCategory == category.name

                CategoryRow(
                    category = category,
                    color = color,
                    totalUsed = analysis.usedSize,
                    isExpanded = isExpanded,
                    onClick = { viewModel.toggleCategory(category.name) }
                )

                // Expanded: show files in this category from largeFiles
                AnimatedVisibility(visible = isExpanded) {
                    val categoryFiles = analysis.largeFiles.filter { lf ->
                        val catName = mapIconToCategory(lf.entry.iconRes())
                        catName == category.name
                    }
                    Column {
                        if (categoryFiles.isEmpty()) {
                            Text(
                                text = "Нет крупных файлов (>10 МБ) в этой категории",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                            )
                        } else {
                            categoryFiles.forEach { lf ->
                                val isSelected = lf.entry.path in state.selectedFiles
                                LargeFileRow(
                                    name = lf.entry.name,
                                    size = lf.entry.size,
                                    relativePath = lf.relativePath,
                                    isSelected = isSelected,
                                    onClick = { viewModel.toggleFileSelection(lf.entry.path) }
                                )
                            }
                        }
                    }
                }
            }

            // Large files section
            if (analysis.largeFiles.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider()
                    Text(
                        text = "Крупные файлы (>10 МБ)",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                items(analysis.largeFiles, key = { it.entry.path }) { lf ->
                    val isSelected = lf.entry.path in state.selectedFiles
                    LargeFileRow(
                        name = lf.entry.name,
                        size = lf.entry.size,
                        relativePath = lf.relativePath,
                        isSelected = isSelected,
                        onClick = { viewModel.toggleFileSelection(lf.entry.path) }
                    )
                }
            }

            // Bottom spacer for FAB
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun StoragePieChart(
    categories: List<StorageCategory>,
    totalSize: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        if (totalSize <= 0 || categories.isEmpty()) {
            // Empty state — draw full grey circle
            drawArc(
                color = Color.LightGray,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 32.dp.toPx()),
                topLeft = Offset(16.dp.toPx(), 16.dp.toPx()),
                size = Size(
                    size.width - 32.dp.toPx(),
                    size.height - 32.dp.toPx()
                )
            )
            return@Canvas
        }

        var startAngle = -90f
        categories.forEachIndexed { index, category ->
            val sweep = (category.size.toFloat() / totalSize * 360f).coerceAtLeast(1f)
            val color = categoryColors.getOrElse(index) { Color.Gray }
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(width = 32.dp.toPx()),
                topLeft = Offset(16.dp.toPx(), 16.dp.toPx()),
                size = Size(
                    size.width - 32.dp.toPx(),
                    size.height - 32.dp.toPx()
                )
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun CategoryRow(
    category: StorageCategory,
    color: Color,
    totalUsed: Long,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = categoryIcon(category.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = category.size.toFileSize(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val fraction = if (totalUsed > 0) (category.size.toFloat() / totalUsed).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = color
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${category.fileCount} файлов",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(4.dp))
        Icon(
            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LargeFileRow(
    name: String,
    size: Long,
    relativePath: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 32.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = relativePath,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = size.toFileSize(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun categoryIcon(icon: String): ImageVector {
    return when (icon) {
        "image" -> Icons.Filled.Image
        "video" -> Icons.Filled.VideoFile
        "audio" -> Icons.Filled.Audiotrack
        "document" -> Icons.Filled.Description
        "archive" -> Icons.Filled.Folder
        "apk" -> Icons.Filled.PhoneAndroid
        else -> Icons.Filled.InsertDriveFile
    }
}

private fun mapIconToCategory(iconType: String): String {
    return when (iconType) {
        "image" -> "Фото"
        "video" -> "Видео"
        "audio" -> "Музыка"
        "pdf", "document", "spreadsheet", "presentation", "text", "code" -> "Документы"
        "archive" -> "Архивы"
        "apk" -> "APK"
        else -> "Прочее"
    }
}

