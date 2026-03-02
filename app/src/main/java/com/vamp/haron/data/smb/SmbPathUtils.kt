package com.vamp.haron.data.smb

import com.vamp.haron.common.constants.HaronConstants

object SmbPathUtils {

    fun isSmbPath(path: String): Boolean =
        path.startsWith(HaronConstants.SMB_PREFIX, ignoreCase = true)

    /** smb://192.168.1.5/Share/Docs → 192.168.1.5 */
    fun parseHost(path: String): String {
        val stripped = path.removePrefix(HaronConstants.SMB_PREFIX)
        return stripped.substringBefore("/")
    }

    /** smb://192.168.1.5/Share/Docs → Share (empty if at server level) */
    fun parseShare(path: String): String {
        val stripped = path.removePrefix(HaronConstants.SMB_PREFIX)
        val afterHost = stripped.substringAfter("/", "")
        return afterHost.substringBefore("/")
    }

    /** smb://192.168.1.5/Share/Docs/Sub → Docs/Sub (empty if at share root) */
    fun parseRelativePath(path: String): String {
        val stripped = path.removePrefix(HaronConstants.SMB_PREFIX)
        val parts = stripped.split("/", limit = 3)
        return if (parts.size > 2) parts[2] else ""
    }

    fun buildPath(host: String, share: String = "", relPath: String = ""): String {
        val sb = StringBuilder(HaronConstants.SMB_PREFIX).append(host)
        if (share.isNotEmpty()) {
            sb.append("/").append(share)
            if (relPath.isNotEmpty()) {
                sb.append("/").append(relPath)
            }
        }
        return sb.toString()
    }

    fun getParentPath(path: String): String {
        val stripped = path.removePrefix(HaronConstants.SMB_PREFIX)
        val lastSlash = stripped.lastIndexOf("/")
        if (lastSlash <= 0) return HaronConstants.SMB_PREFIX + stripped.substringBefore("/")
        return HaronConstants.SMB_PREFIX + stripped.substring(0, lastSlash)
    }

    fun getFileName(path: String): String {
        val stripped = path.trimEnd('/')
        return stripped.substringAfterLast("/")
    }
}
