package com.vamp.haron.data.terminal

import java.io.File

class TabCompletionEngine {

    fun complete(input: String, currentDir: String): List<String> {
        val parts = input.split(" ")
        val prefix = parts.lastOrNull() ?: return emptyList()

        if (parts.size <= 1) {
            // Complete command names
            return completeCommands(prefix) + completePaths(prefix, currentDir)
        }

        // Complete file/directory paths
        return completePaths(prefix, currentDir)
    }

    private fun completePaths(prefix: String, currentDir: String): List<String> {
        val base: File
        val namePrefix: String

        if (prefix.contains('/')) {
            val parentPath = prefix.substringBeforeLast('/')
            namePrefix = prefix.substringAfterLast('/')
            base = if (parentPath.startsWith("/")) File(parentPath)
            else File(currentDir, parentPath)
        } else {
            base = File(currentDir)
            namePrefix = prefix
        }

        if (!base.isDirectory) return emptyList()

        return try {
            base.listFiles()
                ?.filter { it.name.startsWith(namePrefix, ignoreCase = true) }
                ?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                ?.take(20)
                ?.map { file ->
                    val suffix = if (file.isDirectory) "/" else ""
                    if (prefix.contains('/')) {
                        val parent = prefix.substringBeforeLast('/')
                        "$parent/${file.name}$suffix"
                    } else {
                        "${file.name}$suffix"
                    }
                } ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun completeCommands(prefix: String): List<String> {
        val builtins = listOf(
            "cd", "pwd", "clear", "cls", "help", "exit",
            "ls", "cat", "cp", "mv", "rm", "mkdir", "rmdir",
            "grep", "find", "wc", "head", "tail", "sort",
            "chmod", "touch", "echo", "df", "du", "ping",
            "whoami", "date", "uname", "id", "ps", "top",
            "tar", "gzip", "gunzip", "ln", "stat", "file",
            "sed", "awk", "cut", "tr", "uniq", "diff",
            "curl", "wget", "env", "export", "which"
        )
        return builtins.filter { it.startsWith(prefix, ignoreCase = true) }.take(15)
    }
}
