package com.vamp.haron

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.SendToMobile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.vamp.core.db.EcosystemPreferences
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.presentation.applock.AppLockViewModel
import com.vamp.haron.presentation.applock.LockScreen
import com.vamp.haron.presentation.cast.CastOverlay
import com.vamp.haron.presentation.navigation.HaronNavigation
import com.vamp.haron.presentation.voice.VoiceFab
import com.vamp.haron.common.util.IntentHandler
import com.vamp.haron.common.util.ReceivedFile
import com.vamp.haron.presentation.search.IndexNotificationViewModel
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.presentation.transfer.GlobalReceiveViewModel
import com.vamp.haron.presentation.transfer.ReceiveNotificationViewModel
import com.vamp.haron.service.ScreenMirrorService
import com.vamp.haron.ui.theme.HaronScaling
import com.vamp.haron.ui.theme.HaronTheme
import com.vamp.haron.ui.theme.LocalHaronScaling
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @javax.inject.Inject
    lateinit var receiveFileManager: com.vamp.haron.data.transfer.ReceiveFileManager

    @javax.inject.Inject
    lateinit var googleCastManager: com.vamp.haron.data.cast.GoogleCastManager

    @javax.inject.Inject
    lateinit var dlnaManager: com.vamp.haron.data.cast.DlnaManager

    private val appLockViewModel: AppLockViewModel by viewModels()

    /** Received files from external intents (ACTION_VIEW / ACTION_SEND) */
    internal val receivedFiles = mutableStateOf<List<ReceivedFile>>(emptyList())
    internal val isActionView = mutableStateOf(false)

    /** MediaProjection launcher for screen mirroring */
    lateinit var mediaProjectionLauncher: ActivityResultLauncher<Intent>
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Hide system navbar, keep status bar
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // Start transfer listener — runs in ReceiveFileManager's own scope,
        // survives Activity recreation and screen transitions
        receiveFileManager.ensureListening()

        // Register MediaProjection launcher
        mediaProjectionLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            com.vamp.core.logger.EcosystemLogger.d("CastFlow", "MediaProjection result: code=${result.resultCode}, hasData=${result.data != null}")
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenMirrorService::class.java).apply {
                    action = ScreenMirrorService.ACTION_START
                    putExtra(ScreenMirrorService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(ScreenMirrorService.EXTRA_RESULT_DATA, result.data)
                }
                startForegroundService(serviceIntent)

                // Wait for mirror server URL and cast it to TV
                val castVm = androidx.lifecycle.ViewModelProvider(this)[com.vamp.haron.presentation.cast.CastViewModel::class.java]
                lifecycleScope.launch {
                    repeat(20) {
                        val url = ScreenMirrorService.serverUrl
                        if (url != null) {
                            castVm.castMirrorUrl(url)
                            return@launch
                        }
                        delay(500)
                    }
                    com.vamp.core.logger.EcosystemLogger.e(HaronConstants.TAG, "Screen mirror: server URL not available after 10s")
                }
            }
        }

        // Handle widget intent
        val navigateToPath = intent?.getStringExtra("navigate_to")

        // Handle external file intent
        if (savedInstanceState == null) {
            processExternalIntent(intent)
        }

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

            // Night mode schedule check — only while app is in foreground
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
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

                            // Voice command FAB (global overlay)
                            if (!lockState.isLocked) {
                                VoiceFab()
                            }

                            // Cast remote control overlay
                            CastOverlay()

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
                                    pinLength = appLockViewModel.getPinLength(),
                                    securityQuestion = appLockViewModel.getSecurityQuestion(),
                                    onResetPin = { answer ->
                                        appLockViewModel.resetPinViaAnswer(answer)
                                    }
                                )
                            }

                            // Friend receive notification overlay (trusted devices only) — on top of everything
                            val receiveNotifVm = hiltViewModel<ReceiveNotificationViewModel>()
                            val friendSender by receiveNotifVm.senderName.collectAsState()
                            if (friendSender != null) {
                                ReceiveCompleteOverlay(
                                    senderName = friendSender!!,
                                    onDismiss = { receiveNotifVm.dismiss() },
                                    onNavigateToFolder = {
                                        receiveNotifVm.dismiss()
                                        val dir = java.io.File(
                                            android.os.Environment.getExternalStoragePublicDirectory(
                                                android.os.Environment.DIRECTORY_DOWNLOADS
                                            ), "Haron"
                                        )
                                        TransferHolder.pendingNavigationPath.value = dir.absolutePath
                                    }
                                )
                            }

                            // Global incoming transfer dialog (untrusted devices) — on top of everything
                            val globalReceiveVm = hiltViewModel<GlobalReceiveViewModel>()
                            val incomingRequest by globalReceiveVm.incoming.collectAsState()
                            if (incomingRequest != null) {
                                IncomingTransferDialog(
                                    deviceName = incomingRequest!!.deviceName,
                                    fileCount = incomingRequest!!.fileCount,
                                    onAccept = { globalReceiveVm.accept() },
                                    onDecline = { globalReceiveVm.decline() }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val castConnected = googleCastManager.isConnected.value || dlnaManager.isConnected.value
        if (castConnected) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    if (dlnaManager.isConnected.value) {
                        dlnaManager.sendRemoteInput(com.vamp.haron.domain.model.RemoteInputEvent.VolumeChange(0.01f))
                    } else {
                        val current = googleCastManager.getCurrentVolume()
                        googleCastManager.setVolume((current + 0.01).coerceIn(0.0, 1.0))
                    }
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    if (dlnaManager.isConnected.value) {
                        dlnaManager.sendRemoteInput(com.vamp.haron.domain.model.RemoteInputEvent.VolumeChange(-0.01f))
                    } else {
                        val current = googleCastManager.getCurrentVolume()
                        googleCastManager.setVolume((current - 0.01).coerceIn(0.0, 1.0))
                    }
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processExternalIntent(intent)
    }

    private fun processExternalIntent(intent: Intent?) {
        if (intent == null) return

        // Handle Cloud OAuth callback (haron://oauth/{provider}?code=XXX)
        val data = intent.data
        if (data != null && data.scheme == "haron" && data.host == "oauth") {
            val providerScheme = data.pathSegments?.firstOrNull()
            val code = data.getQueryParameter("code")
            if (providerScheme != null && code != null) {
                com.vamp.core.logger.EcosystemLogger.d(
                    HaronConstants.TAG,
                    "OAuth callback: provider=$providerScheme, code=${code.take(10)}..."
                )
                com.vamp.haron.data.cloud.CloudOAuthHelper.pendingAuth.value =
                    com.vamp.haron.data.cloud.CloudOAuthHelper.PendingAuth(providerScheme, code)
            }
            return
        }

        val action = intent.action
        if (action == Intent.ACTION_VIEW || action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
            val files = IntentHandler.handleIntent(intent, this)
            if (files.isNotEmpty()) {
                isActionView.value = action == Intent.ACTION_VIEW
                receivedFiles.value = files
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

@Composable
private fun ReceiveCompleteOverlay(
    senderName: String,
    onDismiss: () -> Unit,
    onNavigateToFolder: () -> Unit
) {
    val transition = rememberInfiniteTransition(label = "receive_pulse")
    val alpha by transition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "receive_alpha"
    )

    var circleRect by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                val longPressMs = viewConfiguration.longPressTimeoutMillis
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    val inCircle = circleRect.contains(down.position)

                    if (!inCircle) {
                        while (true) {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                            if (event.changes.all { !it.pressed }) break
                        }
                        onDismiss()
                        return@awaitEachGesture
                    }

                    val upOrTimeout = withTimeoutOrNull(longPressMs) {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            change.consume()
                            if (!change.pressed) return@withTimeoutOrNull change
                        }
                        null
                    }
                    if (upOrTimeout == null) {
                        onNavigateToFolder()
                    } else {
                        onDismiss()
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInParent()
                    circleRect = androidx.compose.ui.geometry.Rect(
                        pos.x, pos.y,
                        pos.x + coords.size.width, pos.y + coords.size.height
                    )
                }
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.tertiary.copy(alpha = alpha))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.SendToMobile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.9f),
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = senderName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onTertiary.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun IncomingTransferDialog(
    deviceName: String,
    fileCount: Int,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        title = {
            Text(stringResource(R.string.transfer_receive))
        },
        text = {
            Text("$deviceName — $fileCount ${if (fileCount == 1) "file" else "files"}")
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface AuthManagerEntryPoint {
    fun authManager(): com.vamp.haron.data.security.AuthManager
}
