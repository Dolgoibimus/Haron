package com.vamp.haron.common.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.vamp.haron.data.datastore.HaronPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: HaronPreferences
) {
    private val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    /** Short double tap — operation succeeded */
    fun success() {
        if (!preferences.hapticEnabled) return
        vibrate(longArrayOf(0, 30, 60, 30), intArrayOf(0, 120, 0, 120))
    }

    /** Triple pulse — warning/confirmation */
    fun warning() {
        if (!preferences.hapticEnabled) return
        vibrate(longArrayOf(0, 40, 50, 40, 50, 40), intArrayOf(0, 80, 0, 80, 0, 80))
    }

    /** Long heavy vibration — error */
    fun error() {
        if (!preferences.hapticEnabled) return
        vibrate(longArrayOf(0, 200), intArrayOf(0, 255))
    }

    /** Ascending pattern — task/service completed */
    fun completion() {
        if (!preferences.hapticEnabled) return
        vibrate(longArrayOf(0, 20, 40, 30, 40, 50), intArrayOf(0, 60, 0, 120, 0, 200))
    }

    /** Single short tick */
    fun tick() {
        if (!preferences.hapticEnabled) return
        vibrate(longArrayOf(0, 15), intArrayOf(0, 100))
    }

    private fun vibrate(timings: LongArray, amplitudes: IntArray) {
        if (!vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
        vibrator.vibrate(effect)
    }
}
