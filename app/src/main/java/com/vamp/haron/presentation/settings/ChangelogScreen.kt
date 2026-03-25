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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft

private data class ChangelogEntry(
    val title: String,
    val items: List<ChangelogItem>
)

private data class ChangelogItem(
    val text: String,
    val isFix: Boolean = false
)

private fun parseChangelog(raw: String): List<ChangelogEntry> {
    val entries = mutableListOf<ChangelogEntry>()
    var currentTitle = ""
    var currentItems = mutableListOf<ChangelogItem>()

    for (line in raw.lines()) {
        when {
            line.startsWith("# ") -> {
                if (currentTitle.isNotEmpty()) {
                    entries.add(ChangelogEntry(currentTitle, currentItems.toList()))
                }
                currentTitle = line.removePrefix("# ").trim()
                currentItems = mutableListOf()
            }
            line.startsWith("- ") -> {
                val text = line.removePrefix("- ").trim()
                val isFix = text.startsWith("Fix:", ignoreCase = true) ||
                    text.startsWith("Фикс:", ignoreCase = true)
                currentItems.add(ChangelogItem(text, isFix))
            }
        }
    }
    if (currentTitle.isNotEmpty()) {
        entries.add(ChangelogEntry(currentTitle, currentItems.toList()))
    }
    return entries
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val raw = remember {
        context.resources.openRawResource(R.raw.changelog)
            .bufferedReader().readText()
    }
    val entries = remember(raw) { parseChangelog(raw) }
    val expanded = remember { mutableStateMapOf<Int, Boolean>() }

    if (expanded.isEmpty() && entries.isNotEmpty()) {
        expanded[0] = true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.changelog_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            items(entries.size) { index ->
                val entry = entries[index]
                val isExpanded = expanded[index] == true
                val features = entry.items.filter { !it.isFix }
                val fixes = entry.items.filter { it.isFix }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded[index] = !isExpanded }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${entry.items.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Icon(
                            if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            features.forEach { item ->
                                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(
                                        text = "•",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            if (fixes.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.changelog_fixes),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Spacer(Modifier.height(4.dp))
                                fixes.forEach { item ->
                                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                                        Text(
                                            text = "✓",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.tertiary
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = item.text.removePrefix("Fix: ").removePrefix("Фикс: "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (index < entries.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
