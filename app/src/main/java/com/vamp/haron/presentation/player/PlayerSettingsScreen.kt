package com.vamp.haron.presentation.player

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.data.datastore.HaronPreferences

@Composable
fun PlayerSettingsScreen(
    prefs: HaronPreferences,
    initialTab: Int = 0,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(horizontal = 2.dp)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back), tint = Color.White)
            }
            Text(
                text = stringResource(R.string.player_settings),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }

        // Tabs: Video / Audio
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = Color.White,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text(stringResource(R.string.player_tab_video)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text(stringResource(R.string.player_tab_audio)) }
            )
        }

        // Content — same structure, different isVideo parameter
        val isVideo = selectedTab == 0
        DndSettingsContent(prefs = prefs, isVideo = isVideo)
    }
}

@Composable
private fun DndSettingsContent(
    prefs: HaronPreferences,
    isVideo: Boolean
) {
    val context = LocalContext.current
    val notificationManager = remember { context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    var dndEnabled by remember(isVideo) { mutableStateOf(prefs.playerDndEnabled(isVideo)) }
    var allowCalls by remember(isVideo) { mutableStateOf(prefs.playerDndAllowCalls(isVideo)) }
    var allowMessages by remember(isVideo) { mutableStateOf(prefs.playerDndAllowMessages(isVideo)) }
    var allowAlarms by remember(isVideo) { mutableStateOf(prefs.playerDndAllowAlarms(isVideo)) }
    var allowRepeat by remember(isVideo) { mutableStateOf(prefs.playerDndAllowRepeatCallers(isVideo)) }
    var callSenders by remember(isVideo) { mutableIntStateOf(prefs.playerDndCallSenders(isVideo)) }
    var suppressHeadsUp by remember(isVideo) { mutableStateOf(prefs.playerDndSuppressHeadsUp(isVideo)) }
    var suppressStatusBar by remember(isVideo) { mutableStateOf(prefs.playerDndSuppressStatusBar(isVideo)) }
    var suppressScreenOn by remember(isVideo) { mutableStateOf(prefs.playerDndSuppressScreenOn(isVideo)) }
    var silentMode by remember(isVideo) { mutableStateOf(prefs.playerDndSilentMode(isVideo)) }
    var hasPermission by remember { mutableStateOf(notificationManager.isNotificationPolicyAccessGranted) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        // === 1. Master toggle ===
        SectionHeader(stringResource(R.string.player_dnd_title))

        if (!hasPermission) {
            Text(
                stringResource(R.string.player_dnd_no_permission),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
            TextButton(onClick = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
            }) {
                Text(stringResource(R.string.player_dnd_grant_permission))
            }
        }

        SettingsToggle(
            label = stringResource(R.string.player_dnd_subtitle),
            checked = dndEnabled,
            enabled = hasPermission,
            onCheckedChange = {
                dndEnabled = it
                prefs.setPlayerDndEnabled(isVideo, it)
            }
        )

        if (dndEnabled && hasPermission) {
            DividerLine()

            // === 2. Allow through ===
            SectionHeader(stringResource(R.string.player_dnd_allow_through))

            SettingsCheckbox(stringResource(R.string.player_dnd_calls), allowCalls) {
                allowCalls = it; prefs.setPlayerDndAllowCalls(isVideo, it)
            }
            SettingsCheckbox(stringResource(R.string.player_dnd_messages), allowMessages) {
                allowMessages = it; prefs.setPlayerDndAllowMessages(isVideo, it)
            }
            SettingsCheckbox(stringResource(R.string.player_dnd_alarms), allowAlarms) {
                allowAlarms = it; prefs.setPlayerDndAllowAlarms(isVideo, it)
            }
            SettingsCheckbox(stringResource(R.string.player_dnd_repeat_callers), allowRepeat) {
                allowRepeat = it; prefs.setPlayerDndAllowRepeatCallers(isVideo, it)
            }

            // === 3. Who can call ===
            if (allowCalls || allowMessages) {
                DividerLine()
                SectionHeader(stringResource(R.string.player_dnd_who_can_call))

                val senderOptions = listOf(
                    0 to stringResource(R.string.player_dnd_senders_none),
                    1 to stringResource(R.string.player_dnd_senders_starred),
                    2 to stringResource(R.string.player_dnd_senders_contacts),
                    3 to stringResource(R.string.player_dnd_senders_any)
                )
                senderOptions.forEach { (value, label) ->
                    SettingsRadio(label, selected = callSenders == value) {
                        callSenders = value; prefs.setPlayerDndCallSenders(isVideo, value)
                    }
                }
            }

            // === 4. Visual effects ===
            DividerLine()
            SectionHeader(stringResource(R.string.player_dnd_visual))

            SettingsCheckbox(stringResource(R.string.player_dnd_heads_up), suppressHeadsUp) {
                suppressHeadsUp = it; prefs.setPlayerDndSuppressHeadsUp(isVideo, it)
            }
            SettingsCheckbox(stringResource(R.string.player_dnd_status_bar), suppressStatusBar) {
                suppressStatusBar = it; prefs.setPlayerDndSuppressStatusBar(isVideo, it)
            }
            SettingsCheckbox(stringResource(R.string.player_dnd_screen_on), suppressScreenOn) {
                suppressScreenOn = it; prefs.setPlayerDndSuppressScreenOn(isVideo, it)
            }

            // === 5. Vibration ===
            DividerLine()
            SettingsToggle(
                label = stringResource(R.string.player_dnd_vibration),
                checked = silentMode,
                onCheckedChange = {
                    silentMode = it; prefs.setPlayerDndSilentMode(isVideo, it)
                }
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
    )
}

@Composable
private fun DividerLine() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.1f),
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsToggle(
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
            )
        )
    }
}

@Composable
private fun SettingsCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                uncheckedColor = Color.White.copy(alpha = 0.5f),
                checkmarkColor = Color.White
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}

@Composable
private fun SettingsRadio(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = MaterialTheme.colorScheme.primary,
                unselectedColor = Color.White.copy(alpha = 0.5f)
            ),
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White)
    }
}
