package com.vamp.haron.presentation.applock

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vamp.haron.R

@Composable
fun PinSetupDialog(
    isChange: Boolean = false,
    onConfirm: (currentPin: String?, newPin: String) -> Boolean,
    onDismiss: () -> Unit
) {
    var step by remember { mutableIntStateOf(if (isChange) 0 else 1) }
    // step 0: enter current pin (only for change)
    // step 1: enter new pin
    // step 2: confirm new pin

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val tooShortMsg = stringResource(R.string.pin_too_short)
    val mismatchMsg = stringResource(R.string.pin_mismatch)
    val wrongPinMsg = stringResource(R.string.pin_wrong)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_setup_title)) },
        text = {
            Column {
                when (step) {
                    0 -> {
                        Text(stringResource(R.string.pin_enter_current))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) currentPin = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                    }
                    1 -> {
                        Text(stringResource(R.string.pin_enter))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPin,
                            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                    }
                    2 -> {
                        Text(stringResource(R.string.pin_confirm))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmPin,
                            onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) confirmPin = it },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                    }
                }
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = null
                when (step) {
                    0 -> {
                        if (currentPin.length < 4) {
                            error = tooShortMsg
                        } else {
                            step = 1
                        }
                    }
                    1 -> {
                        if (newPin.length < 4) {
                            error = tooShortMsg
                        } else {
                            step = 2
                        }
                    }
                    2 -> {
                        if (confirmPin != newPin) {
                            error = mismatchMsg
                            confirmPin = ""
                        } else {
                            val success = onConfirm(
                                if (isChange) currentPin else null,
                                newPin
                            )
                            if (success) {
                                onDismiss()
                            } else {
                                error = wrongPinMsg
                                step = 0
                                currentPin = ""
                                newPin = ""
                                confirmPin = ""
                            }
                        }
                    }
                }
            }) {
                Text(
                    when (step) {
                        2 -> stringResource(android.R.string.ok)
                        else -> stringResource(R.string.next)
                    }
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
