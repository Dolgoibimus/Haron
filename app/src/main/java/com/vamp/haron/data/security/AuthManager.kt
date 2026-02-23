package com.vamp.haron.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vamp.haron.R
import com.vamp.haron.data.datastore.HaronPreferences
import com.vamp.haron.domain.model.AppLockMethod
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AuthManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: HaronPreferences
) {

    fun isPinSet(): Boolean = preferences.pinHash != null

    fun verifyPin(pin: String): Boolean {
        val stored = preferences.pinHash ?: return false
        return hashPin(pin) == stored
    }

    fun setPin(pin: String) {
        preferences.pinHash = hashPin(pin)
        preferences.pinLength = pin.length
    }

    fun removePin() {
        preferences.pinHash = null
        preferences.pinLength = 4
    }

    fun getPinLength(): Int = preferences.pinLength

    fun hasBiometricHardware(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE
    }

    fun isBiometricEnrolled(): Boolean {
        val bm = BiometricManager.from(context)
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    suspend fun authenticateBiometric(activity: FragmentActivity): Result<Boolean> =
        suspendCancellableCoroutine { cont ->
            val executor = ContextCompat.getMainExecutor(activity)
            val callback = object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    if (cont.isActive) cont.resume(Result.success(true))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (cont.isActive) cont.resume(Result.failure(Exception(errString.toString())))
                }

                override fun onAuthenticationFailed() {
                    // Don't resume — user can retry
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_prompt_title))
                .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(context.getString(R.string.biometric_use_pin))
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }

    fun getAppLockMethod(): AppLockMethod =
        AppLockMethod.entries.getOrElse(preferences.appLockMethod) { AppLockMethod.NONE }

    fun setAppLockMethod(method: AppLockMethod) {
        preferences.appLockMethod = method.ordinal
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
