package com.vamp.haron.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vamp.haron.common.util.hasStoragePermission
import com.vamp.haron.presentation.archive.ArchiveViewerScreen
import com.vamp.haron.presentation.editor.TextEditorScreen
import com.vamp.haron.presentation.explorer.ExplorerScreen
import com.vamp.haron.presentation.gallery.GalleryScreen
import com.vamp.haron.presentation.pdf.PdfReaderScreen
import com.vamp.haron.presentation.permission.PermissionScreen
import com.vamp.haron.presentation.player.MediaPlayerScreen
import com.vamp.haron.presentation.duplicates.DuplicateDetectorScreen
import com.vamp.haron.presentation.storage.StorageAnalysisScreen

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
}

@Composable
fun HaronNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val startDestination = if (hasStoragePermission(context)) {
        HaronRoutes.EXPLORER
    } else {
        HaronRoutes.PERMISSION
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
                }
            )
        }
        composable(HaronRoutes.STORAGE_ANALYSIS) {
            StorageAnalysisScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(HaronRoutes.DUPLICATE_DETECTOR) {
            DuplicateDetectorScreen(
                onBack = { navController.popBackStack() }
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
    }
}
