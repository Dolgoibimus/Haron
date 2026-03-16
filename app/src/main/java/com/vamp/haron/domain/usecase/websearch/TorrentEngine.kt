package com.vamp.haron.domain.usecase.websearch

import android.content.Context
import android.os.Environment
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.libtorrent4j.AlertListener
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.alerts.AddTorrentAlert
import org.libtorrent4j.alerts.Alert
import org.libtorrent4j.alerts.AlertType
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class TorrentProgress(
    val name: String,
    val progress: Float,
    val downloadSpeed: Long,
    val totalSize: Long,
    val isComplete: Boolean,
    val error: String? = null
)

@Singleton
class TorrentEngine @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var session: SessionManager? = null
    private val lock = Any()

    val savePath: String
        get() {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "Haron"
            )
            if (!dir.exists()) dir.mkdirs()
            return dir.absolutePath
        }

    fun start() {
        synchronized(lock) {
            if (session != null) return
            EcosystemLogger.d(HaronConstants.TAG, "TorrentEngine: starting session")
            try {
                val settings = SettingsPack()
                // ZERO upload — do not seed
                settings.uploadRateLimit(0)
                // Do not listen for incoming connections
                settings.setString(
                    settings_pack.string_types.listen_interfaces.swigValue(),
                    ""
                )
                // Minimal DHT for magnet resolution
                settings.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)

                val params = SessionParams(settings)
                val sm = SessionManager(false)
                sm.start(params)
                session = sm
                EcosystemLogger.i(HaronConstants.TAG, "TorrentEngine: session started, upload=0")
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TorrentEngine: failed to start: ${e.message}")
            }
        }
    }

    fun download(magnetUri: String): Flow<TorrentProgress> = flow {
        start()
        val sm = session ?: run {
            emit(TorrentProgress("", 0f, 0, 0, false, "Engine not started"))
            return@flow
        }

        EcosystemLogger.d(HaronConstants.TAG, "TorrentEngine: fetching magnet: ${magnetUri.take(80)}")

        // Resolve magnet to torrent info (blocking, up to 60s)
        val data: ByteArray? = try {
            sm.fetchMagnet(magnetUri, 60, File(savePath))
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "TorrentEngine: fetchMagnet failed: ${e.message}")
            null
        }

        if (data == null) {
            EcosystemLogger.e(HaronConstants.TAG, "TorrentEngine: timeout resolving magnet")
            emit(TorrentProgress("", 0f, 0, 0, false, "Timeout resolving magnet"))
            return@flow
        }

        val ti = TorrentInfo(data)
        val name = ti.name()
        EcosystemLogger.d(HaronConstants.TAG, "TorrentEngine: resolved name=$name, size=${ti.totalSize()}")

        // Start download using the resolved TorrentInfo
        sm.download(ti, File(savePath))

        // Wait for handle to appear
        var handle: TorrentHandle? = null
        for (i in 0 until 30) {
            delay(500)
            handle = sm.find(ti.infoHash())
            if (handle != null && handle.isValid) break
            handle = null
        }

        if (handle == null) {
            EcosystemLogger.e(HaronConstants.TAG, "TorrentEngine: no handle after download")
            emit(TorrentProgress(name, 0f, 0, ti.totalSize(), false, "Failed to start download"))
            return@flow
        }

        // Monitor progress
        var lastLoggedPercent = -1
        while (true) {
            val status = handle.status()
            val progress = status.progress()
            val dlSpeed = status.downloadRate().toLong()
            val totalSize = status.totalWanted()
            val isFinished = status.isFinished || progress >= 1.0f

            val percent = (progress * 100).toInt()
            if (percent != lastLoggedPercent) {
                EcosystemLogger.d(HaronConstants.TAG, "TorrentEngine: $name ${percent}% speed=${dlSpeed / 1024}KB/s")
                lastLoggedPercent = percent
            }

            emit(
                TorrentProgress(
                    name = name,
                    progress = progress,
                    downloadSpeed = dlSpeed,
                    totalSize = totalSize,
                    isComplete = isFinished
                )
            )

            if (isFinished) {
                EcosystemLogger.i(HaronConstants.TAG, "TorrentEngine: $name download complete")
                break
            }
            delay(500)
        }
    }.flowOn(Dispatchers.IO)

    fun stop() {
        synchronized(lock) {
            EcosystemLogger.d(HaronConstants.TAG, "TorrentEngine: stopping session")
            try {
                session?.stop()
            } catch (e: Exception) {
                EcosystemLogger.e(HaronConstants.TAG, "TorrentEngine: stop error: ${e.message}")
            }
            session = null
        }
    }
}
