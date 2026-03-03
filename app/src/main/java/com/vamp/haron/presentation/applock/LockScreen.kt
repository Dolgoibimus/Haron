package com.vamp.haron.presentation.applock

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.AppLockMethod
import kotlin.math.roundToInt

@Composable
fun LockScreen(
    lockMethod: AppLockMethod,
    onPinVerified: (String) -> Boolean,
    onBiometricRequest: () -> Unit,
    hasBiometric: Boolean,
    pinLength: Int = 4,
    onDismiss: (() -> Unit)? = null,
    securityQuestion: String? = null,
    onResetPin: ((answer: String) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    // Block back button (or allow dismiss if callback provided)
    BackHandler {
        onDismiss?.invoke()
    }

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }
    var shakeKey by remember { mutableIntStateOf(0) }
    val haptic = LocalHapticFeedback.current

    // Forgot PIN state
    var showForgotPin by remember { mutableStateOf(false) }
    var resetAnswer by remember { mutableStateOf("") }
    var resetError by remember { mutableStateOf(false) }

    // Shake animation triggered by shakeKey
    LaunchedEffect(shakeKey) {
        if (shakeKey > 0) {
            repeat(3) {
                shakeOffset.animateTo(20f, animationSpec = tween(50))
                shakeOffset.animateTo(-20f, animationSpec = tween(50))
            }
            shakeOffset.animateTo(0f, animationSpec = tween(50))
            pin = ""
            error = false
        }
    }

    // Auto-launch biometric for biometric modes
    LaunchedEffect(lockMethod) {
        if ((lockMethod == AppLockMethod.BIOMETRIC_ONLY || lockMethod == AppLockMethod.BIOMETRIC_WITH_PIN) && hasBiometric) {
            onBiometricRequest()
        }
    }

    // Auto-verify when PIN reaches expected length
    LaunchedEffect(pin) {
        if (pin.length == pinLength) {
            val ok = onPinVerified(pin)
            if (!ok) {
                error = true
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                shakeKey++
            }
        }
    }

    val showPinPad = lockMethod != AppLockMethod.BIOMETRIC_ONLY
    val showBiometricButton = hasBiometric &&
            (lockMethod == AppLockMethod.BIOMETRIC_ONLY || lockMethod == AppLockMethod.BIOMETRIC_WITH_PIN)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(32.dp)
        ) {
            Text(
                text = stringResource(R.string.lock_screen_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(32.dp))

            if (showPinPad) {
                // PIN dots — show exactly pinLength dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                ) {
                    repeat(pinLength) { i ->
                        val filled = i < pin.length
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 6.dp)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    if (filled) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.pin_wrong),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(Modifier.height(32.dp))

                // PIN pad — 3 rows of 3 + bottom row
                val buttons = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                )
                buttons.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        row.forEach { digit ->
                            PinButton(
                                text = digit,
                                onClick = {
                                    if (pin.length < pinLength) {
                                        pin += digit
                                        error = false
                                    }
                                }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // Bottom row: biometric / 0 / backspace
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showBiometricButton) {
                        IconButton(
                            onClick = onBiometricRequest,
                            modifier = Modifier.size(64.dp)
                        ) {
                            Icon(
                                Icons.Filled.Fingerprint,
                                contentDescription = stringResource(R.string.app_lock_method_biometric),
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else {
                        Spacer(Modifier.size(64.dp))
                    }

                    PinButton(text = "0", onClick = {
                        if (pin.length < pinLength) {
                            pin += "0"
                            error = false
                        }
                    })

                    IconButton(
                        onClick = {
                            if (pin.isNotEmpty()) pin = pin.dropLast(1)
                        },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            Icons.Filled.Backspace,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // "Forgot PIN?" button
                if (securityQuestion != null && onResetPin != null) {
                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { showForgotPin = !showForgotPin }) {
                        Text(stringResource(R.string.forgot_pin))
                    }

                    AnimatedVisibility(visible = showForgotPin) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = securityQuestion,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = resetAnswer,
                                onValueChange = {
                                    resetAnswer = it
                                    resetError = false
                                },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.security_answer_hint)) },
                                singleLine = true,
                                isError = resetError
                            )
                            if (resetError) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.security_answer_wrong),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            TextButton(
                                onClick = {
                                    if (resetAnswer.isNotBlank()) {
                                        val ok = onResetPin(resetAnswer)
                                        if (!ok) {
                                            resetError = true
                                        }
                                    }
                                }
                            ) {
                                Text(stringResource(R.string.check_answer))
                            }
                        }
                    }
                }
            }

            if (!showPinPad && showBiometricButton) {
                // Biometric-only mode — just the fingerprint button
                IconButton(
                    onClick = onBiometricRequest,
                    modifier = Modifier.size(80.dp)
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = stringResource(R.string.app_lock_method_biometric),
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun PinButton(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
