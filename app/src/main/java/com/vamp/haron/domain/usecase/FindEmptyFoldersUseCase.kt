package com.vamp.haron.domain.usecase

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

class FindEmptyFoldersUseCase @Inject constructor() {

    operator fun invoke(rootPath: String, recursive: Boolean): Flow<List<String>> = flow {
        val root = File(rootPath)
        if (!root.isDirectory) {
            emit(emptyList())
            return@flow
        }

        val result = mutableListOf<String>()
        if (recursive) {
            root.walkBottomUp()
                .filter { it.isDirectory && it != root }
                .forEach { dir ->
                    if (isEffectivelyEmpty(dir)) {
                        result.add(dir.absolutePath)
                    }
                }
        } else {
            root.listFiles()
                ?.filter { it.isDirectory }
                ?.forEach { dir ->
                    if (isEffectivelyEmpty(dir)) {
                        result.add(dir.absolutePath)
                    }
                }
        }
        emit(result.sorted())
    }.flowOn(Dispatchers.IO)

    /**
     * Folder is "effectively empty" if it has no children,
     * or only hidden files (.nomedia, .thumbnails, etc.) and no subdirectories.
     */
    private fun isEffectivelyEmpty(dir: File): Boolean {
        val children = dir.listFiles() ?: return true
        if (children.isEmpty()) return true
        if (children.any { it.isDirectory }) return false
        return children.all { it.name.startsWith('.') }
    }
}
