package com.vamp.haron.domain.model

/**
 * Static holder for passing large data to FileOperationService.
 * Intent extras are limited to ~500KB, so lists of paths go through this holder.
 * Data is consumed once by the service and cleared.
 */
object OperationHolder {
    // Delete
    var deletePaths: List<String> = emptyList()

    // Archive create
    var archivePaths: List<String> = emptyList()
    var archiveOutputPath: String = ""
    var archivePassword: String? = null
    var archiveSplitSizeMb: Int = 0

    // Extract
    var extractArchivePath: String = ""
    var extractDestDir: String = ""
    var extractSelectedEntries: Set<String>? = null
    var extractPassword: String? = null
    var extractBasePrefix: String = ""

    fun clearDelete() {
        deletePaths = emptyList()
    }

    fun clearArchive() {
        archivePaths = emptyList()
        archiveOutputPath = ""
        archivePassword = null
        archiveSplitSizeMb = 0
    }

    fun clearExtract() {
        extractArchivePath = ""
        extractDestDir = ""
        extractSelectedEntries = null
        extractPassword = null
        extractBasePrefix = ""
    }
}
