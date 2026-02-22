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
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
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
                title = "Ночной режим"
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Включить по расписанию", modifier = Modifier.weight(1f))
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
                    Text("Начало:", modifier = Modifier.weight(1f))
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
                    Text("Конец:", modifier = Modifier.weight(1f))
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
                title = "Шрифт и иконки"
            )
            Spacer(Modifier.height(8.dp))

            Text("Размер шрифта: ${String.format("%.0f%%", state.fontScale * 100)}")
            Slider(
                value = state.fontScale,
                onValueChange = { viewModel.setFontScale(it) },
                valueRange = 0.6f..1.4f,
                steps = 7,
                modifier = Modifier.fillMaxWidth()
            )
            // Preview text
            Text(
                "Пример текста — Preview",
                fontSize = (16 * state.fontScale).sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Text("Размер иконок: ${String.format("%.0f%%", state.iconScale * 100)}")
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
                    "Превью иконки",
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
                title = "Тактильный отклик"
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Вибрация при операциях", modifier = Modifier.weight(1f))
                Switch(
                    checked = state.hapticEnabled,
                    onCheckedChange = { viewModel.setHapticEnabled(it) }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // --- Trash ---
            SectionHeader(
                icon = Icons.Filled.DeleteOutline,
                title = "Корзина"
            )
            Spacer(Modifier.height(8.dp))

            val trashLabel = if (state.trashMaxSizeMb == 0) "Без лимита"
                else "${state.trashMaxSizeMb} МБ"
            Text("Максимальный размер: $trashLabel")
            Slider(
                value = state.trashMaxSizeMb.toFloat(),
                onValueChange = { viewModel.setTrashMaxSizeMb(it.toInt()) },
                valueRange = 0f..5000f,
                steps = 49,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                "0 = без лимита. При превышении старые файлы удаляются автоматически.",
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
