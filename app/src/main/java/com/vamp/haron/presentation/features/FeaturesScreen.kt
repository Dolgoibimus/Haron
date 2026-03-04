package com.vamp.haron.presentation.features

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            sections.forEach { (header, items) ->
                Text(
                    text = header,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
                items.forEach { item ->
                    Text(
                        text = "• $item",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun parseFeaturesFile(text: String): List<Pair<String, List<String>>> {
    val sections = mutableListOf<Pair<String, List<String>>>()
    var currentHeader = ""
    var currentItems = mutableListOf<String>()

    text.lines().forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.startsWith("# ") -> {
                if (currentHeader.isNotEmpty()) {
                    sections.add(currentHeader to currentItems.toList())
                }
                currentHeader = trimmed.removePrefix("# ")
                currentItems = mutableListOf()
            }
            trimmed.startsWith("- ") -> {
                currentItems.add(trimmed.removePrefix("- "))
            }
        }
    }
    if (currentHeader.isNotEmpty()) {
        sections.add(currentHeader to currentItems.toList())
    }
    return sections
}
