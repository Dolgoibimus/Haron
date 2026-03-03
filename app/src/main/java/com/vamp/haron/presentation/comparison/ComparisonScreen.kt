package com.vamp.haron.presentation.comparison

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.presentation.comparison.components.FolderDiffView
import com.vamp.haron.presentation.comparison.components.TextDiffView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComparisonScreen(
    onBack: () -> Unit,
    viewModel: ComparisonViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()

    val handleBack: () -> Unit = {
        if (state.isViewingFileDiff) {
            viewModel.goBackToFolderList()
        } else {
            onBack()
        }
    }

    BackHandler { handleBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.compare_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (state.leftName.isNotEmpty()) {
                            Text(
                                "${state.leftName} \u2194 ${state.rightName}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.error != null -> {
                    Text(
                        text = state.error ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center).padding(16.dp)
                    )
                }
                state.mode == ComparisonMode.LOADING -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        if (state.progressTotal > 0) {
                            Spacer(Modifier.height(12.dp))
                            LinearProgressIndicator(
                                progress = { state.progressCurrent.toFloat() / state.progressTotal },
                                modifier = Modifier.width(200.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${state.progressCurrent} / ${state.progressTotal}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                state.mode == ComparisonMode.TEXT && state.textDiff != null -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Summary bar
                        val diff = state.textDiff!!
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(
                                    R.string.compare_text_summary,
                                    diff.addedCount,
                                    diff.removedCount,
                                    diff.modifiedCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextDiffView(
                            diff = diff,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                state.mode == ComparisonMode.FOLDER -> {
                    FolderDiffView(
                        entries = state.folderEntries,
                        filterStatus = state.filterStatus,
                        onFilterChange = { viewModel.setFilter(it) },
                        onOpenDiff = { entry ->
                            viewModel.openFileDiff(entry.relativePath)
                        }
                    )
                }
                state.mode == ComparisonMode.BINARY && state.binaryMetadata != null -> {
                    BinaryComparisonView(state.binaryMetadata!!)
                }
            }
        }
    }
}

@Composable
private fun BinaryComparisonView(meta: com.vamp.haron.domain.model.FileMetadataComparison) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Icon(
            if (meta.sameContent) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (meta.sameContent) Color(0xFF4CAF50) else Color(0xFFFFC107),
            modifier = Modifier.padding(8.dp)
        )
        Text(
            text = stringResource(
                if (meta.sameContent) R.string.compare_binary_identical
                else R.string.compare_binary_different
            ),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.compare_left), style = MaterialTheme.typography.labelMedium)
                Text(meta.leftSize.toFileSize(), style = MaterialTheme.typography.bodyMedium)
                Text(dateFormat.format(Date(meta.leftModified)), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.compare_right), style = MaterialTheme.typography.labelMedium)
                Text(meta.rightSize.toFileSize(), style = MaterialTheme.typography.bodyMedium)
                Text(dateFormat.format(Date(meta.rightModified)), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
