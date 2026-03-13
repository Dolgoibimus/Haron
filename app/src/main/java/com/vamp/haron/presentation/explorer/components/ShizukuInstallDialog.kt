package com.vamp.haron.presentation.explorer.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vamp.haron.R

@Composable
fun ShizukuNotInstalledDialog(
    onDismiss: () -> Unit,
    onOpenPlayStore: () -> Unit,
    onOpenGitHub: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shizuku_not_installed_title)) },
        text = {
            Text(
                stringResource(R.string.shizuku_not_installed_body),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenPlayStore) {
                Text(stringResource(R.string.shizuku_install))
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenGitHub) {
                Text(stringResource(R.string.shizuku_install_github))
            }
        }
    )
}

@Composable
fun ShizukuNotRunningDialog(
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.shizuku_not_running_title)) },
        text = {
            Text(
                stringResource(R.string.shizuku_not_running_body),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onOpenApp) {
                Text(stringResource(R.string.shizuku_open_app))
            }
        },
        dismissButton = {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.shizuku_retry))
            }
        }
    )
}

fun openShizukuPlayStore(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api"))
        webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(webIntent)
    }
}

fun openShizukuGitHub(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases"))
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

fun openShizukuApp(context: Context) {
    val intent = context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
    if (intent != null) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
