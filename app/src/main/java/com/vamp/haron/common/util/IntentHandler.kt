package com.vamp.haron.common.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import java.io.File
import java.io.FileOutputStream

data class ReceivedFile(
    val displayName: String,
    val localPath: String,
    val mimeType: String?,
    val size: Long
)

object IntentHandler {

    private const val TAG = "IntentHandler"

    fun handleIntent(intent: Intent, context: Context): List<ReceivedFile> {
        return when (intent.action) {
            Intent.ACTION_VIEW -> handleActionView(intent, context)
            Intent.ACTION_SEND -> handleActionSend(intent, context)
            Intent.ACTION_SEND_MULTIPLE -> handleActionSendMultiple(intent, context)
            else -> emptyList()
        }
    }

    private fun handleActionView(intent: Intent, context: Context): List<ReceivedFile> {
        val uri = intent.data ?: return emptyList()
        val file = resolveUri(uri, intent.type, context) ?: return emptyList()
        return listOf(file)
    }

    @Suppress("DEPRECATION")
    private fun handleActionSend(intent: Intent, context: Context): List<ReceivedFile> {
        val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) ?: intent.data
        ?: return emptyList()
        val file = resolveUri(uri, intent.type, context) ?: return emptyList()
        return listOf(file)
    }

    @Suppress("DEPRECATION")
    private fun handleActionSendMultiple(intent: Intent, context: Context): List<ReceivedFile> {
        val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            ?: return emptyList()
        return uris.mapNotNull { resolveUri(it, null, context) }
    }

    private fun resolveUri(uri: Uri, intentMimeType: String?, context: Context): ReceivedFile? {
        return try {
            val cr = context.contentResolver
            val displayName = queryDisplayName(cr, uri) ?: uri.lastPathSegment ?: "unknown"
            val mimeType = intentMimeType ?: cr.getType(uri)
            val cacheDir = File(context.cacheDir, "received")
            cacheDir.mkdirs()
            val targetFile = generateUniqueFile(cacheDir, displayName)

            cr.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            ReceivedFile(
                displayName = displayName,
                localPath = targetFile.absolutePath,
                mimeType = mimeType,
                size = targetFile.length()
            )
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "$TAG resolveUri error: ${e.message}")
            null
        }
    }

    private fun queryDisplayName(cr: ContentResolver, uri: Uri): String? {
        return try {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateUniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        if (!file.exists()) return file
        val baseName = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var counter = 1
        while (file.exists()) {
            val newName = if (ext.isNotEmpty()) "${baseName}_($counter).$ext" else "${baseName}_($counter)"
            file = File(dir, newName)
            counter++
        }
        return file
    }
}
