package com.vamp.haron.common.util

import android.webkit.MimeTypeMap
import com.vamp.haron.domain.model.FileEntry
import java.io.File

fun File.toFileEntry(): FileEntry {
    val children = if (isDirectory) listFiles()?.size ?: 0 else 0
    return FileEntry(
        name = name,
        path = absolutePath,
        isDirectory = isDirectory,
        size = if (isDirectory) 0L else length(),
        lastModified = lastModified(),
        extension = extension.lowercase(),
        isHidden = isHidden,
        childCount = children
    )
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
        extension in listOf("doc", "docx", "odt", "rtf") -> "document"
        extension in listOf("xls", "xlsx", "ods", "csv") -> "spreadsheet"
        extension in listOf("ppt", "pptx", "odp") -> "presentation"
        extension in listOf("zip", "rar", "7z", "tar", "gz", "bz2") -> "archive"
        extension in listOf("apk") -> "apk"
        extension in listOf(
            "txt", "md", "log", "json", "xml", "yml", "yaml",
            "conf", "cfg", "ini", "properties", "env", "toml",
            "fb2", "csv", "sql", "gradle"
        ) -> "text"
        extension in listOf(
            "kt", "java", "py", "js", "ts", "html", "css",
            "sh", "bat", "c", "cpp", "h", "hpp", "rs", "go",
            "rb", "php", "swift", "dart", "lua", "r", "scala"
        ) -> "code"
        else -> "file"
    }
}
