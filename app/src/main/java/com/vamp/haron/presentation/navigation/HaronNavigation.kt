package com.vamp.haron.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.os.Environment
import android.widget.Toast
import com.vamp.haron.R
import com.vamp.haron.common.util.ReceivedFile
import com.vamp.haron.common.util.hasStoragePermission
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.data.voice.VoiceCommandManager
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.NavigationEvent
import com.vamp.haron.presentation.receive.ReceiveFilesDialog
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import com.vamp.haron.presentation.archive.ArchiveViewerScreen
import com.vamp.haron.presentation.editor.TextEditorScreen
import com.vamp.haron.presentation.explorer.ExplorerScreen
import com.vamp.haron.presentation.gallery.GalleryScreen
import com.vamp.haron.presentation.pdf.PdfReaderScreen
import com.vamp.haron.presentation.permission.PermissionScreen
import com.vamp.haron.presentation.player.MediaPlayerScreen
import com.vamp.haron.presentation.appmanager.AppManagerScreen
import com.vamp.haron.presentation.duplicates.DuplicateDetectorScreen
import com.vamp.haron.presentation.settings.GesturesVoiceScreen
import com.vamp.haron.presentation.settings.LogsScreen
import com.vamp.haron.presentation.settings.SettingsScreen
import com.vamp.haron.domain.model.SearchNavigationHolder
import com.vamp.haron.domain.model.TransferHolder
import com.vamp.haron.presentation.search.SearchScreen
import com.vamp.haron.presentation.storage.StorageAnalysisScreen
import android.webkit.MimeTypeMap
import androidx.hilt.navigation.compose.hiltViewModel
import com.vamp.haron.domain.model.CastMode
import com.vamp.haron.presentation.cast.CastViewModel
import com.vamp.haron.presentation.comparison.ComparisonScreen
import com.vamp.haron.presentation.steganography.SteganographyScreen
import com.vamp.haron.presentation.document.DocumentViewerScreen
import com.vamp.haron.presentation.library.LibraryScreen
import com.vamp.haron.presentation.library.LibrarySettingsScreen
import com.vamp.haron.presentation.terminal.TerminalScreen
import com.vamp.haron.presentation.transfer.TransferScreen

object HaronRoutes {
    const val PERMISSION = "permission"
    const val EXPLORER = "explorer"
    const val MEDIA_PLAYER = "media_player"
    const val MEDIA_PLAYER_ROUTE = "media_player?startIndex={startIndex}"
    const val TEXT_EDITOR = "text_editor"
    const val TEXT_EDITOR_ROUTE = "text_editor?filePath={filePath}&fileName={fileName}"
    const val GALLERY = "gallery"
    const val GALLERY_ROUTE = "gallery?startIndex={startIndex}"
    const val PDF_READER = "pdf_reader"
    const val PDF_READER_ROUTE = "pdf_reader?filePath={filePath}&fileName={fileName}"
    const val ARCHIVE_VIEWER = "archive_viewer"
    const val ARCHIVE_VIEWER_ROUTE = "archive_viewer?filePath={filePath}&fileName={fileName}"
    const val STORAGE_ANALYSIS = "storage_analysis"
    const val DUPLICATE_DETECTOR = "duplicate_detector"
    const val APP_MANAGER = "app_manager"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
    const val TRANSFER = "transfer"
    const val TRANSFER_ROUTE = "transfer?openScanner={openScanner}"

    fun transfer(openScanner: Boolean = false): String {
        return "transfer?openScanner=$openScanner"
    }
    const val TERMINAL = "terminal"
    const val GESTURES_VOICE = "gestures_voice"
    const val GESTURES_VOICE_ROUTE = "gestures_voice?tab={tab}"

    fun gesturesVoice(tab: Int = 0): String {
        return "gestures_voice?tab=$tab"
    }
    const val FEATURES = "features"
    const val SUPPORT = "support"
    const val COMPARISON = "comparison"
    const val STEGANOGRAPHY = "steganography"
    const val LIBRARY = "library"
    const val LIBRARY_SETTINGS = "library_settings"
    const val NAVBAR_SETTINGS = "navbar_settings"
    const val NAVBAR_ICONS = "navbar_icons"
    const val THEMES = "themes"
    const val MATRIX_SETTINGS = "matrix_settings"
    const val SNOWFALL_SETTINGS = "snowfall_settings"
    const val STARFIELD_SETTINGS = "starfield_settings"
    const val DUST_SETTINGS = "dust_settings"
    const val LOGS = "logs"
    const val CHANGELOG = "changelog"
    const val ABOUT = "about"
    const val TEXT_EDITOR_CLOUD = "text_editor_cloud"
    const val TEXT_EDITOR_CLOUD_ROUTE = "text_editor_cloud?filePath={filePath}&fileName={fileName}&cloudUri={cloudUri}&otherPanelPath={otherPanelPath}"
    const val DOCUMENT_VIEWER = "document_viewer"
    const val DOCUMENT_VIEWER_ROUTE = "document_viewer?filePath={filePath}&fileName={fileName}"

    fun mediaPlayer(startIndex: Int): String {
        return "media_player?startIndex=$startIndex"
    }

    fun textEditor(filePath: String, fileName: String): String {
        return "text_editor?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}"
    }

    fun textEditorCloud(filePath: String, fileName: String, cloudUri: String, otherPanelPath: String = ""): String {
        return "text_editor_cloud?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}&cloudUri=${Uri.encode(cloudUri)}&otherPanelPath=${Uri.encode(otherPanelPath)}"
    }

    fun gallery(startIndex: Int): String {
        return "gallery?startIndex=$startIndex"
    }

    fun pdfReader(filePath: String, fileName: String): String {
        return "pdf_reader?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}"
    }

    fun archiveViewer(filePath: String, fileName: String): String {
        return "archive_viewer?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}"
    }

    fun documentViewer(filePath: String, fileName: String): String {
        return "document_viewer?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}"
    }
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface VoiceManagerEntryPoint {
    fun voiceCommandManager(): VoiceCommandManager
}

@dagger.hilt.EntryPoint
@dagger.hilt.InstallIn(dagger.hilt.components.SingletonComponent::class)
interface PreferencesEntryPoint {
    fun haronPreferences(): com.vamp.haron.data.datastore.HaronPreferences
}

@Composable
fun HaronNavigation(navigateToPath: String? = null, modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination = if (hasStoragePermission(context)) {
        HaronRoutes.EXPLORER
    } else {
        HaronRoutes.PERMISSION
    }

    // Received files from external intent
    val activity = context as? com.vamp.haron.MainActivity
    val receivedFiles = activity?.receivedFiles?.value ?: emptyList()
    val isActionView = activity?.isActionView?.value ?: false
    var showReceiveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(receivedFiles) {
        if (receivedFiles.isNotEmpty()) {
            if (isActionView) {
                // ACTION_VIEW — open directly without dialog
                val file = receivedFiles.first()
                openReceivedFile(navController, file)
                activity?.receivedFiles?.value = emptyList()
                activity?.isActionView?.value = false
            } else {
                showReceiveDialog = true
            }
        }
    }

    if (showReceiveDialog && receivedFiles.isNotEmpty()) {
        ReceiveFilesDialog(
            files = receivedFiles,
            onSave = {
                showReceiveDialog = false
                saveReceivedFilesToDownloads(context, receivedFiles)
                activity?.receivedFiles?.value = emptyList()
            },
            onOpen = {
                showReceiveDialog = false
                val file = receivedFiles.first()
                openReceivedFile(navController, file)
                activity?.receivedFiles?.value = emptyList()
            },
            onDismiss = {
                showReceiveDialog = false
                activity?.receivedFiles?.value = emptyList()
            }
        )
    }

    // Global voice command dispatcher — handles navigation from any screen
    val voiceCmdMgr = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            VoiceManagerEntryPoint::class.java
        ).voiceCommandManager()
    }

    LaunchedEffect(Unit) {
        voiceCmdMgr.lastResult.collect { action ->
            if (action == null) return@collect
            val currentRoute = navController.currentDestination?.route

            if (action in GestureAction.GLOBAL_ACTIONS) {
                voiceCmdMgr.consumeResult()
                when (action) {
                    GestureAction.LOGS_PAUSE -> {
                        com.vamp.core.logger.EcosystemLogger.isPaused = true
                        Toast.makeText(context, context.getString(R.string.logs_paused), Toast.LENGTH_SHORT).show()
                    }
                    GestureAction.LOGS_RESUME -> {
                        com.vamp.core.logger.EcosystemLogger.isPaused = false
                        Toast.makeText(context, context.getString(R.string.logs_resumed), Toast.LENGTH_SHORT).show()
                    }
                    else -> {}
                }
            } else if (action.isScreenNavigation) {
                // Screen navigation — navigate directly, clean backstack to Explorer
                val targetRoute = when (action) {
                    GestureAction.OPEN_SETTINGS -> HaronRoutes.SETTINGS
                    GestureAction.OPEN_TERMINAL -> HaronRoutes.TERMINAL
                    GestureAction.OPEN_TRANSFER -> HaronRoutes.transfer()
                    GestureAction.GLOBAL_SEARCH -> HaronRoutes.SEARCH
                    GestureAction.OPEN_STORAGE -> HaronRoutes.STORAGE_ANALYSIS
                    GestureAction.OPEN_DUPLICATES -> HaronRoutes.DUPLICATE_DETECTOR
                    GestureAction.OPEN_APPS -> HaronRoutes.APP_MANAGER
                    GestureAction.OPEN_SCANNER -> HaronRoutes.transfer(openScanner = true)
                    GestureAction.OPEN_LOGS -> HaronRoutes.LOGS
                    else -> null
                }
                voiceCmdMgr.consumeResult()
                if (targetRoute != null && currentRoute != targetRoute) {
                    navController.navigate(targetRoute) {
                        popUpTo(HaronRoutes.EXPLORER) { inclusive = false }
                    }
                }
            } else {
                // Local actions (drawer, shelf, trash, hidden files, etc.)
                // Pass via pendingVoiceAction → ExplorerScreen executes.
                voiceCmdMgr.consumeResult()
                TransferHolder.pendingVoiceAction.value = action
                if (currentRoute != null && currentRoute != HaronRoutes.EXPLORER) {
                    navController.popBackStack(HaronRoutes.EXPLORER, inclusive = false)
                }
            }
        }
    }

    // Cast mode action handler (composable context for Hilt ViewModel)
    val castViewModel: CastViewModel = hiltViewModel(
        viewModelStoreOwner = context as androidx.activity.ComponentActivity
    )
    val pendingCastMode = CastActionHolder.pendingMode
    val pendingCastFiles = CastActionHolder.pendingFilePaths
    LaunchedEffect(pendingCastMode) {
        val mode = pendingCastMode ?: return@LaunchedEffect
        CastActionHolder.pendingMode = null
        val filePaths = pendingCastFiles
        CastActionHolder.pendingFilePaths = emptyList()
        val connected = castViewModel.isConnected.value
        com.vamp.core.logger.EcosystemLogger.d("CastFlow", "pendingCastMode=$mode, connected=$connected, files=${filePaths.size}")

        when (mode) {
            CastMode.SINGLE_MEDIA -> {
                if (filePaths.isNotEmpty()) {
                    castViewModel.setCastMode(CastMode.SINGLE_MEDIA)
                    val path = filePaths.first()
                    val name = File(path).name
                    if (connected) {
                        com.vamp.core.logger.EcosystemLogger.d("CastFlow", "SINGLE_MEDIA: casting now: $name")
                        castViewModel.castMedia(path, name)
                    } else {
                        com.vamp.core.logger.EcosystemLogger.d("CastFlow", "SINGLE_MEDIA: not connected, showing sheet + pending")
                        castViewModel.setPendingAction { castViewModel.castMedia(path, name) }
                        castViewModel.showSheet()
                    }
                }
            }
            CastMode.SLIDESHOW -> {
                val files = filePaths.map { File(it) }.filter { it.exists() }
                if (connected) {
                    com.vamp.core.logger.EcosystemLogger.d("CastFlow", "SLIDESHOW: casting now")
                    castViewModel.castSlideshow(files, com.vamp.haron.domain.model.SlideshowConfig())
                } else {
                    com.vamp.core.logger.EcosystemLogger.d("CastFlow", "SLIDESHOW: not connected, showing sheet + pending")
                    castViewModel.setPendingAction { castViewModel.castSlideshow(files, com.vamp.haron.domain.model.SlideshowConfig()) }
                    castViewModel.showSheet()
                }
            }
            CastMode.PDF_PRESENTATION -> {
                if (filePaths.isNotEmpty()) {
                    val path = filePaths.first()
                    if (connected) {
                        com.vamp.core.logger.EcosystemLogger.d("CastFlow", "PDF: casting now")
                        castViewModel.castPdfPresentation(path)
                    } else {
                        com.vamp.core.logger.EcosystemLogger.d("CastFlow", "PDF: not connected, showing sheet + pending")
                        castViewModel.setPendingAction { castViewModel.castPdfPresentation(path) }
                        castViewModel.showSheet()
                    }
                }
            }
            CastMode.SCREEN_MIRROR -> {
                com.vamp.core.logger.EcosystemLogger.d("CastFlow", "SCREEN_MIRROR: browser mode, launching MediaProjection")
                val activity = context as? com.vamp.haron.MainActivity ?: return@LaunchedEffect
                val projectionManager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                        as android.media.projection.MediaProjectionManager
                activity.mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
            }
        }
    }

    // Global Cast device selection sheet (for Explorer and other screens without their own)
    val showCastSheet by castViewModel.showDeviceSheet.collectAsState()
    val castDevices by castViewModel.devices.collectAsState()
    val castSearching by castViewModel.isSearching.collectAsState()
    val castConnectedDevice by castViewModel.connectedDeviceName.collectAsState()
    // Only show from HaronNavigation when on Explorer (other screens have their own sheet)
    val currentRoute = navController.currentBackStackEntry?.destination?.route
    val showGlobalSheet = showCastSheet && (currentRoute == null || currentRoute == HaronRoutes.EXPLORER)
    if (showGlobalSheet) {
        com.vamp.haron.presentation.cast.components.CastDeviceSheet(
            devices = castDevices,
            isSearching = castSearching,
            connectedDeviceName = castConnectedDevice,
            onSelectDevice = { device -> castViewModel.selectDeviceWithPendingAction(device) },
            onDisconnect = { castViewModel.disconnect() },
            onDismiss = { castViewModel.hideSheet() }
        )
    }

    // Navigate back to Explorer when pendingNavigationPath is set from any screen
    val pendingNavPath by TransferHolder.pendingNavigationPath.collectAsState()
    LaunchedEffect(pendingNavPath) {
        if (pendingNavPath != null) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != null && currentRoute != HaronRoutes.EXPLORER) {
                navController.popBackStack(HaronRoutes.EXPLORER, inclusive = false)
            }
        }
    }

    // Mic FAB long press → open voice commands list
    val pendingVoiceList by TransferHolder.pendingOpenVoiceList.collectAsState()
    LaunchedEffect(pendingVoiceList) {
        if (pendingVoiceList) {
            TransferHolder.pendingOpenVoiceList.value = false
            navController.navigate(HaronRoutes.gesturesVoice(tab = 1)) {
                popUpTo(HaronRoutes.EXPLORER) { inclusive = false }
            }
        }
    }

    // Matrix Rain animation — reactive config via SharedPreferences listener
    val matrixPrefs = remember { com.vamp.haron.data.datastore.HaronPreferences(context) }

    /** Check if animations should be globally suppressed (power save or low battery) */
    fun isAnimSuppressed(): Boolean {
        if (matrixPrefs.powerSaveEnabled) return true
        val threshold = matrixPrefs.animBatteryThreshold
        if (threshold > 0) {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            val level = bm?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            if (level <= threshold) return true
        }
        return false
    }

    fun readMatrixConfig(): com.vamp.haron.presentation.matrix.MatrixRainConfig {
        val suppressed = isAnimSuppressed()
        val isCharging = if (matrixPrefs.matrixOnlyCharging) {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.isCharging ?: false
        } else true
        return com.vamp.haron.presentation.matrix.MatrixRainConfig(
            enabled = matrixPrefs.matrixEnabled && isCharging && !suppressed,
            mode = matrixPrefs.matrixMode,
            color = androidx.compose.ui.graphics.Color(matrixPrefs.matrixColor.toInt() or 0xFF000000.toInt()),
            speed = matrixPrefs.matrixSpeed,
            density = matrixPrefs.matrixDensity,
            opacity = matrixPrefs.matrixOpacity,
            charset = matrixPrefs.matrixCharset,
            onlyCharging = matrixPrefs.matrixOnlyCharging,
            fps = matrixPrefs.matrixFps
        )
    }
    fun readSnowfallConfig(): com.vamp.haron.presentation.matrix.SnowfallConfig {
        val suppressed = isAnimSuppressed()
        val isCharging = if (matrixPrefs.snowfallOnlyCharging) {
            val bm = context.getSystemService(android.content.Context.BATTERY_SERVICE) as? android.os.BatteryManager
            bm?.isCharging ?: false
        } else true
        return com.vamp.haron.presentation.matrix.SnowfallConfig(
            enabled = matrixPrefs.snowfallEnabled && isCharging && !suppressed,
            speed = matrixPrefs.snowfallSpeed,
            density = matrixPrefs.snowfallDensity,
            opacity = matrixPrefs.snowfallOpacity,
            size = matrixPrefs.snowfallSize,
            onlyCharging = matrixPrefs.snowfallOnlyCharging,
            fps = matrixPrefs.snowfallFps
        )
    }
    fun readStarfieldConfig(): com.vamp.haron.presentation.matrix.StarfieldConfig {
        val suppressed = isAnimSuppressed()
        return com.vamp.haron.presentation.matrix.StarfieldConfig(
            enabled = matrixPrefs.starfieldEnabled && !suppressed, speed = matrixPrefs.starfieldSpeed, density = matrixPrefs.starfieldDensity,
            opacity = matrixPrefs.starfieldOpacity, size = matrixPrefs.starfieldSize, onlyCharging = matrixPrefs.starfieldOnlyCharging,
            fps = matrixPrefs.starfieldFps)
    }
    fun readDustConfig(): com.vamp.haron.presentation.matrix.DustConfig {
        val suppressed = isAnimSuppressed()
        return com.vamp.haron.presentation.matrix.DustConfig(
            enabled = matrixPrefs.dustEnabled && !suppressed, speed = matrixPrefs.dustSpeed, density = matrixPrefs.dustDensity,
            opacity = matrixPrefs.dustOpacity, size = matrixPrefs.dustSize, onlyCharging = matrixPrefs.dustOnlyCharging,
            fps = matrixPrefs.dustFps)
    }

    var matrixConfig by remember { mutableStateOf(readMatrixConfig()) }
    var snowfallConfig by remember { mutableStateOf(readSnowfallConfig()) }
    var starfieldConfig by remember { mutableStateOf(readStarfieldConfig()) }
    var dustConfig by remember { mutableStateOf(readDustConfig()) }
    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences(
            com.vamp.haron.common.constants.HaronConstants.PREFS_NAME,
            android.content.Context.MODE_PRIVATE
        )
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("matrix_") == true) matrixConfig = readMatrixConfig()
            if (key?.startsWith("snowfall_") == true) snowfallConfig = readSnowfallConfig()
            if (key?.startsWith("starfield_") == true) starfieldConfig = readStarfieldConfig()
            if (key?.startsWith("dust_") == true) dustConfig = readDustConfig()
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val animExcludedRoutes = remember { setOf("pdf_reader", "document_viewer", "media_player") }
    val animCurrentRoute = navController.currentBackStackEntry?.destination?.route?.substringBefore("?")
    val notExcluded = animCurrentRoute !in animExcludedRoutes
    val matrixShow = matrixConfig.enabled && notExcluded
    val snowfallShow = snowfallConfig.enabled && notExcluded
    val starfieldShow = starfieldConfig.enabled && notExcluded
    val dustShow = dustConfig.enabled && notExcluded
    Box(modifier = modifier) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(HaronRoutes.PERMISSION) {
            PermissionScreen(
                onPermissionGranted = {
                    navController.navigate(HaronRoutes.EXPLORER) {
                        popUpTo(HaronRoutes.PERMISSION) { inclusive = true }
                    }
                }
            )
        }
        composable(HaronRoutes.EXPLORER) {
            ExplorerScreen(
                initialNavigatePath = navigateToPath,
                onOpenMediaPlayer = { startIndex ->
                    navController.navigate(HaronRoutes.mediaPlayer(startIndex))
                },
                onOpenTextEditor = { filePath, fileName ->
                    navController.navigate(HaronRoutes.textEditor(filePath, fileName))
                },
                onOpenGallery = { startIndex ->
                    navController.navigate(HaronRoutes.gallery(startIndex))
                },
                onOpenPdfReader = { filePath, fileName ->
                    navController.navigate(HaronRoutes.pdfReader(filePath, fileName))
                },
                onOpenArchiveViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.archiveViewer(filePath, fileName))
                },
                onOpenStorageAnalysis = {
                    navController.navigate(HaronRoutes.STORAGE_ANALYSIS)
                },
                onOpenDuplicateDetector = {
                    navController.navigate(HaronRoutes.DUPLICATE_DETECTOR)
                },
                onOpenAppManager = {
                    navController.navigate(HaronRoutes.APP_MANAGER)
                },
                onOpenSettings = {
                    navController.navigate(HaronRoutes.SETTINGS)
                },
                onOpenFeatures = {
                    navController.navigate(HaronRoutes.FEATURES)
                },
                onOpenAbout = {
                    navController.navigate(HaronRoutes.ABOUT)
                },
                onOpenSupport = {
                    navController.navigate(HaronRoutes.SUPPORT)
                },
                onOpenGlobalSearch = {
                    navController.navigate(HaronRoutes.SEARCH)
                },
                onOpenTransfer = {
                    navController.navigate(HaronRoutes.transfer())
                },
                onNavigateToLibrary = {
                    navController.navigate(HaronRoutes.LIBRARY)
                },
                onOpenTerminal = {
                    navController.navigate(HaronRoutes.TERMINAL)
                },
                onOpenComparison = {
                    navController.navigate(HaronRoutes.COMPARISON)
                },
                onOpenSteganography = {
                    navController.navigate(HaronRoutes.STEGANOGRAPHY)
                },
                onOpenScanner = {
                    navController.navigate(HaronRoutes.transfer(openScanner = true))
                },
                onOpenDocumentViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.documentViewer(filePath, fileName))
                },
                onOpenTextEditorCloud = { localCachePath, fileName, cloudUri, otherPanelPath ->
                    navController.navigate(HaronRoutes.textEditorCloud(localCachePath, fileName, cloudUri, otherPanelPath))
                },
                onCastModeSelected = { mode, filePaths ->
                    handleCastModeSelected(mode, filePaths, context)
                }
            )
        }
        composable(HaronRoutes.STORAGE_ANALYSIS) {
            StorageAnalysisScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.APP_MANAGER) {
            AppManagerScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenGesturesVoice = { navController.navigate(HaronRoutes.gesturesVoice()) },
                onOpenNavbarSettings = { navController.navigate(HaronRoutes.NAVBAR_SETTINGS) },
                onOpenThemes = { navController.navigate(HaronRoutes.THEMES) },
            )
        }
        composable(HaronRoutes.LOGS) {
            LogsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.CHANGELOG) {
            com.vamp.haron.presentation.settings.ChangelogScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.ABOUT) {
            com.vamp.haron.presentation.settings.AboutScreen(
                onBack = { navController.popBackStack() },
                onOpenFeatures = { navController.navigate(HaronRoutes.FEATURES) },
                onOpenChangelog = { navController.navigate(HaronRoutes.CHANGELOG) },
                onOpenLogs = { navController.navigate(HaronRoutes.LOGS) }
            )
        }
        composable(HaronRoutes.FEATURES) {
            com.vamp.haron.presentation.features.FeaturesScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.SUPPORT) {
            com.vamp.haron.presentation.support.SupportScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            HaronRoutes.GESTURES_VOICE_ROUTE,
            arguments = listOf(
                androidx.navigation.navArgument("tab") {
                    type = androidx.navigation.NavType.IntType
                    defaultValue = 0
                }
            )
        ) { backStackEntry ->
            GesturesVoiceScreen(
                onBack = { navController.popBackStack() },
                initialTab = backStackEntry.arguments?.getInt("tab") ?: 0
            )
        }
        composable(
            HaronRoutes.TRANSFER_ROUTE,
            arguments = listOf(
                navArgument("openScanner") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            TransferScreen(
                onBack = { navController.popBackStack() },
                onOpenFolder = { path ->
                    TransferHolder.pendingNavigationPath.value = path
                    navController.popBackStack()
                },
                openScanner = backStackEntry.arguments?.getBoolean("openScanner") ?: false
            )
        }
        composable(HaronRoutes.TERMINAL) {
            TerminalScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.COMPARISON) {
            ComparisonScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.STEGANOGRAPHY) {
            SteganographyScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.SEARCH) {
            SearchScreen(
                onBack = { navController.popBackStack() },
                onNavigateToFile = { _ ->
                    navController.popBackStack()
                },
                onOpenMediaPlayer = { startIndex ->
                    navController.navigate(HaronRoutes.mediaPlayer(startIndex))
                },
                onOpenTextEditor = { filePath, fileName ->
                    navController.navigate(HaronRoutes.textEditor(filePath, fileName))
                },
                onOpenGallery = { startIndex ->
                    navController.navigate(HaronRoutes.gallery(startIndex))
                },
                onOpenPdfReader = { filePath, fileName ->
                    navController.navigate(HaronRoutes.pdfReader(filePath, fileName))
                },
                onOpenArchiveViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.archiveViewer(filePath, fileName))
                },
                onOpenDocumentViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.documentViewer(filePath, fileName))
                }
            )
        }
        composable(HaronRoutes.DUPLICATE_DETECTOR) {
            DuplicateDetectorScreen(
                onBack = { navController.popBackStack() },
                onOpenMediaPlayer = { startIndex ->
                    navController.navigate(HaronRoutes.mediaPlayer(startIndex))
                },
                onOpenTextEditor = { filePath, fileName ->
                    navController.navigate(HaronRoutes.textEditor(filePath, fileName))
                },
                onOpenGallery = { startIndex ->
                    navController.navigate(HaronRoutes.gallery(startIndex))
                },
                onOpenPdfReader = { filePath, fileName ->
                    navController.navigate(HaronRoutes.pdfReader(filePath, fileName))
                },
                onOpenArchiveViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.archiveViewer(filePath, fileName))
                },
                onOpenDocumentViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.documentViewer(filePath, fileName))
                }
            )
        }
        composable(
            route = HaronRoutes.MEDIA_PLAYER_ROUTE,
            arguments = listOf(
                navArgument("startIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            val playerPrefs = remember {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    PreferencesEntryPoint::class.java
                ).haronPreferences()
            }
            MediaPlayerScreen(
                startIndex = startIndex,
                prefs = playerPrefs,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = HaronRoutes.TEXT_EDITOR_ROUTE,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("fileName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            TextEditorScreen(
                filePath = filePath,
                fileName = fileName,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = HaronRoutes.TEXT_EDITOR_CLOUD_ROUTE,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("fileName") { type = NavType.StringType; defaultValue = "" },
                navArgument("cloudUri") { type = NavType.StringType; defaultValue = "" },
                navArgument("otherPanelPath") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            val cloudUri = backStackEntry.arguments?.getString("cloudUri") ?: ""
            val otherPanelPath = backStackEntry.arguments?.getString("otherPanelPath") ?: ""
            TextEditorScreen(
                filePath = filePath,
                fileName = fileName,
                cloudUri = cloudUri.ifEmpty { null },
                otherPanelPath = otherPanelPath.ifEmpty { null },
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = HaronRoutes.GALLERY_ROUTE,
            arguments = listOf(
                navArgument("startIndex") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backStackEntry ->
            val startIndex = backStackEntry.arguments?.getInt("startIndex") ?: 0
            GalleryScreen(
                startIndex = startIndex,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = HaronRoutes.PDF_READER_ROUTE,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("fileName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            PdfReaderScreen(
                filePath = filePath,
                fileName = fileName,
                onBack = { navController.popBackStack() },
                onNavigateToLibrary = { navController.navigate(HaronRoutes.LIBRARY) }
            )
        }
        composable(
            route = HaronRoutes.ARCHIVE_VIEWER_ROUTE,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("fileName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            ArchiveViewerScreen(
                archivePath = filePath,
                archiveName = fileName,
                onBack = { navController.popBackStack() }
            )
        }
        composable(
            route = HaronRoutes.DOCUMENT_VIEWER_ROUTE,
            arguments = listOf(
                navArgument("filePath") { type = NavType.StringType; defaultValue = "" },
                navArgument("fileName") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val filePath = backStackEntry.arguments?.getString("filePath") ?: ""
            val fileName = backStackEntry.arguments?.getString("fileName") ?: ""
            DocumentViewerScreen(
                filePath = filePath,
                fileName = fileName,
                onBack = { navController.popBackStack() },
                onNavigateToLibrary = { navController.navigate(HaronRoutes.LIBRARY) }
            )
        }
        composable(HaronRoutes.LIBRARY) {
            LibraryScreen(
                onBack = { navController.popBackStack() },
                onOpenReader = { filePath, fileName ->
                    navController.navigate(HaronRoutes.documentViewer(filePath, fileName))
                },
                onOpenPdfReader = { filePath, fileName ->
                    navController.navigate(HaronRoutes.pdfReader(filePath, fileName))
                },
                onOpenSettings = {
                    navController.navigate(HaronRoutes.LIBRARY_SETTINGS)
                }
            )
        }
        composable(HaronRoutes.LIBRARY_SETTINGS) {
            LibrarySettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.NAVBAR_SETTINGS) {
            val context = LocalContext.current
            val prefs = remember {
                com.vamp.haron.data.datastore.HaronPreferences(context)
            }
            com.vamp.haron.presentation.settings.NavbarSettingsScreen(
                prefs = prefs,
                onBack = { navController.popBackStack() },
                onOpenIcons = { navController.navigate(HaronRoutes.NAVBAR_ICONS) }
            )
        }
        composable(HaronRoutes.NAVBAR_ICONS) {
            com.vamp.haron.presentation.settings.NavbarIconsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.THEMES) {
            com.vamp.haron.presentation.matrix.ThemesScreen(
                onBack = { navController.popBackStack() },
                onOpenMatrix = { navController.navigate(HaronRoutes.MATRIX_SETTINGS) },
                onOpenSnowfall = { navController.navigate(HaronRoutes.SNOWFALL_SETTINGS) },
                onOpenStarfield = { navController.navigate(HaronRoutes.STARFIELD_SETTINGS) },
                onOpenDust = { navController.navigate(HaronRoutes.DUST_SETTINGS) }
            )
        }
        composable(HaronRoutes.SNOWFALL_SETTINGS) {
            com.vamp.haron.presentation.matrix.SnowfallSettingsScreen(
                prefs = matrixPrefs,
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.STARFIELD_SETTINGS) {
            val p = matrixPrefs
            fun disableOthers() { p.matrixEnabled = false; p.snowfallEnabled = false; p.dustEnabled = false }
            com.vamp.haron.presentation.matrix.AnimSettingsScreen(
                title = context.getString(R.string.starfield_title), initialEnabled = p.starfieldEnabled, initialSpeed = p.starfieldSpeed,
                initialDensity = p.starfieldDensity, initialOpacity = p.starfieldOpacity, initialSize = p.starfieldSize, initialOnlyCharging = p.starfieldOnlyCharging,
                onEnabledChange = { if (it) disableOthers(); p.starfieldEnabled = it }, onSpeedChange = { p.starfieldSpeed = it },
                onDensityChange = { p.starfieldDensity = it }, onOpacityChange = { p.starfieldOpacity = it },
                onSizeChange = { p.starfieldSize = it }, onOnlyChargingChange = { p.starfieldOnlyCharging = it },
                initialFps = p.starfieldFps, onFpsChange = { p.starfieldFps = it },
                onBack = { navController.popBackStack() }, previewBgColor = androidx.compose.ui.graphics.Color(0xFF0A0A1A)
            ) { spd, den, opa, sz -> com.vamp.haron.presentation.matrix.StarfieldCanvas(config = com.vamp.haron.presentation.matrix.StarfieldConfig(enabled = true, speed = spd, density = den, opacity = opa, size = sz)) }
        }
        composable(HaronRoutes.DUST_SETTINGS) {
            val p = matrixPrefs
            fun disableOthers() { p.matrixEnabled = false; p.snowfallEnabled = false; p.starfieldEnabled = false }
            com.vamp.haron.presentation.matrix.AnimSettingsScreen(
                title = context.getString(R.string.dust_title), initialEnabled = p.dustEnabled, initialSpeed = p.dustSpeed,
                initialDensity = p.dustDensity, initialOpacity = p.dustOpacity, initialSize = p.dustSize, initialOnlyCharging = p.dustOnlyCharging,
                onEnabledChange = { if (it) disableOthers(); p.dustEnabled = it }, onSpeedChange = { p.dustSpeed = it },
                onDensityChange = { p.dustDensity = it }, onOpacityChange = { p.dustOpacity = it },
                onSizeChange = { p.dustSize = it }, onOnlyChargingChange = { p.dustOnlyCharging = it },
                initialFps = p.dustFps, onFpsChange = { p.dustFps = it },
                onBack = { navController.popBackStack() }, previewBgColor = androidx.compose.ui.graphics.Color.Black
            ) { spd, den, opa, sz -> com.vamp.haron.presentation.matrix.DustCanvas(config = com.vamp.haron.presentation.matrix.DustConfig(enabled = true, speed = spd, density = den, opacity = opa, size = sz)) }
        }
        composable(HaronRoutes.MATRIX_SETTINGS) {
            com.vamp.haron.presentation.matrix.MatrixSettingsScreen(
                prefs = matrixPrefs,
                onBack = { navController.popBackStack() }
            )
        }
    }
    // Animation overlays on top of content
    if (matrixShow) {
        com.vamp.haron.presentation.matrix.MatrixRainCanvas(
            config = matrixConfig.copy(enabled = true),
            modifier = Modifier.fillMaxSize()
        )
    }
    if (snowfallShow) {
        com.vamp.haron.presentation.matrix.SnowfallCanvas(
            config = snowfallConfig.copy(enabled = true),
            modifier = Modifier.fillMaxSize()
        )
    }
    if (starfieldShow) {
        com.vamp.haron.presentation.matrix.StarfieldCanvas(
            config = starfieldConfig.copy(enabled = true),
            modifier = Modifier.fillMaxSize()
        )
    }
    if (dustShow) {
        com.vamp.haron.presentation.matrix.DustCanvas(
            config = dustConfig.copy(enabled = true),
            modifier = Modifier.fillMaxSize()
        )
    }
    } // Box
}

private fun saveReceivedFilesToDownloads(context: android.content.Context, files: List<ReceivedFile>) {
    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    var saved = 0
    files.forEach { received ->
        try {
            val src = File(received.localPath)
            var target = File(downloadsDir, received.displayName)
            if (target.exists()) {
                val base = received.displayName.substringBeforeLast('.')
                val ext = received.displayName.substringAfterLast('.', "")
                var counter = 1
                while (target.exists()) {
                    val name = if (ext.isNotEmpty()) "${base}_($counter).$ext" else "${base}_($counter)"
                    target = File(downloadsDir, name)
                    counter++
                }
            }
            src.copyTo(target)
            src.delete()
            saved++
        } catch (_: Exception) { }
    }
    if (saved > 0) {
        Toast.makeText(
            context,
            context.getString(R.string.received_saved_count, saved),
            Toast.LENGTH_SHORT
        ).show()
    }
}

/** Holder for cast mode + file paths, set from ExplorerScreen, consumed in HaronNavigation */
object CastActionHolder {
    var pendingMode: CastMode? by androidx.compose.runtime.mutableStateOf(null)
    var pendingFilePaths: List<String> = emptyList()
}

private fun handleCastModeSelected(
    mode: CastMode,
    filePaths: List<String>,
    context: android.content.Context
) {
    com.vamp.core.logger.EcosystemLogger.d("CastFlow", "handleCastModeSelected: mode=$mode, files=${filePaths.size}")
    CastActionHolder.pendingMode = mode
    CastActionHolder.pendingFilePaths = filePaths
}

private fun guessMimeTypeFromFile(file: File): String {
    val ext = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
}

private fun openReceivedFile(
    navController: androidx.navigation.NavController,
    file: ReceivedFile
) {
    val ext = file.displayName.substringAfterLast('.', "").lowercase()
    val path = file.localPath
    val name = file.displayName
    when {
        ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") -> {
            com.vamp.haron.domain.model.GalleryHolder.items = listOf(
                com.vamp.haron.domain.model.GalleryHolder.GalleryItem(
                    filePath = path, fileName = name, fileSize = file.size
                )
            )
            com.vamp.haron.domain.model.GalleryHolder.startIndex = 0
            navController.navigate(HaronRoutes.gallery(0))
        }
        ext in listOf("mp4", "avi", "mkv", "mov", "webm", "3gp", "flv", "ts", "m4v") -> {
            com.vamp.haron.domain.model.PlaylistHolder.items = listOf(
                com.vamp.haron.domain.model.PlaylistHolder.PlaylistItem(
                    filePath = path, fileName = name, fileType = "video"
                )
            )
            com.vamp.haron.domain.model.PlaylistHolder.startIndex = 0
            navController.navigate(HaronRoutes.mediaPlayer(0))
        }
        ext in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus") -> {
            com.vamp.haron.domain.model.PlaylistHolder.items = listOf(
                com.vamp.haron.domain.model.PlaylistHolder.PlaylistItem(
                    filePath = path, fileName = name, fileType = "audio"
                )
            )
            com.vamp.haron.domain.model.PlaylistHolder.startIndex = 0
            navController.navigate(HaronRoutes.mediaPlayer(0))
        }
        ext == "pdf" -> {
            navController.navigate(HaronRoutes.pdfReader(path, name))
        }
        name.lowercase().let { it.endsWith(".fb2.zip") || (it.endsWith(".zip") && it.contains(".fb2")) } -> {
            navController.navigate(HaronRoutes.documentViewer(path, name))
        }
        ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "gtar") -> {
            navController.navigate(HaronRoutes.archiveViewer(path, name))
        }
        ext in listOf("doc", "docx", "odt", "rtf", "fb2", "xls", "xlsx", "csv", "tsv") -> {
            navController.navigate(HaronRoutes.documentViewer(path, name))
        }
        ext == "apk" -> {
            // Launch system installer directly
            try {
                val context = navController.context
                val apkFile = File(path)
                val uri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                val intent = android.content.Intent(android.content.Intent.ACTION_INSTALL_PACKAGE).apply {
                    data = uri
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(android.content.Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    putExtra(android.content.Intent.EXTRA_RETURN_RESULT, true)
                }
                context.startActivity(intent)
            } catch (_: Exception) { }
        }
        else -> {
            navController.navigate(HaronRoutes.textEditor(path, name))
        }
    }
}
