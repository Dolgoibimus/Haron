package com.vamp.haron.presentation.settings

import androidx.lifecycle.ViewModel
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
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
        EcosystemLogger.d(HaronConstants.TAG, "GesturesVoiceVM: setWakeWordEnabled=$enabled")
        prefs.wakeWordEnabled = enabled
        voiceCommandManager.setWakeWordEnabled(enabled)
    }
}
