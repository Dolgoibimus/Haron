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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
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
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxHeight
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.NavbarAction
import com.vamp.haron.domain.model.NavbarButton
import com.vamp.haron.domain.model.NavbarConfig
import com.vamp.haron.domain.model.NavbarPage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavbarSettingsScreen(
    prefs: HaronPreferences,
    onBack: () -> Unit,
    onOpenIcons: () -> Unit = {}
) {
    var config by remember { mutableStateOf(prefs.getNavbarConfig()) }

    // Editing state
    var editingPageIndex by remember { mutableIntStateOf(-1) }
    var editingBtnIndex by remember { mutableIntStateOf(-1) }

    fun save(newConfig: NavbarConfig) {
        config = newConfig
        prefs.setNavbarConfig(newConfig)
    }

    // Button editor dialog — shows all actions with tap/long checkboxes
    if (editingPageIndex >= 0 && editingBtnIndex >= 0) {
        val currentBtn = config.pages[editingPageIndex].buttons[editingBtnIndex]
        ButtonEditorDialog(
            currentButton = currentBtn,
            onSave = { newButton ->
                val pages = config.pages.toMutableList()
                val buttons = pages[editingPageIndex].buttons.toMutableList()
                buttons[editingBtnIndex] = newButton
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
                    IconButton(onClick = onOpenIcons) {
                        Icon(Icons.Filled.Palette, contentDescription = stringResource(R.string.navbar_icons_title))
                    }
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
                .swipeBackFromLeft(onBack = onBack)
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
                    onButtonTap = { btnIndex ->
                        editingPageIndex = pageIndex
                        editingBtnIndex = btnIndex
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
    onButtonTap: (btnIndex: Int) -> Unit
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
                    valueRange = 5f..9f,
                    steps = 3,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Text("9", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(8.dp))

            // Buttons preview — tap to open editor
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
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onButtonTap(btnIndex) }
                    ) {
                        // Tap action
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = actionShortLabel(btn.tapAction),
                                fontSize = 22.sp,
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
                            fontSize = 20.sp,
                            color = if (btn.longAction == NavbarAction.NONE) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                                    else MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * Button editor dialog: list of all actions, each with two checkboxes (tap / long).
 * One action cannot be both tap and long simultaneously.
 * Hidden internal actions (FORCE_DELETE, CREATE_FILE) are excluded from the list.
 */
@Composable
private fun ButtonEditorDialog(
    currentButton: NavbarButton,
    onSave: (NavbarButton) -> Unit,
    onDismiss: () -> Unit
) {
    var tapAction by remember { mutableStateOf(currentButton.tapAction) }
    var longAction by remember { mutableStateOf(currentButton.longAction) }

    // Actions visible in picker (exclude internal-only actions)
    val visibleActions = remember {
        NavbarAction.entries.filter {
            it != NavbarAction.FORCE_DELETE && it != NavbarAction.CREATE_FILE &&
            it != NavbarAction.SWITCH_PANEL && it != NavbarAction.ENTER_FOLDER &&
            it != NavbarAction.CURSOR_LEFT && it != NavbarAction.CURSOR_RIGHT &&
            it != NavbarAction.TOGGLE_SHIFT
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(5f / 6f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Title
                Text(
                    stringResource(R.string.navbar_choose_action),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                // Header: Action | Tap | Long
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.navbar_tap_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp)
                    )
                    Text(
                        stringResource(R.string.navbar_long_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(48.dp)
                    )
                }

                // Action list
                val radialActions = listOf(
                    NavbarAction.COPY_MOVE,
                    NavbarAction.DELETE_MENU,
                    NavbarAction.CREATE_MENU
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(visibleActions.size) { index ->
                        val action = visibleActions[index]
                        val isTap = tapAction == action
                        val isLong = longAction == action
                        val isArrow = action in listOf(
                            NavbarAction.ARROW_UP, NavbarAction.ARROW_DOWN,
                            NavbarAction.ARROW_LEFT, NavbarAction.ARROW_RIGHT
                        )
                        val isRadial = action in radialActions

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(action.labelRes),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                                color = if (isTap || isLong) MaterialTheme.colorScheme.onSurface
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            // Tap checkbox
                            Checkbox(
                                checked = isTap,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (longAction == action) longAction = NavbarAction.NONE
                                        tapAction = action
                                    } else {
                                        tapAction = NavbarAction.NONE
                                    }
                                },
                                modifier = Modifier.size(36.dp),
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary
                                )
                            )
                            // Long checkbox — hidden for radial actions (long is built-in)
                            if (isRadial || isArrow) {
                                Spacer(Modifier.size(36.dp))
                            } else {
                                Checkbox(
                                    checked = isLong,
                                    onCheckedChange = { checked ->
                                        if (checked) {
                                            if (tapAction == action) tapAction = NavbarAction.NONE
                                            longAction = action
                                        } else {
                                            longAction = NavbarAction.NONE
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = MaterialTheme.colorScheme.error
                                    )
                                )
                            }
                        }
                    }
                }

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { onSave(NavbarButton(tapAction, longAction)) }) {
                        Text("OK")
                    }
                }
            }
        }
    }
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
    NavbarAction.COPY_MOVE -> "📋📦"
    NavbarAction.DELETE_MENU -> "🗑⚡"
    NavbarAction.CREATE_MENU -> "+📁"
    NavbarAction.FORCE_DELETE -> "💀"
    NavbarAction.CREATE_FILE -> "📄"
    NavbarAction.ARROW_UP -> "▲"
    NavbarAction.ARROW_DOWN -> "▼"
    NavbarAction.ARROW_LEFT -> "◀"
    NavbarAction.ARROW_RIGHT -> "▶"
    NavbarAction.SWITCH_PANEL -> "⇅"
    NavbarAction.ENTER_FOLDER -> "▶"
    NavbarAction.CURSOR_LEFT -> "◁"
    NavbarAction.CURSOR_RIGHT -> "▷"
    NavbarAction.TOGGLE_SHIFT -> "⇧"
}
