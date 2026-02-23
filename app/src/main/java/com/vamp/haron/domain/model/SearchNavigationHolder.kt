package com.vamp.haron.domain.model

/**
 * Static holder for passing navigation target from SearchScreen
 * back to ExplorerScreen (navigate to file's parent directory and highlight file).
 */
object SearchNavigationHolder {
    /** Full path of the file to highlight/scroll to in file list */
    var targetFilePath: String? = null
    /** Parent folder to navigate to */
    var targetParentPath: String? = null
}
