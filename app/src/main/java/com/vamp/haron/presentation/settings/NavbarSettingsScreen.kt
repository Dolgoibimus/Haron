package com.vamp.haron.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vamp.haron.R
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.NavbarAction
import com.vamp.haron.domain.model.NavbarButton
import com.vamp.haron.domain.model.NavbarConfig
import com.vamp.haron.domain.model.NavbarPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavbarSettingsScreen(
    prefs: HaronPreferences,
    onBack: () -> Unit
) {
    var config by remember { mutableStateOf(prefs.getNavbarConfig()) }

    // Editing state
    var editingPageIndex by remember { mutableIntStateOf(-1) }
    var editingBtnIndex by remember { mutableIntStateOf(-1) }
    var editingIsLong by remember { mutableStateOf(false) }

    fun save(newConfig: NavbarConfig) {
        config = newConfig
        prefs.setNavbarConfig(newConfig)
    }

    // Action picker dialog
    if (editingPageIndex >= 0 && editingBtnIndex >= 0) {
        ActionPickerDialog(
            currentAction = if (editingIsLong) config.pages[editingPageIndex].buttons[editingBtnIndex].longAction
                            else config.pages[editingPageIndex].buttons[editingBtnIndex].tapAction,
            isLong = editingIsLong,
            onSelect = { action ->
                val pages = config.pages.toMutableList()
                val buttons = pages[editingPageIndex].buttons.toMutableList()
                val btn = buttons[editingBtnIndex]
                buttons[editingBtnIndex] = if (editingIsLong) btn.copy(longAction = action) else btn.copy(tapAction = action)
                pages[editingPageIndex] = pages[editingPageIndex].copy(buttons = buttons)
                save(config.copy(pages = pages))
                editingPageIndex = -1
            },
            onDismiss = { editingPageIndex = -1 }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navbar_settings_title), fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        save(config.copy(pages = config.pages + NavbarPage()))
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.navbar_add_page))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                expandedHeight = 36.dp
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)
        ) {
            itemsIndexed(config.pages) { pageIndex, page ->
                NavbarPageCard(
                    pageIndex = pageIndex,
                    page = page,
                    canDelete = config.pages.size > 1,
                    onDelete = {
                        val pages = config.pages.toMutableList()
                        pages.removeAt(pageIndex)
                        save(config.copy(pages = pages))
                    },
                    onButtonCountChange = { count ->
                        val pages = config.pages.toMutableList()
                        val oldButtons = pages[pageIndex].buttons
                        val newButtons = if (count > oldButtons.size) {
                            oldButtons + List(count - oldButtons.size) { NavbarButton() }
                        } else {
                            oldButtons.take(count)
                        }
                        pages[pageIndex] = pages[pageIndex].copy(buttons = newButtons)
                        save(config.copy(pages = pages))
                    },
                    onButtonTap = { btnIndex, isLong ->
                        editingPageIndex = pageIndex
                        editingBtnIndex = btnIndex
                        editingIsLong = isLong
                    }
                )
            }
        }
    }
}

@Composable
private fun NavbarPageCard(
    pageIndex: Int,
    page: NavbarPage,
    canDelete: Boolean,
    onDelete: () -> Unit,
    onButtonCountChange: (Int) -> Unit,
    onButtonTap: (btnIndex: Int, isLong: Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.navbar_page_title, pageIndex + 1),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    stringResource(R.string.navbar_buttons_count, page.buttons.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (canDelete) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // Button count slider (5-7)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("5", style = MaterialTheme.typography.bodySmall)
                Slider(
                    value = page.buttons.size.toFloat(),
                    onValueChange = { onButtonCountChange(it.toInt()) },
                    valueRange = 5f..7f,
                    steps = 1,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("7", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            // Buttons preview — tap to edit tap action, long label to edit long action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                page.buttons.forEachIndexed { btnIndex, btn ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        // Tap action
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                .clickable { onButtonTap(btnIndex, false) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = actionShortLabel(btn.tapAction),
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                color = if (btn.tapAction == NavbarAction.NONE) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                        else MaterialTheme.colorScheme.onSurface,
                                maxLines = 2
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // Long action label
                        Text(
                            text = if (btn.longAction == NavbarAction.NONE) "—" else actionShortLabel(btn.longAction),
                            fontSize = 8.sp,
                            color = if (btn.longAction == NavbarAction.NONE) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.clickable { onButtonTap(btnIndex, true) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionPickerDialog(
    currentAction: NavbarAction,
    isLong: Boolean,
    onSelect: (NavbarAction) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (isLong) stringResource(R.string.navbar_long_action)
                else stringResource(R.string.navbar_tap_action)
            )
        },
        text = {
            LazyColumn {
                val actions = NavbarAction.entries
                items(actions.size) { index ->
                    val action = actions[index]
                    val isSelected = action == currentAction
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(action) }
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(action.labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                    else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun actionShortLabel(action: NavbarAction): String = when (action) {
    NavbarAction.NONE -> "·"
    NavbarAction.BACK -> "←"
    NavbarAction.FORWARD -> "→"
    NavbarAction.UP -> "↑"
    NavbarAction.HOME -> "⌂"
    NavbarAction.EXIT -> "✕"
    NavbarAction.REFRESH -> "↻"
    NavbarAction.SEARCH -> "🔍"
    NavbarAction.SETTINGS -> "⚙"
    NavbarAction.TERMINAL -> ">_"
    NavbarAction.LIBRARY -> "📚"
    NavbarAction.TRANSFER -> "⇄"
    NavbarAction.TRASH -> "🗑"
    NavbarAction.STORAGE -> "📊"
    NavbarAction.APPS -> "📱"
    NavbarAction.DUPLICATES -> "🔄"
    NavbarAction.SCANNER -> "📷"
    NavbarAction.SELECT_ALL -> "☑"
    NavbarAction.TOGGLE_HIDDEN -> "👁"
    NavbarAction.CREATE_NEW -> "+"
    NavbarAction.COPY -> "📋"
    NavbarAction.MOVE -> "📦"
    NavbarAction.DELETE -> "🗑"
    NavbarAction.RENAME -> "✏"
    NavbarAction.APP_ICON -> "◉"
}
