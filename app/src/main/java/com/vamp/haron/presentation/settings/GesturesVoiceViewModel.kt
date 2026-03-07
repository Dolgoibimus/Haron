package com.vamp.haron.presentation.settings

import androidx.lifecycle.ViewModel
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.data.voice.VoiceCommandManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class GesturesVoiceViewModel @Inject constructor(
    private val voiceCommandManager: VoiceCommandManager,
    private val prefs: HaronPreferences
) : ViewModel() {

    val wakeWordEnabled = voiceCommandManager.wakeWordEnabled

    fun setWakeWordEnabled(enabled: Boolean) {
        prefs.wakeWordEnabled = enabled
        voiceCommandManager.setWakeWordEnabled(enabled)
    }
}
