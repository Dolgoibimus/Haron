package com.vamp.haron.presentation.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.security.AuthManager
import com.vamp.haron.data.shizuku.ShizukuManager
import com.vamp.haron.data.shizuku.ShizukuState
import com.vamp.haron.domain.model.AppLockMethod
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.GestureType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsUiState(
    val nightModeEnabled: Boolean = false,
    val nightModeStartHour: Int = 22,
    val nightModeStartMinute: Int = 0,
    val nightModeEndHour: Int = 7,
    val nightModeEndMinute: Int = 0,
    val fontScale: Float = 1.0f,
    val iconScale: Float = 1.0f,
    val hapticEnabled: Boolean = true,
    val marqueeEnabled: Boolean = true,
    val trashMaxSizeMb: Int = 500,
    // Cast
    val transcodeCacheTtlHours: Int = 24,
    // Security
    val appLockMethod: AppLockMethod = AppLockMethod.NONE,
    val isPinSet: Boolean = false,
    val hasBiometric: Boolean = false,
    val showPinSetupDialog: Boolean = false,
    val isPinChange: Boolean = false,
    val requirePinOnLaunch: Boolean = true,
    val hasSecurityQuestion: Boolean = false,
    val showSecurityQuestionDialog: Boolean = false,
    // Gestures
    val gestureMappings: Map<GestureType, GestureAction> = GestureType.entries.associateWith { it.defaultAction }
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val preferences: HaronPreferences,
    private val authManager: AuthManager,
    val shizukuManager: ShizukuManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(
            nightModeEnabled = preferences.nightModeEnabled,
            nightModeStartHour = preferences.nightModeStartHour,
            nightModeStartMinute = preferences.nightModeStartMinute,
            nightModeEndHour = preferences.nightModeEndHour,
            nightModeEndMinute = preferences.nightModeEndMinute,
            fontScale = preferences.fontScale,
            iconScale = preferences.iconScale,
            hapticEnabled = preferences.hapticEnabled,
            marqueeEnabled = preferences.marqueeEnabled,
            trashMaxSizeMb = preferences.trashMaxSizeMb,
            transcodeCacheTtlHours = preferences.transcodeCacheTtlHours,
            appLockMethod = authManager.getAppLockMethod(),
            isPinSet = authManager.isPinSet(),
            hasBiometric = authManager.hasBiometricHardware() && authManager.isBiometricEnrolled(),
            requirePinOnLaunch = preferences.requirePinOnLaunch,
            hasSecurityQuestion = authManager.hasSecurityQuestion(),
            gestureMappings = preferences.getGestureMappings()
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    fun setNightModeEnabled(enabled: Boolean) {
        preferences.nightModeEnabled = enabled
        _state.update { it.copy(nightModeEnabled = enabled) }
    }

    fun setNightModeStart(hour: Int, minute: Int) {
        preferences.nightModeStartHour = hour
        preferences.nightModeStartMinute = minute
        _state.update { it.copy(nightModeStartHour = hour, nightModeStartMinute = minute) }
    }

    fun setNightModeEnd(hour: Int, minute: Int) {
        preferences.nightModeEndHour = hour
        preferences.nightModeEndMinute = minute
        _state.update { it.copy(nightModeEndHour = hour, nightModeEndMinute = minute) }
    }

    fun setFontScale(scale: Float) {
        val clamped = scale.coerceIn(0.6f, 1.4f)
        preferences.fontScale = clamped
        _state.update { it.copy(fontScale = clamped) }
    }

    fun setIconScale(scale: Float) {
        val clamped = scale.coerceIn(0.6f, 1.4f)
        preferences.iconScale = clamped
        _state.update { it.copy(iconScale = clamped) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        preferences.hapticEnabled = enabled
        _state.update { it.copy(hapticEnabled = enabled) }
    }

    fun setMarqueeEnabled(enabled: Boolean) {
        preferences.marqueeEnabled = enabled
        _state.update { it.copy(marqueeEnabled = enabled) }
    }

    fun setTrashMaxSizeMb(sizeMb: Int) {
        val clamped = sizeMb.coerceIn(0, 5000)
        preferences.trashMaxSizeMb = clamped
        _state.update { it.copy(trashMaxSizeMb = clamped) }
    }

    fun setTranscodeCacheTtlHours(hours: Int) {
        preferences.transcodeCacheTtlHours = hours
        _state.update { it.copy(transcodeCacheTtlHours = hours) }
    }

    // --- Security ---

    fun setAppLockMethod(method: AppLockMethod) {
        // If PIN-required method and no PIN set, prompt to set it
        if (method != AppLockMethod.NONE && method != AppLockMethod.BIOMETRIC_ONLY && !authManager.isPinSet()) {
            _state.update { it.copy(showPinSetupDialog = true, isPinChange = false) }
            return
        }
        authManager.setAppLockMethod(method)
        _state.update { it.copy(appLockMethod = method) }
    }

    fun showPinSetup(isChange: Boolean) {
        _state.update { it.copy(showPinSetupDialog = true, isPinChange = isChange) }
    }

    fun dismissPinSetup() {
        _state.update { it.copy(showPinSetupDialog = false) }
    }

    /**
     * @return true if PIN was successfully set/changed
     */
    fun onPinSetupConfirm(currentPin: String?, newPin: String, question: String?, answer: String?): Boolean {
        if (currentPin != null && !authManager.verifyPin(currentPin)) {
            return false
        }
        authManager.setPin(newPin)
        // Save security question if provided
        if (question != null && answer != null) {
            authManager.setSecurityQuestion(question, answer)
        }
        // If lock method was NONE and user just set PIN, auto-enable PIN_ONLY
        val method = authManager.getAppLockMethod()
        if (method == AppLockMethod.NONE) {
            authManager.setAppLockMethod(AppLockMethod.PIN_ONLY)
        }
        _state.update {
            it.copy(
                isPinSet = true,
                showPinSetupDialog = false,
                appLockMethod = authManager.getAppLockMethod(),
                hasSecurityQuestion = authManager.hasSecurityQuestion()
            )
        }
        return true
    }

    fun showSecurityQuestionDialog() {
        _state.update { it.copy(showSecurityQuestionDialog = true) }
    }

    fun dismissSecurityQuestionDialog() {
        _state.update { it.copy(showSecurityQuestionDialog = false) }
    }

    fun saveSecurityQuestion(question: String, answer: String) {
        authManager.setSecurityQuestion(question, answer)
        _state.update { it.copy(showSecurityQuestionDialog = false, hasSecurityQuestion = true) }
    }

    fun setRequirePinOnLaunch(enabled: Boolean) {
        preferences.requirePinOnLaunch = enabled
        _state.update { it.copy(requirePinOnLaunch = enabled) }
    }

    // --- Gestures ---

    fun setGestureAction(type: GestureType, action: GestureAction) {
        preferences.setGestureAction(type, action)
        _state.update {
            it.copy(gestureMappings = it.gestureMappings.toMutableMap().apply { put(type, action) })
        }
    }

    fun resetGestures() {
        GestureType.entries.forEach { type ->
            preferences.setGestureAction(type, type.defaultAction)
        }
        _state.update {
            it.copy(gestureMappings = GestureType.entries.associateWith { t -> t.defaultAction })
        }
    }

    // --- Shizuku ---

    fun refreshShizukuState() {
        shizukuManager.refreshState()
    }

    fun requestShizukuPermission() {
        shizukuManager.requestPermission()
    }

    fun openShizukuApp() {
        com.vamp.haron.presentation.explorer.components.openShizukuApp(appContext)
    }

    fun openShizukuPlayStore() {
        com.vamp.haron.presentation.explorer.components.openShizukuPlayStore(appContext)
    }
}
