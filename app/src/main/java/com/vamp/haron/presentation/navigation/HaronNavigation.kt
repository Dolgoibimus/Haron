package com.vamp.haron.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.vamp.haron.common.util.hasStoragePermission
import com.vamp.haron.presentation.explorer.ExplorerScreen
import com.vamp.haron.presentation.permission.PermissionScreen

object HaronRoutes {
    const val PERMISSION = "permission"
    const val EXPLORER = "explorer"
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
            ExplorerScreen()
        }
    }
}
