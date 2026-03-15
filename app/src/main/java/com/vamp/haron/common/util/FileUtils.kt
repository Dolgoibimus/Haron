package com.vamp.haron.common.util

import android.webkit.MimeTypeMap
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.domain.model.FileEntry
import java.io.File

fun File.toFileEntry(): FileEntry {
    val children = if (isDirectory) listFiles()?.size ?: 0 else 0
    val displayName = if (parentFile?.name == HaronConstants.TRASH_DIR_NAME) {
        stripTrashPrefix(name)
    } else {
        name
    }
    return FileEntry(
        name = displayName,
        path = absolutePath,
        isDirectory = isDirectory,
        size = if (isDirectory) 0L else length(),
        lastModified = lastModified(),
        extension = displayName.substringAfterLast('.', "").lowercase(),
        isHidden = isHidden,
        childCount = children
    )
}

/** Strip timestamp prefix from trash file name: "1773087338091_file.txt" -> "file.txt" */
private fun stripTrashPrefix(name: String): String {
    val idx = name.indexOf('_')
    if (idx > 0 && name.substring(0, idx).toLongOrNull() != null) {
        return name.substring(idx + 1)
    }
    return name
}

fun FileEntry.mimeType(): String {
    if (isDirectory) return "inode/directory"
    return MimeTypeMap.getSingleton()
        .getMimeTypeFromExtension(extension) ?: "application/octet-stream"
}

fun FileEntry.iconRes(): String {
    if (isDirectory) return "folder"
    return when {
        extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "svg") -> "image"
        extension in listOf("mp4", "avi", "mkv", "mov", "wmv", "flv", "webm", "3gp", "3gpp", "ts", "m4v", "mts") -> "video"
        extension in listOf("mp3", "wav", "flac", "aac", "ogg", "m4a", "wma", "opus") -> "audio"
        extension in listOf("pdf") -> "pdf"
        extension in listOf("doc", "docx", "odt", "rtf", "fb2") -> "document"
        extension in listOf("xls", "xlsx", "ods", "csv") -> "spreadsheet"
        extension in listOf("ppt", "pptx", "odp") -> "presentation"
        extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz2", "txz", "gtar") -> "archive"
        extension in listOf("apk") -> "apk"
        extension in listOf(
            "txt", "md", "log", "json", "xml", "yml", "yaml",
            "conf", "cfg", "ini", "properties", "env", "toml",
            "csv", "sql", "gradle"
        ) -> "text"
        extension in listOf(
            "kt", "java", "py", "js", "ts", "html", "css",
            "sh", "bat", "c", "cpp", "h", "hpp", "rs", "go",
            "rb", "php", "swift", "dart", "lua", "r", "scala"
        ) -> "code"
        else -> "file"
    }
}
