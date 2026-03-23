package com.vamp.haron.data.player

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences

/**
 * Manages Do Not Disturb mode during media playback.
 * Reads settings based on media type (video/audio).
 * Saves original state, applies DND with user-configured policy, restores on exit.
 */
class DndManager(
    private val context: Context,
    private val prefs: HaronPreferences,
    private val isVideo: Boolean
) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var originalInterruptionFilter: Int = NotificationManager.INTERRUPTION_FILTER_ALL
    private var originalRingerMode: Int = AudioManager.RINGER_MODE_NORMAL
    private var originalNotificationVolume: Int = 0
    private var isActive = false

    val hasPermission: Boolean
        get() = notificationManager.isNotificationPolicyAccessGranted

    fun activate() {
        if (!prefs.playerDndEnabled(isVideo) || !hasPermission || isActive) return

        // Save original state
        originalInterruptionFilter = notificationManager.currentInterruptionFilter
        originalRingerMode = audioManager.ringerMode
        originalNotificationVolume = audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        isActive = true

        // Build policy
        var priorityCategories = 0
        if (prefs.playerDndAllowCalls(isVideo)) priorityCategories = priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
        if (prefs.playerDndAllowMessages(isVideo)) priorityCategories = priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES
        if (prefs.playerDndAllowAlarms(isVideo)) priorityCategories = priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
        if (prefs.playerDndAllowRepeatCallers(isVideo)) priorityCategories = priorityCategories or NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS

        val callSenders = when (prefs.playerDndCallSenders(isVideo)) {
            0 -> NotificationManager.Policy.PRIORITY_SENDERS_ANY
            1 -> NotificationManager.Policy.PRIORITY_SENDERS_STARRED
            2 -> NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS
            3 -> NotificationManager.Policy.PRIORITY_SENDERS_ANY
            else -> NotificationManager.Policy.PRIORITY_SENDERS_ANY
        }

        var suppressedEffects = 0
        if (prefs.playerDndSuppressHeadsUp(isVideo)) {
            suppressedEffects = suppressedEffects or NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK
        }
        if (prefs.playerDndSuppressStatusBar(isVideo)) {
            suppressedEffects = suppressedEffects or NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR
        }
        if (prefs.playerDndSuppressScreenOn(isVideo)) {
            @Suppress("DEPRECATION")
            suppressedEffects = suppressedEffects or NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON
        }

        val policy = NotificationManager.Policy(
            priorityCategories,
            callSenders,
            callSenders,
            suppressedEffects
        )

        try {
            notificationManager.setNotificationPolicy(policy)
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)

            if (prefs.playerDndSilentMode(isVideo)) {
                // Mute notifications/ringtone but keep media volume untouched
                audioManager.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, 0, 0)
            }

            EcosystemLogger.d(HaronConstants.TAG, "DndManager: activated (${if (isVideo) "video" else "audio"}), categories=$priorityCategories, senders=$callSenders, suppressed=$suppressedEffects")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "DndManager: failed to activate: ${e.message}")
            isActive = false
        }
    }

    fun deactivate() {
        if (!isActive) return
        isActive = false

        try {
            notificationManager.setInterruptionFilter(originalInterruptionFilter)
            audioManager.ringerMode = originalRingerMode
            audioManager.setStreamVolume(AudioManager.STREAM_NOTIFICATION, originalNotificationVolume, 0)
            EcosystemLogger.d(HaronConstants.TAG, "DndManager: deactivated, restored filter=$originalInterruptionFilter, ringer=$originalRingerMode")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "DndManager: failed to deactivate: ${e.message}")
        }
    }
}
