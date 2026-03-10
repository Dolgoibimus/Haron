package com.vamp.haron.presentation.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.R
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.GestureType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GesturesVoiceScreen(
    onBack: () -> Unit,
    initialTab: Int = 0,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gestures_and_voice_title)) },
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
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.tab_gestures)) },
                    icon = { Icon(Icons.Filled.SwipeRight, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text(stringResource(R.string.tab_voice)) },
                    icon = { Icon(Icons.Filled.Mic, contentDescription = null) }
                )
            }

            when (selectedTab) {
                0 -> GesturesTab(state = state, viewModel = viewModel)
                1 -> VoiceTab()
            }
        }
    }
}

@Composable
private fun GesturesTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            stringResource(R.string.gestures_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        GestureType.entries.forEach { gestureType ->
            val currentAction = state.gestureMappings[gestureType] ?: gestureType.defaultAction
            GestureSettingRow(
                gestureLabel = stringResource(gestureType.labelRes),
                currentAction = currentAction,
                onActionSelected = { viewModel.setGestureAction(gestureType, it) }
            )
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { viewModel.resetGestures() }) {
            Text(stringResource(R.string.gestures_reset))
        }
    }
}

@Composable
private fun VoiceTab(
    viewModel: GesturesVoiceViewModel = hiltViewModel()
) {
    val wakeEnabled by viewModel.wakeWordEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Wake word toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.wake_word_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    stringResource(R.string.wake_word_description),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = wakeEnabled,
                onCheckedChange = { viewModel.setWakeWordEnabled(it) }
            )
        }
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.voice_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))

        Text(
            stringResource(R.string.voice_available_commands),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))

        val commands = listOf(
            R.string.voice_cmd_menu to R.string.voice_cmd_menu_desc,
            R.string.voice_cmd_shelf to R.string.voice_cmd_shelf_desc,
            R.string.voice_cmd_hidden to R.string.voice_cmd_hidden_desc,
            R.string.voice_cmd_create to R.string.voice_cmd_create_desc,
            R.string.voice_cmd_search to R.string.voice_cmd_search_desc,
            R.string.voice_cmd_terminal to R.string.voice_cmd_terminal_desc,
            R.string.voice_cmd_select_all to R.string.voice_cmd_select_all_desc,
            R.string.voice_cmd_refresh to R.string.voice_cmd_refresh_desc,
            R.string.voice_cmd_home to R.string.voice_cmd_home_desc,
            R.string.voice_cmd_sort to R.string.voice_cmd_sort_desc,
            R.string.voice_cmd_settings to R.string.voice_cmd_settings_desc,
            R.string.voice_cmd_transfer to R.string.voice_cmd_transfer_desc,
            R.string.voice_cmd_trash to R.string.voice_cmd_trash_desc,
            R.string.voice_cmd_storage to R.string.voice_cmd_storage_desc,
            R.string.voice_cmd_duplicates to R.string.voice_cmd_duplicates_desc,
            R.string.voice_cmd_apps to R.string.voice_cmd_apps_desc,
            R.string.voice_cmd_scanner to R.string.voice_cmd_scanner_desc,
            R.string.voice_cmd_logs to R.string.voice_cmd_logs_desc,
            R.string.voice_cmd_logs_pause to R.string.voice_cmd_logs_pause_desc,
            R.string.voice_cmd_logs_resume to R.string.voice_cmd_logs_resume_desc,
            R.string.voice_cmd_back to R.string.voice_cmd_back_desc,
            R.string.voice_cmd_forward to R.string.voice_cmd_forward_desc,
            R.string.voice_cmd_up to R.string.voice_cmd_up_desc,
            R.string.voice_cmd_delete to R.string.voice_cmd_delete_desc,
            R.string.voice_cmd_copy to R.string.voice_cmd_copy_desc,
            R.string.voice_cmd_move to R.string.voice_cmd_move_desc,
            R.string.voice_cmd_rename to R.string.voice_cmd_rename_desc,
            R.string.voice_cmd_archive to R.string.voice_cmd_archive_desc,
            R.string.voice_cmd_extract to R.string.voice_cmd_extract_desc,
            R.string.voice_cmd_properties to R.string.voice_cmd_properties_desc,
            R.string.voice_cmd_deselect to R.string.voice_cmd_deselect_desc,
            R.string.voice_cmd_go_to to R.string.voice_cmd_go_to_desc,
            R.string.voice_cmd_refresh_cache to R.string.voice_cmd_refresh_cache_desc,
            R.string.voice_cmd_secure to R.string.voice_cmd_secure_desc
        )

        commands.forEach { (cmdRes, descRes) ->
            VoiceCommandRow(
                command = stringResource(cmdRes),
                description = stringResource(descRes)
            )
            Spacer(Modifier.height(8.dp))
        }

        // Folder aliases section
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.voice_cmd_go_to_aliases_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.voice_cmd_go_to_aliases),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Panel targeting section
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.voice_panel_targeting_title),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.voice_panel_targeting_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.voice_usage_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VoiceCommandRow(
    command: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = command,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GestureSettingRow(
    gestureLabel: String,
    currentAction: GestureAction,
    onActionSelected: (GestureAction) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            gestureLabel,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f)
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            ) {
                Text(
                    stringResource(currentAction.labelRes),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                GestureAction.entries.forEach { action ->
                    DropdownMenuItem(
                        text = { Text(stringResource(action.labelRes), style = MaterialTheme.typography.bodySmall) },
                        onClick = {
                            onActionSelected(action)
                            expanded = false
                        },
                        contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                    )
                }
            }
        }
    }
}
