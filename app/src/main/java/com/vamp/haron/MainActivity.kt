package com.vamp.haron

import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vamp.core.db.EcosystemPreferences
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.presentation.applock.AppLockViewModel
import com.vamp.haron.presentation.applock.LockScreen
import com.vamp.haron.presentation.navigation.HaronNavigation
import com.vamp.haron.presentation.search.IndexNotificationViewModel
import com.vamp.haron.ui.theme.HaronScaling
import com.vamp.haron.ui.theme.HaronTheme
import com.vamp.haron.ui.theme.LocalHaronScaling
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val appLockViewModel: AppLockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Handle widget intent
        val navigateToPath = intent?.getStringExtra("navigate_to")

        // Lifecycle observer for app lock
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> appLockViewModel.onStop()
                Lifecycle.Event.ON_RESUME -> appLockViewModel.onResume()
                else -> {}
            }
        })

        setContent {
            val lockState by appLockViewModel.state.collectAsState()
            val scope = rememberCoroutineScope()

            // FLAG_SECURE when locked
            val view = LocalView.current
            LaunchedEffect(lockState.isLocked) {
                if (lockState.isLocked) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            var themeMode by remember { mutableStateOf(EcosystemPreferences.theme) }
            val isSystemDark = isSystemInDarkTheme()

            // Haron prefs for night mode and scaling
            val haronPrefs = remember {
                getSharedPreferences(HaronConstants.PREFS_NAME, MODE_PRIVATE)
            }
            var nightModeForced by remember { mutableStateOf(false) }
            var fontScale by remember { mutableStateOf(haronPrefs.getFloat("font_scale", 1.0f)) }
            var iconScale by remember { mutableStateOf(haronPrefs.getFloat("icon_scale", 1.0f)) }

            DisposableEffect(Unit) {
                val prefs = getSharedPreferences("ecosystem_prefs", MODE_PRIVATE)
                val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme") {
                        themeMode = EcosystemPreferences.theme
                    }
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)

                val haronListener = SharedPreferences.OnSharedPreferenceChangeListener { sp, key ->
                    when (key) {
                        "font_scale" -> fontScale = sp.getFloat("font_scale", 1.0f)
                        "icon_scale" -> iconScale = sp.getFloat("icon_scale", 1.0f)
                    }
                }
                haronPrefs.registerOnSharedPreferenceChangeListener(haronListener)

                onDispose {
                    prefs.unregisterOnSharedPreferenceChangeListener(listener)
                    haronPrefs.unregisterOnSharedPreferenceChangeListener(haronListener)
                }
            }

            // Night mode schedule check every 60 seconds
            LaunchedEffect(Unit) {
                while (true) {
                    val enabled = haronPrefs.getBoolean("night_mode_enabled", false)
                    if (enabled) {
                        val startH = haronPrefs.getInt("night_mode_start_hour", 22)
                        val startM = haronPrefs.getInt("night_mode_start_minute", 0)
                        val endH = haronPrefs.getInt("night_mode_end_hour", 7)
                        val endM = haronPrefs.getInt("night_mode_end_minute", 0)
                        val cal = Calendar.getInstance()
                        val nowMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                        val startMinutes = startH * 60 + startM
                        val endMinutes = endH * 60 + endM
                        nightModeForced = if (startMinutes < endMinutes) {
                            nowMinutes in startMinutes until endMinutes
                        } else {
                            nowMinutes >= startMinutes || nowMinutes < endMinutes
                        }
                    } else {
                        nightModeForced = false
                    }
                    delay(60_000)
                }
            }

            val darkTheme = when {
                nightModeForced -> true
                themeMode == "light" -> false
                themeMode == "dark" -> true
                else -> isSystemDark
            }

            if (!view.isInEditMode) {
                SideEffect {
                    val window = (view.context as android.app.Activity).window
                    WindowInsetsControllerCompat(window, view).isAppearanceLightStatusBars = !darkTheme
                }
            }

            val scaling = HaronScaling(fontScale = fontScale, iconScale = iconScale)

            CompositionLocalProvider(LocalHaronScaling provides scaling) {
                HaronTheme(darkTheme = darkTheme, fontScale = fontScale) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                            HaronNavigation(
                                navigateToPath = navigateToPath,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Index complete notification overlay
                            val indexNotifVm = hiltViewModel<IndexNotificationViewModel>()
                            val showIndexComplete by indexNotifVm.showNotification.collectAsState()
                            if (showIndexComplete) {
                                IndexCompleteOverlay(onDismiss = { indexNotifVm.dismiss() })
                            }

                            if (lockState.isLocked) {
                                LockScreen(
                                    lockMethod = lockState.lockMethod,
                                    onPinVerified = { pin ->
                                        appLockViewModel.onPinEntered(pin)
                                    },
                                    onBiometricRequest = {
                                        launchBiometric()
                                    },
                                    hasBiometric = lockState.hasBiometric,
                                    pinLength = appLockViewModel.getPinLength()
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun launchBiometric() {
        val authManager = (application as HaronApp).let {
            // Get AuthManager via Hilt — we use the entry point
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                applicationContext,
                AuthManagerEntryPoint::class.java
            ).authManager()
        }
        val executor = androidx.core.content.ContextCompat.getMainExecutor(this)
        val callback = object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                appLockViewModel.onBiometricSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // User cancelled or error — stay locked
            }

            override fun onAuthenticationFailed() {
                // Retry allowed
            }
        }
        val prompt = androidx.biometric.BiometricPrompt(this, executor, callback)
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.biometric_prompt_title))
            .setSubtitle(getString(R.string.biometric_prompt_subtitle))
            .setNegativeButtonText(getString(R.string.biometric_use_pin))
            .build()
        prompt.authenticate(info)
    }
}

@Composable
private fun IndexCompleteOverlay(onDismiss: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AuthManagerEntryPoint {
    fun authManager(): com.vamp.haron.data.security.AuthManager
}
