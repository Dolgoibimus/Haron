package com.vamp.haron.presentation.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/** No-op stub for mini variant. Video player disabled. */
@Composable
fun MediaPlayerScreen(
    startIndex: Int = 0,
    prefs: com.vamp.haron.data.datastore.HaronPreferences? = null,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Video player not available in mini build")
    }
}
