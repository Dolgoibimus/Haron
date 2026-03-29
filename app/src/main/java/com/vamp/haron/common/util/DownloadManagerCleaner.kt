package com.vamp.haron.common.util

import android.app.DownloadManager
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants

/**
 * Fixes Android DownloadManager orphan cleanup bug (AOSP Issue #148573846).
 *
 * When a file is moved out of Download/ by a third-party file manager,
 * DownloadIdleService.cleanOrphans() sees the file as missing and deletes
 * the DB record — or on Xiaomi/HyperOS, may delete the file at its new location.
 *
 * Solution: after moving a file from Download/, remove the DownloadManager record
 * (the file is already at the new path) and update MediaStore.
 */
object DownloadManagerCleaner {

    private const val SUB = "DLCleaner"

    private val DOWNLOAD_DIR = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS
    ).absolutePath

    /**
     * Call after successfully moving files out of Download/.
     * Removes DownloadManager records for moved files and updates MediaStore.
     *
     * @param context application context
     * @param movedFiles list of pairs (oldPath, newPath) for files that were moved
     */
    fun cleanAfterMove(context: Context, movedFiles: List<Pair<String, String>>) {
        val fromDownload = movedFiles.filter { (old, _) ->
            old.startsWith(DOWNLOAD_DIR, ignoreCase = true)
        }
        if (fromDownload.isEmpty()) return

        EcosystemLogger.d(HaronConstants.TAG, "$SUB: cleaning ${fromDownload.size} moved files from Download/")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm != null) {
            removeDownloadRecords(dm, fromDownload.map { it.first })
        }

        updateMediaStore(context, fromDownload)
    }

    /**
     * Remove DownloadManager DB records for files that no longer exist at old path.
     * DownloadManager.remove(id) deletes the record but NOT the file (already moved).
     */
    private fun removeDownloadRecords(dm: DownloadManager, oldPaths: List<String>) {
        for (oldPath in oldPaths) {
            try {
                val query = DownloadManager.Query()
                val cursor = dm.query(query) ?: continue
                cursor.use {
                    val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
                    val pathCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    val fileCol = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_FILENAME)
                    while (it.moveToNext()) {
                        val localUri = if (pathCol >= 0) it.getString(pathCol) else null
                        val localFile = if (fileCol >= 0) {
                            try { it.getString(fileCol) } catch (_: Exception) { null }
                        } else null

                        val matchesUri = localUri != null && (
                            localUri == "file://$oldPath" ||
                            localUri.endsWith(oldPath.substringAfterLast("/"))
                        )
                        val matchesFile = localFile != null && localFile == oldPath

                        if (matchesUri || matchesFile) {
                            val id = it.getLong(idCol)
                            dm.remove(id)
                            EcosystemLogger.d(HaronConstants.TAG, "$SUB: removed DM record id=$id for $oldPath")
                        }
                    }
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "$SUB: error cleaning DM record for $oldPath: ${e.message}")
            }
        }
    }

    /**
     * Notify MediaStore: delete old entry, scan new path.
     */
    private fun updateMediaStore(context: Context, movedFiles: List<Pair<String, String>>) {
        val oldPaths = movedFiles.map { it.first }.toTypedArray()
        val newPaths = movedFiles.map { it.second }.toTypedArray()

        // Delete old MediaStore entries
        for (oldPath in oldPaths) {
            try {
                val uri = android.provider.MediaStore.Files.getContentUri("external")
                val deleted = context.contentResolver.delete(
                    uri,
                    "${android.provider.MediaStore.Files.FileColumns.DATA} = ?",
                    arrayOf(oldPath)
                )
                if (deleted > 0) {
                    EcosystemLogger.d(HaronConstants.TAG, "$SUB: deleted MediaStore entry for $oldPath")
                }
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "$SUB: MediaStore delete error: ${e.message}")
            }
        }

        // Scan new paths so MediaStore picks them up
        MediaScannerConnection.scanFile(context, newPaths, null) { path, uri ->
            EcosystemLogger.d(HaronConstants.TAG, "$SUB: scanned $path → $uri")
        }
    }
}
