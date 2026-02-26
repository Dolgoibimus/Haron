package com.vamp.haron.domain.model

enum class TransferState {
    IDLE,
    DISCOVERING,
    CONNECTING,
    TRANSFERRING,
    PAUSED,
    COMPLETED,
    FAILED
}
