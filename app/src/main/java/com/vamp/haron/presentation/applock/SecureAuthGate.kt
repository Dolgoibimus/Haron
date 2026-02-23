package com.vamp.haron.presentation.applock

import androidx.compose.runtime.Composable
import com.vamp.haron.domain.model.AppLockMethod

/**
 * Reusable auth gate for Secure Folder shield toggle.
 * Shows PIN/biometric dialog, calls [onAuthenticated] on success.
 */
@Composable
fun SecureAuthGate(
    show: Boolean,
    lockMethod: AppLockMethod,
    hasBiometric: Boolean,
    pinLength: Int = 4,
    onPinVerify: (String) -> Boolean,
    onBiometricRequest: () -> Unit,
    onAuthenticated: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!show) return

    LockScreen(
        lockMethod = when (lockMethod) {
            AppLockMethod.NONE -> AppLockMethod.PIN_ONLY
            else -> lockMethod
        },
        onPinVerified = { pin ->
            val ok = onPinVerify(pin)
            if (ok) onAuthenticated()
            ok
        },
        onBiometricRequest = onBiometricRequest,
        hasBiometric = hasBiometric,
        pinLength = pinLength
    )
}
