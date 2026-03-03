package com.vamp.haron.presentation.applock

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vamp.haron.data.datastore.HaronPreferences
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
    private val authManager: AuthManager,
    private val preferences: HaronPreferences
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    /** Timestamp of last successful unlock — grace period 3 seconds */
    private var lastUnlockTime: Long = 0L

    /** Timestamp when app went to background (ON_STOP) */
    private var lastStopTime: Long = 0L

    init {
        val method = authManager.getAppLockMethod()
        val hasBio = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()
        val shouldLock = method != AppLockMethod.NONE && preferences.requirePinOnLaunch
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
     * Locks if timeout exceeded and requirePinOnLaunch is enabled.
     */
    fun onResume() {
        val method = authManager.getAppLockMethod()
        val hasBio = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled()
        if (method == AppLockMethod.NONE || !preferences.requirePinOnLaunch) {
            _state.update { it.copy(lockMethod = method, hasBiometric = hasBio) }
            return
        }
        if (lastStopTime > 0 && !_state.value.isLocked) {
            val elapsed = System.currentTimeMillis() - lastStopTime
            val timeoutMs = preferences.lockTimeoutMinutes * 60_000L
            if (elapsed > timeoutMs) {
                _state.update { it.copy(isLocked = true, lockMethod = method, hasBiometric = hasBio, pinError = false) }
                return
            }
        }
        _state.update { it.copy(lockMethod = method, hasBiometric = hasBio) }
    }

    /**
     * Called when app goes to background (ON_STOP).
     * Only records timestamp — does NOT lock immediately.
     */
    fun onStop() {
        val method = authManager.getAppLockMethod()
        if (method == AppLockMethod.NONE) return
        lastStopTime = System.currentTimeMillis()
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

    // --- Security question ---

    fun getSecurityQuestion(): String? = authManager.getSecurityQuestion()

    fun resetPinViaAnswer(answer: String): Boolean {
        val ok = authManager.resetPinViaSecurityAnswer(answer)
        if (ok) {
            unlockApp()
        }
        return ok
    }
}
