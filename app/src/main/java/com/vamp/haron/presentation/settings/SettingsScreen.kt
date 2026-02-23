package com.vamp.haron.presentation.settings

import android.app.TimePickerDialog
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.domain.model.AppLockMethod
import com.vamp.haron.presentation.applock.PinSetupDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // PIN setup dialog
    if (state.showPinSetupDialog) {
        PinSetupDialog(
            isChange = state.isPinChange,
            onConfirm = { currentPin, newPin ->
                viewModel.onPinSetupConfirm(currentPin, newPin)
            },
            onDismiss = { viewModel.dismissPinSetup() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
                .padding(16.dp)
        ) {
            // --- Night Mode ---
            SectionHeader(
                icon = Icons.Filled.DarkMode,
                title = stringResource(R.string.night_mode_section)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.night_mode_schedule), modifier = Modifier.weight(1f))
                Switch(
                    checked = state.nightModeEnabled,
                    onCheckedChange = { viewModel.setNightModeEnabled(it) }
                )
            }

            if (state.nightModeEnabled) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.night_mode_start), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> viewModel.setNightModeStart(h, m) },
                            state.nightModeStartHour,
                            state.nightModeStartMinute,
                            true
                        ).show()
                    }) {
                        Text(formatTime(state.nightModeStartHour, state.nightModeStartMinute))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.night_mode_end), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        TimePickerDialog(
                            context,
                            { _, h, m -> viewModel.setNightModeEnd(h, m) },
                            state.nightModeEndHour,
                            state.nightModeEndMinute,
                            true
                        ).show()
                    }) {
                        Text(formatTime(state.nightModeEndHour, state.nightModeEndMinute))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Font & Icon Scale ---
            SectionHeader(
                icon = Icons.Filled.FormatSize,
                title = stringResource(R.string.font_and_icons)
            )
            Spacer(Modifier.height(8.dp))

            Text(stringResource(R.string.font_scale_label, (state.fontScale * 100).toInt()))
            Slider(
                value = state.fontScale,
                onValueChange = { viewModel.setFontScale(it) },
                valueRange = 0.6f..1.4f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )
            // Preview text
            Text(
                stringResource(R.string.preview_text),
                fontSize = (16 * state.fontScale).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Text(stringResource(R.string.icon_scale_label, (state.iconScale * 100).toInt()))
            Slider(
                value = state.iconScale,
                onValueChange = { viewModel.setIconScale(it) },
                valueRange = 0.6f..1.4f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )
            // Preview icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = null,
                    modifier = Modifier.size((24 * state.iconScale).dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.icon_preview),
                    fontSize = (14 * state.fontScale).sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Haptic ---
            SectionHeader(
                icon = Icons.Filled.Vibration,
                title = stringResource(R.string.haptic_section)
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.haptic_vibration), modifier = Modifier.weight(1f))
                Switch(
                    checked = state.hapticEnabled,
                    onCheckedChange = { viewModel.setHapticEnabled(it) }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Security ---
            SectionHeader(
                icon = Icons.Filled.Lock,
                title = stringResource(R.string.security_section)
            )
            Spacer(Modifier.height(8.dp))

            Text(
                stringResource(R.string.app_lock_title),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(4.dp))

            // Lock method radio buttons
            val lockMethods = listOf(
                AppLockMethod.NONE to stringResource(R.string.app_lock_method_none),
                AppLockMethod.PIN_ONLY to stringResource(R.string.app_lock_method_pin),
                AppLockMethod.BIOMETRIC_ONLY to stringResource(R.string.app_lock_method_biometric),
                AppLockMethod.BIOMETRIC_WITH_PIN to stringResource(R.string.app_lock_method_biometric_pin)
            )

            lockMethods.forEach { (method, label) ->
                val enabled = when (method) {
                    AppLockMethod.BIOMETRIC_ONLY, AppLockMethod.BIOMETRIC_WITH_PIN -> state.hasBiometric
                    else -> true
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = state.appLockMethod == method,
                        onClick = { if (enabled) viewModel.setAppLockMethod(method) },
                        enabled = enabled
                    )
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (!state.hasBiometric) {
                Text(
                    stringResource(R.string.biometric_not_available),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(8.dp))

            // PIN buttons
            if (state.isPinSet) {
                TextButton(onClick = { viewModel.showPinSetup(isChange = true) }) {
                    Text(stringResource(R.string.change_pin))
                }
            } else {
                TextButton(onClick = { viewModel.showPinSetup(isChange = false) }) {
                    Text(stringResource(R.string.set_pin))
                }
            }

            Text(
                stringResource(R.string.app_lock_protects_secure),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Trash ---
            SectionHeader(
                icon = Icons.Filled.DeleteOutline,
                title = stringResource(R.string.trash_section)
            )
            Spacer(Modifier.height(8.dp))

            val trashLabel = if (state.trashMaxSizeMb == 0) stringResource(R.string.trash_no_limit)
                else "${state.trashMaxSizeMb} ${stringResource(R.string.size_mb)}"
            Text(stringResource(R.string.trash_max_size_label, trashLabel))
            Slider(
                value = state.trashMaxSizeMb.toFloat(),
                onValueChange = { viewModel.setTrashMaxSizeMb(it.toInt()) },
                valueRange = 0f..5000f,
                steps = 49,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                stringResource(R.string.trash_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleSmall)
    }
}

private fun formatTime(hour: Int, minute: Int): String {
    return String.format("%02d:%02d", hour, minute)
}
