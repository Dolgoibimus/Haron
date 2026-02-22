package com.vamp.haron.presentation.settings

import androidx.lifecycle.ViewModel
import com.vamp.haron.data.datastore.HaronPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val trashMaxSizeMb: Int = 500
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: HaronPreferences
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
            trashMaxSizeMb = preferences.trashMaxSizeMb
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

    fun setTrashMaxSizeMb(sizeMb: Int) {
        val clamped = sizeMb.coerceIn(0, 5000)
        preferences.trashMaxSizeMb = clamped
        _state.update { it.copy(trashMaxSizeMb = clamped) }
    }
}
