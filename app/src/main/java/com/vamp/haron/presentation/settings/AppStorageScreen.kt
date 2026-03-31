package com.vamp.haron.presentation.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.common.util.toFileSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class DirEntry(
    val name: String,
    val path: String,
    val isDir: Boolean,
    val size: Long,
    val children: List<DirEntry> = emptyList()
)

// Directories that must NOT be shown (security-sensitive)
private val HIDDEN_DIRS = setOf(".haron_secure")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStorageScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<DirEntry>() }
    var totalSize by remember { mutableStateOf(0L) }
    val expandedPaths = remember { mutableStateListOf<String>() }

    fun scan() {
        scope.launch {
            val filesDir = context.filesDir
            val result = withContext(Dispatchers.IO) {
                scanDir(filesDir)
            }
            entries.clear()
            entries.addAll(result)
            totalSize = result.sumOf { it.size }
        }
    }

    LaunchedEffect(Unit) { scan() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.app_storage_title))
                        Text(
                            totalSize.toFileSize(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .swipeBackFromLeft(onBack = onBack)
                .padding(padding)
        ) {
            items(entries, key = { it.path }) { entry ->
                if (entry.isDir) {
                    val expanded = entry.path in expandedPaths
                    // Directory header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (expanded) expandedPaths.remove(entry.path)
                                else expandedPaths.add(entry.path)
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                entry.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "${entry.children.size} ${stringResource(R.string.app_storage_files)} — ${entry.size.toFileSize()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (entry.children.isNotEmpty()) {
                            // Delete all in dir
                            IconButton(onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        File(entry.path).listFiles()?.forEach { it.delete() }
                                    }
                                    expandedPaths.remove(entry.path)
                                    scan()
                                }
                            }) {
                                Icon(
                                    Icons.Filled.DeleteSweep,
                                    contentDescription = stringResource(R.string.app_storage_clear_dir),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Expanded children
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            entry.children.forEach { child ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 52.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.InsertDriveFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            child.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            child.size.toFileSize(),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            withContext(Dispatchers.IO) { File(child.path).delete() }
                                            scan()
                                        }
                                    }) {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                } else {
                    // Top-level file
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.InsertDriveFile,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                entry.size.toFileSize(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { File(entry.path).delete() }
                                scan()
                            }
                        }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.delete),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

private fun scanDir(dir: File): List<DirEntry> {
    val items = dir.listFiles() ?: return emptyList()
    return items
        .filter { it.name !in HIDDEN_DIRS }
        .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
        .map { file ->
            if (file.isDirectory) {
                val children = file.listFiles()
                    ?.filter { it.isFile }
                    ?.sortedByDescending { it.length() }
                    ?.map { DirEntry(it.name, it.absolutePath, false, it.length()) }
                    ?: emptyList()
                val totalSize = children.sumOf { it.size }
                DirEntry(file.name, file.absolutePath, true, totalSize, children)
            } else {
                DirEntry(file.name, file.absolutePath, false, file.length())
            }
        }
}
