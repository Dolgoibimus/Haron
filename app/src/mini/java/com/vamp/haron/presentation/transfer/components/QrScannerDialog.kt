package com.vamp.haron.presentation.transfer.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** No-op stub for mini variant. QR scanner disabled (no CameraX/ML Kit). */
@Composable
fun QrScannerDialog(
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Dismiss immediately — QR scanner not available in mini
    LaunchedEffect(Unit) { onDismiss() }
}
