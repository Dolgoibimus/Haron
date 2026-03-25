package com.vamp.haron.presentation.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Regex to extract tag from log line: "2025-03-05 12:00:00.123 [D] Ecosystem/Haron: message" or "Ecosystem/Haron/TermBuf: ..." */
private val TAG_REGEX = Regex("""\[\w] Ecosystem/([\w/]+):""")

private fun extractTag(line: String): String? = TAG_REGEX.find(line)?.groupValues?.get(1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var allLines by remember { mutableStateOf(emptyList<String>()) }
    var categories by remember { mutableStateOf(emptyList<String>()) }
    var selectedCategory by remember { mutableStateOf<String?>(null) } // null = all
    var showShareDialog by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(EcosystemLogger.isPaused) }
    val listState = rememberLazyListState()

    // Auto-refresh every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            // Sync with EcosystemLogger (voice commands change isPaused externally)
            isPaused = EcosystemLogger.isPaused
            val file = EcosystemLogger.getLogFile()
            val lines = file?.readLines() ?: emptyList()
            allLines = lines
            val tags = lines.mapNotNull { extractTag(it) }.distinct().sorted()
            categories = tags
            delay(2000)
        }
    }

    // Filtered lines
    val displayLines = remember(allLines, selectedCategory) {
        if (selectedCategory == null) allLines
        else allLines.filter { extractTag(it) == selectedCategory }
    }

    // Auto-scroll to bottom when lines change
    LaunchedEffect(displayLines.size) {
        if (displayLines.isNotEmpty()) {
            listState.animateScrollToItem(displayLines.size - 1)
        }
    }

    // Share dialog
    if (showShareDialog && categories.isNotEmpty()) {
        ShareCategoriesDialog(
            categories = categories,
            allLines = allLines,
            onDismiss = { showShareDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isPaused) "${stringResource(R.string.logs_title)} (${stringResource(R.string.gesture_action_logs_pause)})"
                        else stringResource(R.string.logs_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        isPaused = !isPaused
                        EcosystemLogger.isPaused = isPaused
                    }) {
                        Icon(
                            if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                            contentDescription = if (isPaused) stringResource(R.string.gesture_action_logs_resume)
                                else stringResource(R.string.gesture_action_logs_pause)
                        )
                    }
                    IconButton(onClick = {
                        EcosystemLogger.clearLogs()
                        allLines = emptyList()
                        categories = emptyList()
                        selectedCategory = null
                    }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.logs_clear))
                    }
                    IconButton(onClick = {
                        if (allLines.isNotEmpty()) showShareDialog = true
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.logs_share))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .swipeBackFromLeft(onBack = onBack)
                .padding(padding)
        ) {
            // Category filter chips
            if (categories.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text(stringResource(R.string.logs_all)) }
                    )
                    categories.forEach { tag ->
                        Spacer(Modifier.width(4.dp))
                        FilterChip(
                            selected = selectedCategory == tag,
                            onClick = { selectedCategory = if (selectedCategory == tag) null else tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }

            if (displayLines.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.logs_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp)
                ) {
                    items(displayLines, key = null) { line ->
                        Text(
                            text = line,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShareCategoriesDialog(
    categories: List<String>,
    allLines: List<String>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val selected = remember { categories.map { true }.toMutableStateList() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.logs_share_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.logs_select_categories),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                categories.forEachIndexed { index, tag ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected[index] = !selected[index] },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = selected[index],
                            onCheckedChange = { selected[index] = it }
                        )
                        Text(tag, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedTags = categories.filterIndexed { i, _ -> selected[i] }.toSet()
                if (selectedTags.isEmpty()) {
                    onDismiss()
                    return@TextButton
                }
                val filtered = allLines.filter { line ->
                    val tag = extractTag(line)
                    tag != null && tag in selectedTags
                }
                shareFilteredLogs(context, filtered)
                onDismiss()
            }) {
                Text(stringResource(R.string.logs_share_selected))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

private fun shareFilteredLogs(context: android.content.Context, lines: List<String>) {
    try {
        val tempFile = File(context.cacheDir, "filtered_logs.txt")
        FileWriter(tempFile).use { w -> lines.forEach { w.appendLine(it) } }
        val zipFile = File(context.cacheDir, "logs.zip")
        ZipOutputStream(zipFile.outputStream()).use { zos ->
            zos.putNextEntry(ZipEntry(tempFile.name))
            tempFile.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
        tempFile.delete()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zipFile)
        val intent = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "application/zip"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            context.getString(R.string.logs_share)
        )
        context.startActivity(intent)
    } catch (_: Exception) { }
}
