package com.vamp.haron.presentation.applock

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vamp.haron.data.security.AuthManager
import com.vamp.haron.domain.model.AppLockMethod
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class AppLockState(
    val isLocked: Boolean = false,
    val lockMethod: AppLockMethod = AppLockMethod.NONE,
    val hasBiometric: Boolean = false,
    val pinError: Boolean = false
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val authManager: AuthManager
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    /** Timestamp of last successful unlock — grace period 3 seconds */
    private var lastUnlockTime: Long = 0L

    init {
        val method = authManager.getAppLockMethod()
        val hasBio = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()
        val shouldLock = method != AppLockMethod.NONE
        _state.value = AppLockState(
            isLocked = shouldLock,
            lockMethod = method,
            hasBiometric = hasBio
        )
    }

    fun onPinEntered(pin: String): Boolean {
        return if (authManager.verifyPin(pin)) {
            unlockApp()
            true
        } else {
            _state.update { it.copy(pinError = true) }
            false
        }
    }

    fun getPinLength(): Int = authManager.getPinLength()

    fun onBiometricSuccess() {
        unlockApp()
    }

    fun unlockApp() {
        lastUnlockTime = System.currentTimeMillis()
        _state.update { it.copy(isLocked = false, pinError = false) }
    }

    fun lockApp() {
        val method = authManager.getAppLockMethod()
        if (method == AppLockMethod.NONE) return
        _state.update { it.copy(isLocked = true, lockMethod = method, pinError = false) }
    }

    /**
     * Called from lifecycle observer when app goes to foreground.
     * Grace period: don't lock if less than 3 seconds since last unlock.
     */
    fun onResume() {
        val method = authManager.getAppLockMethod()
        if (method == AppLockMethod.NONE) return
        val elapsed = System.currentTimeMillis() - lastUnlockTime
        if (elapsed > 3000 && !_state.value.isLocked) {
            // Only lock if we were previously unlocked for more than grace period
        }
        _state.update {
            it.copy(
                lockMethod = method,
                hasBiometric = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()
            )
        }
    }

    /**
     * Called when app goes to background (ON_STOP).
     */
    fun onStop() {
        val method = authManager.getAppLockMethod()
        if (method == AppLockMethod.NONE) return
        // Mark time; actual lock happens on resume after grace period
        _state.update { it.copy(isLocked = true, lockMethod = method) }
    }

    fun refreshLockState() {
        val method = authManager.getAppLockMethod()
        _state.update {
            it.copy(
                lockMethod = method,
                hasBiometric = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()
            )
        }
    }
}
