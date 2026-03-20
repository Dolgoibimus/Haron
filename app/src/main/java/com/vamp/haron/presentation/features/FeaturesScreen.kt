package com.vamp.haron.presentation.features

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamp.haron.R

private data class FeatureItem(
    val text: String,
    val isNew: Boolean,
    val isSubHeader: Boolean = false,
    val details: List<String> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val sections = remember {
        parseFeaturesFile(context.resources.openRawResource(R.raw.features)
            .bufferedReader().readText())
    }
    var detailSection by remember { mutableStateOf<Pair<String, List<FeatureItem>>?>(null) }
    val scrollState = rememberScrollState()

    // Detail overlay
    if (detailSection != null) {
        BackHandler { detailSection = null }
        FeatureCategoryDetailScreen(
            header = detailSection!!.first,
            items = detailSection!!.second,
            onBack = { detailSection = null }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.features_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            sections.forEach { (header, items) ->
                val hasDetails = items.any { it.details.isNotEmpty() }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    if (hasDetails) {
                        IconButton(
                            onClick = { detailSection = header to items },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                items.forEach { item ->
                    if (item.isSubHeader) {
                        Text(
                            text = item.text,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            modifier = Modifier.padding(top = 6.dp, bottom = 2.dp, start = 4.dp)
                        )
                    } else {
                        val displayText = if (item.isNew) item.text.removeSuffix("[NEW]").trim() else item.text
                        Text(
                            text = "• $displayText",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (item.isNew) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureCategoryDetailScreen(
    header: String,
    items: List<FeatureItem>,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(header) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            items.forEachIndexed { index, item ->
                if (item.isSubHeader) {
                    if (index > 0) Spacer(Modifier.height(12.dp))
                    Text(
                        text = item.text,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                } else {
                    val displayText = if (item.isNew) item.text.removeSuffix("[NEW]").trim() else item.text

                    // Feature title
                    Text(
                        text = displayText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.isNew) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    // Details
                    if (item.details.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(12.dp)
                        ) {
                            Column {
                                item.details.forEach { detail ->
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (index < items.lastIndex && !items.getOrNull(index + 1)?.isSubHeader!!) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun parseFeaturesFile(text: String): List<Pair<String, List<FeatureItem>>> {
    val sections = mutableListOf<Pair<String, List<FeatureItem>>>()
    var currentHeader = ""
    var currentItems = mutableListOf<FeatureItem>()

    text.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("## ") -> {
                currentItems.add(FeatureItem(
                    text = trimmed.removePrefix("## "),
                    isNew = false,
                    isSubHeader = true
                ))
            }
            trimmed.startsWith("# ") -> {
                if (currentHeader.isNotEmpty()) {
                    sections.add(currentHeader to currentItems.toList())
                }
                currentHeader = trimmed.removePrefix("# ")
                currentItems = mutableListOf()
            }
            trimmed.startsWith("- ") -> {
                val itemText = trimmed.removePrefix("- ")
                currentItems.add(FeatureItem(
                    text = itemText,
                    isNew = itemText.endsWith("[NEW]")
                ))
            }
            trimmed.startsWith("> ") -> {
                val detail = trimmed.removePrefix("> ")
                if (currentItems.isNotEmpty()) {
                    val last = currentItems.removeAt(currentItems.lastIndex)
                    currentItems.add(last.copy(details = last.details + detail))
                }
            }
        }
    }
    if (currentHeader.isNotEmpty()) {
        sections.add(currentHeader to currentItems.toList())
    }
    return sections
}
