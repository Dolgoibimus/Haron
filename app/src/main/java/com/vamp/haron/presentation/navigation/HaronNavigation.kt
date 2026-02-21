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
import com.vamp.haron.presentation.editor.TextEditorScreen
import com.vamp.haron.presentation.explorer.ExplorerScreen
import com.vamp.haron.presentation.permission.PermissionScreen
import com.vamp.haron.presentation.player.MediaPlayerScreen

object HaronRoutes {
    const val PERMISSION = "permission"
    const val EXPLORER = "explorer"
    const val MEDIA_PLAYER = "media_player"
    const val MEDIA_PLAYER_ROUTE = "media_player?startIndex={startIndex}"
    const val TEXT_EDITOR = "text_editor"
    const val TEXT_EDITOR_ROUTE = "text_editor?filePath={filePath}&fileName={fileName}"

    fun mediaPlayer(startIndex: Int): String {
        return "media_player?startIndex=$startIndex"
    }

    fun textEditor(filePath: String, fileName: String): String {
        return "text_editor?filePath=${Uri.encode(filePath)}&fileName=${Uri.encode(fileName)}"
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
    }
}
