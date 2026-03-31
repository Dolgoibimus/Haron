package com.vamp.haron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.R
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.transfer.SimpleHttpServer
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicInteger

/**
 * Captures device screen via MediaProjection and streams as JPEG/MJPEG over HTTP.
 * Used for Chromecast screen mirroring. Runs SimpleHttpServer on port 8090+.
 */
class ScreenMirrorService : Service() {

    companion object {
        const val CHANNEL_ID = "haron_screen_mirror"
        const val NOTIFICATION_ID = 2001
        const val ACTION_START = "com.vamp.haron.SCREEN_MIRROR_START"
        const val ACTION_STOP = "com.vamp.haron.SCREEN_MIRROR_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"

        var serverUrl: String? = null
            private set
        var isRunning: Boolean = false
            private set
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var imageThread: HandlerThread? = null
    private var httpServer: SimpleHttpServer? = null
    @Volatile private var lastFrameBytes: ByteArray? = null
    private var actualPort: Int = 0
    private val activeClients = AtomicInteger(0)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())

        EcosystemLogger.d("CastFlow", "ScreenMirrorService.onStartCommand: action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
                val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(EXTRA_RESULT_DATA)
                }

                if (resultCode != Int.MIN_VALUE && resultData != null) {
                    startMirroring(resultCode, resultData)
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopMirroring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            else -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun startMirroring(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getMetrics(metrics)

        val width = (metrics.widthPixels / 2).coerceAtMost(720)
        val height = (metrics.heightPixels / 2).coerceAtMost(1280)
        val density = metrics.densityDpi / 2

        imageReader = ImageReader.newInstance(width, height, android.graphics.PixelFormat.RGBA_8888, 2)

        imageThread = HandlerThread("ImageReader").also { it.start() }
        val imageHandler = Handler(imageThread!!.looper)

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (activeClients.get() == 0) return@setOnImageAvailableListener

                val plane = image.planes[0]
                val buffer = plane.buffer
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * width
                val bitmap = Bitmap.createBitmap(
                    width + rowPadding / pixelStride, height,
                    Bitmap.Config.ARGB_8888
                )
                bitmap.copyPixelsFromBuffer(buffer)
                val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                if (cropped !== bitmap) bitmap.recycle()

                val stream = ByteArrayOutputStream()
                cropped.compress(Bitmap.CompressFormat.JPEG, 50, stream)
                cropped.recycle()
                lastFrameBytes = stream.toByteArray()
            } finally {
                image.close()
            }
        }, imageHandler)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    stopMirroring()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))
        }

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "HaronMirror",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null, null
        )

        startHttpServer()
        Companion.isRunning = true
        EcosystemLogger.d(HaronConstants.TAG, "Screen mirror started: ${width}x${height}")
    }

    private fun startHttpServer() {
        val port = findAvailablePort()
        actualPort = port

        val srv = SimpleHttpServer(port)

        srv.addRoute("GET", "/mirror") { _, resp ->
            val html = """<!DOCTYPE html>
<html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>Haron Screen Mirror</title>
<style>body{margin:0;background:#000;display:flex;justify-content:center;align-items:center;min-height:100vh}img{max-width:100%;max-height:100vh}</style>
</head><body><img id="frame" src="/frame"/>
<script>var img=document.getElementById('frame');function refresh(){var next=new Image();next.onload=function(){img.src=next.src;setTimeout(refresh,200);};next.onerror=function(){setTimeout(refresh,500);};next.src='/frame?t='+Date.now();}refresh();</script>
</body></html>"""
            resp.respondHtml(html)
        }

        srv.addRoute("GET", "/frame") { _, resp ->
            activeClients.incrementAndGet()
            try {
                val frame = lastFrameBytes
                if (frame != null) {
                    resp.respondBytes(frame, "image/jpeg")
                } else {
                    resp.respondText("No frame", statusCode = 503)
                }
            } finally {
                activeClients.decrementAndGet()
            }
        }

        srv.addRoute("GET", "/mjpeg") { _, resp ->
            activeClients.incrementAndGet()
            try {
                resp.respondStream(
                    contentType = "multipart/x-mixed-replace; boundary=frame",
                    totalSize = -1
                ) { output ->
                    while (Companion.isRunning) {
                        val frame = lastFrameBytes
                        if (frame != null) {
                            val header = "--frame\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n"
                            output.write(header.toByteArray())
                            output.write(frame)
                            output.write("\r\n".toByteArray())
                            output.flush()
                        }
                        Thread.sleep(500)
                    }
                }
            } finally {
                activeClients.decrementAndGet()
            }
        }

        srv.start()
        httpServer = srv
        serverUrl = "http://${getLocalIp()}:$port/mirror"
        EcosystemLogger.d(HaronConstants.TAG, "Mirror HTTP server on port $port")
    }

    private fun stopMirroring() {
        httpServer?.stop()
        httpServer = null
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        imageThread?.quitSafely()
        imageThread = null
        mediaProjection?.stop()
        mediaProjection = null
        lastFrameBytes = null
        serverUrl = null
        Companion.isRunning = false
        EcosystemLogger.d(HaronConstants.TAG, "Screen mirror stopped")
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.cast_mirror_notification_title))
            .setContentText(getString(R.string.cast_mirror_notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.cast_mirror_channel_name),
            NotificationManager.IMPORTANCE_LOW
        )
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun findAvailablePort(): Int {
        for (port in 8090..8095) {
            try { java.net.ServerSocket(port).use { return port } } catch (_: Exception) {}
        }
        java.net.ServerSocket(0).use { return it.localPort }
    }

    private fun getLocalIp(): String {
        try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
        } catch (_: Exception) {}
        return "127.0.0.1"
    }

    override fun onDestroy() {
        stopMirroring()
        super.onDestroy()
    }
}
