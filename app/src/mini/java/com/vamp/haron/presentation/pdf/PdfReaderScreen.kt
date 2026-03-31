package com.vamp.haron.presentation.pdf

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** No-op stub for mini variant. PDF reader disabled. */
@Composable
fun PdfReaderScreen(
    filePath: String,
    fileName: String,
    onBack: () -> Unit,
    onNavigateToLibrary: (() -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("PDF reader not available in mini build")
    }
}
