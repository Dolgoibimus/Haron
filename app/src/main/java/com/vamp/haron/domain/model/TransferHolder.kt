package com.vamp.haron.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object TransferHolder {
    var selectedFiles: List<File> = emptyList()
    val pendingNavigationPath = MutableStateFlow<String?>(null)
    /** Voice/gesture action to execute after returning to Explorer from another screen */
    val pendingVoiceAction = MutableStateFlow<GestureAction?>(null)
    /** Request to open voice commands list from mic FAB long press */
    val pendingOpenVoiceList = MutableStateFlow(false)
    /** Controls VoiceFab visibility from viewer screens (video player, readers, gallery) */
    val voiceFabVisible = MutableStateFlow(true)
    /** When true, VoiceFab stays visible (user tapped the mic, don't auto-hide) */
    val voiceFabPinned = MutableStateFlow(false)
}
