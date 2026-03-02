package com.vamp.haron.presentation.cast.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vamp.haron.R
import kotlinx.coroutines.flow.StateFlow

@Composable
fun CastButton(
    isConnected: StateFlow<Boolean>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val connected by isConnected.collectAsState()

    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (connected) Icons.Filled.CastConnected else Icons.Filled.Cast,
            contentDescription = stringResource(R.string.cast_title),
            tint = if (connected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
