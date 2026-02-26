package com.vamp.haron.domain.model

import java.io.File
import java.util.UUID

data class TransferSession(
    val id: String = UUID.randomUUID().toString(),
    val files: List<File>,
    val protocol: TransferProtocol,
    val state: TransferState = TransferState.IDLE,
    val progress: TransferProgressInfo = TransferProgressInfo(),
    val targetDevice: DiscoveredDevice? = null,
    val error: String? = null
)
