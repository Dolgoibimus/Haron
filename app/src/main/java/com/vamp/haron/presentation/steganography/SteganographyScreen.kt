package com.vamp.haron.presentation.steganography

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.StegoPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SteganographyScreen(
    onBack: () -> Unit,
    viewModel: SteganographyViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.toastMessage.collect { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.stego_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Mode selector
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.mode == StegoMode.HIDE,
                    onClick = { viewModel.setMode(StegoMode.HIDE) },
                    label = { Text(stringResource(R.string.stego_mode_hide)) },
                    leadingIcon = {
                        Icon(Icons.Filled.VisibilityOff, null, modifier = Modifier.size(16.dp))
                    }
                )
                FilterChip(
                    selected = state.mode == StegoMode.EXTRACT,
                    onClick = { viewModel.setMode(StegoMode.EXTRACT) },
                    label = { Text(stringResource(R.string.stego_mode_extract)) },
                    leadingIcon = {
                        Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(16.dp))
                    }
                )
            }

            // Carrier file card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        stringResource(R.string.stego_carrier),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Spacer(Modifier.height(4.dp))
                    if (state.carrierName.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.InsertDriveFile, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                state.carrierName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        // Detection result
                        val detect = state.detectResult
                        if (detect != null) {
                            Spacer(Modifier.height(8.dp))
                            if (detect.hasHiddenData) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        null,
                                        tint = Color(0xFFFFC107),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(
                                            R.string.stego_hidden_detected,
                                            detect.payloadName ?: "?",
                                            detect.payloadSize.toFileSize()
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFFFFC107)
                                    )
                                }
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        null,
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.stego_no_hidden),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.stego_select_carrier),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Payload file card (hide mode only)
            if (state.mode == StegoMode.HIDE) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.stego_payload),
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(Modifier.height(4.dp))
                        if (state.payloadName.isNotEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.InsertDriveFile, null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    state.payloadName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        } else {
                            Text(
                                stringResource(R.string.stego_select_payload),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Progress
            if (state.isProcessing) {
                Column {
                    Text(
                        phaseLabel(state.progress.phase),
                        style = MaterialTheme.typography.bodySmall
                    )
                    LinearProgressIndicator(
                        progress = { state.progress.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Result message
            if (state.resultMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Text(
                        state.resultMessage!!,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Action button
            when (state.mode) {
                StegoMode.HIDE -> {
                    Button(
                        onClick = { viewModel.hidePayload() },
                        enabled = state.carrierPath.isNotEmpty() && state.payloadPath.isNotEmpty() && !state.isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.VisibilityOff, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stego_hide_action))
                    }
                }
                StegoMode.EXTRACT -> {
                    Button(
                        onClick = { viewModel.extractPayload() },
                        enabled = state.carrierPath.isNotEmpty()
                                && state.detectResult?.hasHiddenData == true
                                && !state.isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Visibility, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stego_extract_action))
                    }
                }
            }

            // Hint
            Text(
                stringResource(R.string.stego_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun phaseLabel(phase: StegoPhase): String {
    return when (phase) {
        StegoPhase.IDLE -> ""
        StegoPhase.COPYING_CARRIER -> stringResource(R.string.stego_phase_copying)
        StegoPhase.ENCRYPTING -> stringResource(R.string.stego_phase_encrypting)
        StegoPhase.APPENDING -> stringResource(R.string.stego_phase_appending)
        StegoPhase.DETECTING -> stringResource(R.string.stego_phase_detecting)
        StegoPhase.EXTRACTING -> stringResource(R.string.stego_phase_extracting)
        StegoPhase.DONE -> stringResource(R.string.done)
        StegoPhase.ERROR -> stringResource(R.string.stego_phase_error)
    }
}
