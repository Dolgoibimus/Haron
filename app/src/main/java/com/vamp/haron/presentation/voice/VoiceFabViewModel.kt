package com.vamp.haron.presentation.voice

import androidx.lifecycle.ViewModel
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.voice.VoiceCommandManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VoiceFabViewModel @Inject constructor(
    private val voiceCommandManager: VoiceCommandManager,
    private val prefs: HaronPreferences
) : ViewModel() {

    val voiceState = voiceCommandManager.state
    val isAvailable: Boolean get() = voiceCommandManager.isAvailable

    val savedOffsetX: Float get() = prefs.micFabOffsetX
    val savedOffsetY: Float get() = prefs.micFabOffsetY

    val shouldShowHint: Boolean get() = !prefs.micHintShown

    fun markHintShown() {
        prefs.micHintShown = true
    }

    fun saveOffset(x: Float, y: Float) {
        prefs.micFabOffsetX = x
        prefs.micFabOffsetY = y
    }

    fun startListening() {
        voiceCommandManager.startListening()
    }

    fun stopListening() {
        voiceCommandManager.stop()
    }
}
