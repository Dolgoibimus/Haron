package com.vamp.haron.data.torrent

import android.content.Context
import com.vamp.haron.domain.model.TorrentFileInfo
import com.vamp.haron.domain.model.TorrentStreamState
import com.vamp.haron.domain.repository.TorrentStreamRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op stub for Google Play variant. Torrent streaming disabled.
 */
@Singleton
class TorrentStreamRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : TorrentStreamRepository {

    private val _state = MutableStateFlow<TorrentStreamState>(TorrentStreamState.Idle)
    override val state: StateFlow<TorrentStreamState> = _state.asStateFlow()

    override val isAvailable: Boolean = false

    override suspend fun startStream(uri: String, fileIndex: Int) {
        _state.value = TorrentStreamState.Error("Torrent streaming not available in this build")
    }

    override suspend fun getFiles(uri: String): List<TorrentFileInfo> = emptyList()

    override fun stopStream() {
        _state.value = TorrentStreamState.Idle
    }

    override suspend fun cleanCache() {}
}
