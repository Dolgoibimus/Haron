package com.vamp.haron.presentation.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.vamp.haron.service.ScreenMirrorService
import com.vamp.haron.presentation.document.DocumentViewerScreen
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
    const val TERMINAL = "terminal"
    const val GESTURES_VOICE = "gestures_voice"
    const val GESTURES_VOICE_ROUTE = "gestures_voice?tab={tab}"

    fun gesturesVoice(tab: Int = 0): String {
        return "gestures_voice?tab=$tab"
    }
    const val COMPARISON = "comparison"
    const val STEGANOGRAPHY = "steganography"
    const val DOCUMENT_VIEWER = "document_viewer"
    const val DOCUMENT_VIEWER_ROUTE = "document_viewer?filePath={filePath}&fileName={fileName}"

    fun mediaPlayer(startIndex: Int): String {
        return "media_player?startIndex=$startIndex"
    }

    fun textEditor(filePath: String, fileName: String): String {
        return "text_editor?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}"
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

            if (action.isScreenNavigation) {
                // Screen navigation — navigate directly, clean backstack to Explorer
                val targetRoute = when (action) {
                    GestureAction.OPEN_SETTINGS -> HaronRoutes.SETTINGS
                    GestureAction.OPEN_TERMINAL -> HaronRoutes.TERMINAL
                    GestureAction.OPEN_TRANSFER -> HaronRoutes.TRANSFER
                    GestureAction.GLOBAL_SEARCH -> HaronRoutes.SEARCH
                    GestureAction.OPEN_STORAGE -> HaronRoutes.STORAGE_ANALYSIS
                    GestureAction.OPEN_DUPLICATES -> HaronRoutes.DUPLICATE_DETECTOR
                    GestureAction.OPEN_APPS -> HaronRoutes.APP_MANAGER
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
    val castViewModel: CastViewModel = hiltViewModel()
    val pendingCastMode = CastActionHolder.pendingMode
    val pendingCastFiles = CastActionHolder.pendingFilePaths
    LaunchedEffect(pendingCastMode) {
        val mode = pendingCastMode ?: return@LaunchedEffect
        CastActionHolder.pendingMode = null
        val filePaths = pendingCastFiles
        CastActionHolder.pendingFilePaths = emptyList()

        when (mode) {
            CastMode.SINGLE_MEDIA -> {
                if (filePaths.isNotEmpty()) {
                    castViewModel.setCastMode(CastMode.SINGLE_MEDIA)
                    if (castViewModel.isConnected.value) {
                        castViewModel.castMedia(filePaths.first(), File(filePaths.first()).name)
                    } else {
                        castViewModel.showSheet()
                    }
                }
            }
            CastMode.SLIDESHOW -> {
                val files = filePaths.map { File(it) }.filter { it.exists() }
                if (castViewModel.isConnected.value) {
                    castViewModel.castSlideshow(files, com.vamp.haron.domain.model.SlideshowConfig())
                } else {
                    castViewModel.showSheet()
                }
            }
            CastMode.PDF_PRESENTATION -> {
                if (filePaths.isNotEmpty()) {
                    if (castViewModel.isConnected.value) {
                        castViewModel.castPdfPresentation(filePaths.first())
                    } else {
                        castViewModel.showSheet()
                    }
                }
            }
            CastMode.FILE_INFO -> {
                if (filePaths.isNotEmpty()) {
                    val file = File(filePaths.first())
                    if (castViewModel.isConnected.value) {
                        castViewModel.castFileInfo(
                            name = file.name,
                            path = file.absolutePath,
                            size = file.length().toFileSize(context),
                            modified = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                .format(java.util.Date(file.lastModified())),
                            mimeType = guessMimeTypeFromFile(file)
                        )
                    } else {
                        castViewModel.showSheet()
                    }
                }
            }
            CastMode.SCREEN_MIRROR -> { /* handled in non-composable function */ }
        }
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

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
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
                onOpenGlobalSearch = {
                    navController.navigate(HaronRoutes.SEARCH)
                },
                onOpenTransfer = {
                    navController.navigate(HaronRoutes.TRANSFER)
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
                onOpenDocumentViewer = { filePath, fileName ->
                    navController.navigate(HaronRoutes.documentViewer(filePath, fileName))
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
                onOpenGesturesVoice = { navController.navigate(HaronRoutes.gesturesVoice()) }
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
        composable(HaronRoutes.TRANSFER) {
            TransferScreen(
                onBack = { navController.popBackStack() },
                onOpenFolder = { path ->
                    TransferHolder.pendingNavigationPath.value = path
                    navController.popBackStack()
                }
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
            MediaPlayerScreen(
                startIndex = startIndex,
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
                onBack = { navController.popBackStack() }
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
                onBack = { navController.popBackStack() }
            )
        }
    }
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
    var pendingMode: CastMode? = null
    var pendingFilePaths: List<String> = emptyList()
}

private fun handleCastModeSelected(
    mode: CastMode,
    filePaths: List<String>,
    context: android.content.Context
) {
    // Save for composable consumption, or handle non-composable actions directly
    when (mode) {
        CastMode.SCREEN_MIRROR -> {
            val activity = context as? com.vamp.haron.MainActivity ?: return
            val projectionManager = context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE)
                    as android.media.projection.MediaProjectionManager
            activity.mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
        else -> {
            // Store for composable-level CastViewModel handling
            CastActionHolder.pendingMode = mode
            CastActionHolder.pendingFilePaths = filePaths
        }
    }
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
        name.lowercase().endsWith(".fb2.zip") -> {
            navController.navigate(HaronRoutes.documentViewer(path, name))
        }
        ext in listOf("zip", "rar", "7z", "tar", "gz") -> {
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
