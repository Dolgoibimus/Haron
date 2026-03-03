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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import kotlinx.coroutines.delay

@Composable
fun PinSetupDialog(
    isChange: Boolean = false,
    onConfirm: (currentPin: String?, newPin: String, question: String?, answer: String?) -> Boolean,
    onDismiss: () -> Unit
) {
    // step 0: enter current pin (only for change)
    // step 1: enter new pin
    // step 2: confirm new pin
    // step 3: enter security question (only for new PIN, not change)
    // step 4: enter security answer (only for new PIN, not change)

    var step by remember { mutableIntStateOf(if (isChange) 0 else 1) }

    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var securityQuestion by remember { mutableStateOf("") }
    var securityAnswer by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    val tooShortMsg = stringResource(R.string.pin_too_short)
    val mismatchMsg = stringResource(R.string.pin_mismatch)
    val wrongPinMsg = stringResource(R.string.pin_wrong)
    val questionHintMsg = stringResource(R.string.security_question_hint)
    val answerHintMsg = stringResource(R.string.security_answer_hint)

    // Auto-focus text field on step change
    LaunchedEffect(step) {
        delay(100)
        try { focusRequester.requestFocus() } catch (_: Exception) {}
    }

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
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
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
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true
                        )
                    }
                    3 -> {
                        Text(stringResource(R.string.security_question_title))
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = securityQuestion,
                            onValueChange = { securityQuestion = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.security_question_hint)) },
                            singleLine = true
                        )
                    }
                    4 -> {
                        Text(securityQuestion)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = securityAnswer,
                            onValueChange = { securityAnswer = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text(stringResource(R.string.security_answer_hint)) },
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
                        } else if (isChange) {
                            // For change — skip security question steps
                            val success = onConfirm(currentPin, newPin, null, null)
                            if (success) {
                                onDismiss()
                            } else {
                                error = wrongPinMsg
                                step = 0
                                currentPin = ""
                                newPin = ""
                                confirmPin = ""
                            }
                        } else {
                            // For new PIN — proceed to security question
                            step = 3
                        }
                    }
                    3 -> {
                        if (securityQuestion.isBlank()) {
                            error = questionHintMsg
                        } else {
                            step = 4
                        }
                    }
                    4 -> {
                        if (securityAnswer.isBlank()) {
                            error = answerHintMsg
                        } else {
                            val success = onConfirm(null, newPin, securityQuestion.trim(), securityAnswer.trim())
                            if (success) {
                                onDismiss()
                            }
                        }
                    }
                }
            }) {
                Text(
                    when (step) {
                        4 -> stringResource(android.R.string.ok)
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
