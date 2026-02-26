package com.vamp.haron.presentation.voice

import androidx.lifecycle.ViewModel
import com.vamp.haron.data.voice.VoiceCommandManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class VoiceFabViewModel @Inject constructor(
    private val voiceCommandManager: VoiceCommandManager
) : ViewModel() {

    val voiceState = voiceCommandManager.state
    val isAvailable: Boolean get() = voiceCommandManager.isAvailable

    fun startListening() {
        voiceCommandManager.startListening()
    }

    fun stopListening() {
        voiceCommandManager.stop()
    }
}
