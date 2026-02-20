package com.vamp.haron.data.saf

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.vamp.haron.domain.model.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafFileOperations @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Fast listing via ContentResolver.query() — much faster than DocumentFile.listFiles()
     * for large directories.
     */
    fun listFiles(treeUri: Uri): Result<List<FileEntry>> = runCatching {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val entries = mutableListOf<FileEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val mimeType = cursor.getString(mimeIdx) ?: ""
                val size = cursor.getLongOrNull(sizeIdx) ?: 0L
                val lastModified = cursor.getLongOrNull(modIdx) ?: 0L
                val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                val ext = if (!isDir) name.substringAfterLast('.', "").lowercase() else ""

                val childCount = if (isDir) countChildren(treeUri, childDocId) else 0

                entries.add(
                    FileEntry(
                        name = name,
                        path = childUri.toString(),
                        isDirectory = isDir,
                        size = if (isDir) 0L else size,
                        lastModified = lastModified,
                        extension = ext,
                        isHidden = name.startsWith("."),
                        childCount = childCount,
                        isContentUri = true
                    )
                )
            }
        }
        entries
    }

    /**
     * List files for a specific document URI within a tree.
     */
    fun listFilesForDocument(treeUri: Uri, documentUri: Uri): Result<List<FileEntry>> = runCatching {
        val docId = DocumentsContract.getDocumentId(documentUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        val entries = mutableListOf<FileEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
            val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                val childDocId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                val mimeType = cursor.getString(mimeIdx) ?: ""
                val size = cursor.getLongOrNull(sizeIdx) ?: 0L
                val lastModified = cursor.getLongOrNull(modIdx) ?: 0L
                val isDir = mimeType == DocumentsContract.Document.MIME_TYPE_DIR

                val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocId)
                val ext = if (!isDir) name.substringAfterLast('.', "").lowercase() else ""

                val childCount = if (isDir) countChildren(treeUri, childDocId) else 0

                entries.add(
                    FileEntry(
                        name = name,
                        path = childUri.toString(),
                        isDirectory = isDir,
                        size = if (isDir) 0L else size,
                        lastModified = lastModified,
                        extension = ext,
                        isHidden = name.startsWith("."),
                        childCount = childCount,
                        isContentUri = true
                    )
                )
            }
        }
        entries
    }

    fun copyFileFromSaf(sourceUri: Uri, destFile: java.io.File): Boolean {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun copyFileToSaf(sourceFile: java.io.File, destParentUri: Uri, name: String): Boolean {
        return try {
            val mimeType = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(sourceFile.extension) ?: "application/octet-stream"
            val parent = DocumentFile.fromTreeUri(context, destParentUri) ?: return false
            val destDoc = parent.createFile(mimeType, name) ?: return false
            context.contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun copySafToSaf(sourceUri: Uri, destParentUri: Uri, name: String, mimeType: String): Boolean {
        return try {
            val parent = DocumentFile.fromTreeUri(context, destParentUri) ?: return false
            val destDoc = parent.createFile(mimeType, name) ?: return false
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                context.contentResolver.openOutputStream(destDoc.uri)?.use { output ->
                    input.copyTo(output)
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun deleteFile(uri: Uri): Boolean {
        return try {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } catch (_: Exception) {
            false
        }
    }

    fun renameFile(uri: Uri, newName: String): Uri? {
        return try {
            DocumentsContract.renameDocument(context.contentResolver, uri, newName)
        } catch (_: Exception) {
            null
        }
    }

    fun createDirectory(parentUri: Uri, name: String): Uri? {
        return try {
            val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return null
            parent.createDirectory(name)?.uri
        } catch (_: Exception) {
            null
        }
    }

    fun createFile(parentUri: Uri, name: String, mimeType: String = "application/octet-stream"): Uri? {
        return try {
            val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return null
            parent.createFile(mimeType, name)?.uri
        } catch (_: Exception) {
            null
        }
    }

    fun findFile(parentUri: Uri, name: String): DocumentFile? {
        val parent = DocumentFile.fromTreeUri(context, parentUri) ?: return null
        return parent.findFile(name)
    }

    fun getParentUri(uri: Uri): Uri? {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val parts = docId.split(":")
            if (parts.size < 2) return null
            val path = parts[1]
            val parentPath = path.substringBeforeLast('/', "")
            if (parentPath.isEmpty()) return null
            val parentDocId = "${parts[0]}:$parentPath"
            // Find the tree URI from the persisted URIs
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val treeUri = DocumentsContract.buildTreeDocumentUri(uri.authority, treeDocId)
            DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract the tree URI root from a document URI within that tree.
     */
    fun getTreeUriForDocument(documentUri: Uri): Uri? {
        return try {
            val treeDocId = DocumentsContract.getTreeDocumentId(documentUri)
            DocumentsContract.buildTreeDocumentUri(documentUri.authority, treeDocId)
        } catch (_: Exception) {
            null
        }
    }

    fun isTreeRoot(uri: Uri): Boolean {
        return try {
            val docId = DocumentsContract.getDocumentId(uri)
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            docId == treeDocId
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Count children of a directory via lightweight query (only fetches COLUMN_DOCUMENT_ID).
     */
    private fun countChildren(treeUri: Uri, dirDocId: String): Int {
        return try {
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, dirDocId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { it.count } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun Cursor.getLongOrNull(index: Int): Long? {
        return if (index >= 0 && !isNull(index)) getLong(index) else null
    }
}
