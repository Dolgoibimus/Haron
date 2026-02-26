package com.vamp.haron.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File

object TransferHolder {
    var selectedFiles: List<File> = emptyList()
    val pendingNavigationPath = MutableStateFlow<String?>(null)
}
