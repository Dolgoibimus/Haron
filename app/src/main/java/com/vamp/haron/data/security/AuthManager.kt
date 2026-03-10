package com.vamp.haron.data.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
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
        val result = hashPin(pin) == stored
        if (result) {
            EcosystemLogger.d(HaronConstants.TAG, "AuthManager: PIN verification succeeded")
        } else {
            EcosystemLogger.w(HaronConstants.TAG, "AuthManager: PIN verification failed")
        }
        return result
    }

    fun setPin(pin: String) {
        preferences.pinHash = hashPin(pin)
        preferences.pinLength = pin.length
        EcosystemLogger.d(HaronConstants.TAG, "AuthManager: PIN set (length=${pin.length})")
    }

    fun removePin() {
        preferences.pinHash = null
        preferences.pinLength = 4
        EcosystemLogger.d(HaronConstants.TAG, "AuthManager: PIN removed")
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
                    EcosystemLogger.d(HaronConstants.TAG, "AuthManager: biometric authentication succeeded")
                    if (cont.isActive) cont.resume(Result.success(true))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    EcosystemLogger.e(HaronConstants.TAG, "AuthManager: biometric error code=$errorCode: $errString")
                    if (cont.isActive) cont.resume(Result.failure(Exception(errString.toString())))
                }

                override fun onAuthenticationFailed() {
                    EcosystemLogger.w(HaronConstants.TAG, "AuthManager: biometric authentication failed (retry possible)")
                    // Don't resume — user can retry
                }
            }
            val prompt = BiometricPrompt(activity, executor, callback)
            val method = getAppLockMethod()
            val negText = if (method == AppLockMethod.BIOMETRIC_ONLY) {
                context.getString(R.string.cancel)
            } else {
                context.getString(R.string.biometric_use_pin)
            }
            val info = BiometricPrompt.PromptInfo.Builder()
                .setTitle(context.getString(R.string.biometric_prompt_title))
                .setSubtitle(context.getString(R.string.biometric_prompt_subtitle))
                .setNegativeButtonText(negText)
                .build()
            prompt.authenticate(info)
            cont.invokeOnCancellation { prompt.cancelAuthentication() }
        }

    fun getAppLockMethod(): AppLockMethod =
        AppLockMethod.entries.getOrElse(preferences.appLockMethod) { AppLockMethod.NONE }

    fun setAppLockMethod(method: AppLockMethod) {
        EcosystemLogger.d(HaronConstants.TAG, "AuthManager: lock method changed to $method")
        preferences.appLockMethod = method.ordinal
    }

    // --- Security question ---

    fun setSecurityQuestion(question: String, answer: String) {
        preferences.securityQuestion = question
        preferences.securityAnswerHash = hashAnswer(answer)
        EcosystemLogger.d(HaronConstants.TAG, "AuthManager: security question set")
    }

    fun getSecurityQuestion(): String? = preferences.securityQuestion

    fun hasSecurityQuestion(): Boolean = preferences.securityQuestion != null && preferences.securityAnswerHash != null

    fun verifySecurityAnswer(answer: String): Boolean {
        val stored = preferences.securityAnswerHash ?: return false
        val result = hashAnswer(answer) == stored
        EcosystemLogger.d(HaronConstants.TAG, "AuthManager: security answer verification ${if (result) "succeeded" else "failed"}")
        return result
    }

    fun resetPinViaSecurityAnswer(answer: String): Boolean {
        if (!verifySecurityAnswer(answer)) {
            EcosystemLogger.w(HaronConstants.TAG, "AuthManager: PIN reset via security answer failed")
            return false
        }
        removePin()
        setAppLockMethod(AppLockMethod.NONE)
        EcosystemLogger.d(HaronConstants.TAG, "AuthManager: PIN reset via security answer succeeded")
        return true
    }

    private fun hashAnswer(answer: String): String {
        return hashPin(answer.trim().lowercase())
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
