package com.vamp.haron.presentation.cast.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.RemoteInputEvent

@Composable
fun VirtualKeyboardPanel(
    deviceName: String,
    onRemoteInput: (RemoteInputEvent) -> Unit,
    onShowTouchpad: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Mouse, null,
                        Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.remote_keyboard),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Row {
                    IconButton(onClick = onShowTouchpad, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.remote_touchpad),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Text input field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { newText ->
                        // Send each new character as it's typed
                        if (newText.length > text.length) {
                            val added = newText.substring(text.length)
                            onRemoteInput(RemoteInputEvent.TextInput(added))
                        } else if (newText.length < text.length) {
                            // Backspace
                            val deleted = text.length - newText.length
                            repeat(deleted) {
                                onRemoteInput(RemoteInputEvent.KeyPress(8)) // Backspace
                            }
                        }
                        text = newText
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            onRemoteInput(RemoteInputEvent.KeyPress(13)) // Enter
                            text = ""
                        }
                    ),
                    decorationBox = { innerTextField ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (text.isEmpty()) {
                                Text(
                                    stringResource(R.string.remote_keyboard_hint),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )

                IconButton(
                    onClick = {
                        onRemoteInput(RemoteInputEvent.KeyPress(13)) // Enter
                        text = ""
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.done),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Text(
                deviceName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
