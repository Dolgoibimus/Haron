package com.vamp.haron.data.ftp

import com.vamp.haron.common.constants.HaronConstants

object FtpPathUtils {

    fun isFtpPath(path: String): Boolean =
        path.startsWith(HaronConstants.FTP_PREFIX, ignoreCase = true) ||
            path.startsWith(HaronConstants.FTPS_PREFIX, ignoreCase = true)

    /** ftp://192.168.1.5:21/docs/file.txt → 192.168.1.5 */
    fun parseHost(path: String): String {
        val stripped = stripPrefix(path)
        val hostPort = stripped.substringBefore("/")
        return hostPort.substringBefore(":")
    }

    /** ftp://192.168.1.5:2121/docs → 2121 (default 21) */
    fun parsePort(path: String): Int {
        val stripped = stripPrefix(path)
        val hostPort = stripped.substringBefore("/")
        return if (":" in hostPort) {
            hostPort.substringAfter(":").toIntOrNull() ?: HaronConstants.FTP_DEFAULT_PORT
        } else {
            HaronConstants.FTP_DEFAULT_PORT
        }
    }

    /** ftp://192.168.1.5:21/docs/sub → /docs/sub (empty if at root) */
    fun parseRelativePath(path: String): String {
        val stripped = stripPrefix(path)
        val afterHost = stripped.substringAfter("/", "")
        return if (afterHost.isEmpty()) "/" else "/$afterHost"
    }

    fun buildPath(host: String, port: Int = HaronConstants.FTP_DEFAULT_PORT, relPath: String = "/"): String {
        val prefix = HaronConstants.FTP_PREFIX
        val sb = StringBuilder(prefix).append(host)
        if (port != HaronConstants.FTP_DEFAULT_PORT) {
            sb.append(":").append(port)
        }
        if (relPath.isNotEmpty() && relPath != "/") {
            val normalized = if (relPath.startsWith("/")) relPath.substring(1) else relPath
            sb.append("/").append(normalized)
        }
        return sb.toString()
    }

    fun getParentPath(path: String): String {
        val host = parseHost(path)
        val port = parsePort(path)
        val relPath = parseRelativePath(path)
        if (relPath == "/" || relPath.isEmpty()) return buildPath(host, port, "/")
        val parent = relPath.substringBeforeLast("/")
        return buildPath(host, port, parent.ifEmpty { "/" })
    }

    fun getFileName(path: String): String {
        val stripped = path.trimEnd('/')
        return stripped.substringAfterLast("/")
    }

    fun isRoot(path: String): Boolean {
        val relPath = parseRelativePath(path)
        return relPath == "/" || relPath.isEmpty()
    }

    fun connectionKey(host: String, port: Int): String = "$host:$port"

    private fun stripPrefix(path: String): String =
        path.removePrefix(HaronConstants.FTPS_PREFIX)
            .removePrefix(HaronConstants.FTP_PREFIX)
}
