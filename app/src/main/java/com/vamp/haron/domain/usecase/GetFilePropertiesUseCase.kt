package com.vamp.haron.domain.usecase

import android.content.Context
import androidx.exifinterface.media.ExifInterface
import com.vamp.haron.common.util.iconRes
import com.vamp.haron.common.util.mimeType
import com.vamp.haron.domain.model.FileEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import javax.inject.Inject

data class FileProperties(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val mimeType: String,
    val isDirectory: Boolean,
    val childCount: Int = 0,
    val totalSize: Long = 0L,
    val exifData: Map<String, String> = emptyMap(),
    val permissions: String = ""
)

class GetFilePropertiesUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(entry: FileEntry): Flow<FileProperties> = flow {
        val file = File(entry.path)
        val mime = entry.mimeType()

        // Basic properties — emit fast
        val permissions = buildPermissionsString(file)
        val base = FileProperties(
            name = entry.name,
            path = entry.path,
            size = entry.size,
            lastModified = entry.lastModified,
            mimeType = mime,
            isDirectory = entry.isDirectory,
            childCount = entry.childCount,
            totalSize = entry.size,
            permissions = permissions
        )
        emit(base)

        // For directories — recursive size calculation
        if (entry.isDirectory) {
            var totalSize = 0L
            var totalFiles = 0
            file.walkTopDown().forEach { f ->
                if (f.isFile) {
                    totalSize += f.length()
                    totalFiles++
                }
            }
            emit(base.copy(totalSize = totalSize, childCount = totalFiles))
            return@flow
        }

        // For images — read EXIF
        if (entry.iconRes() == "image" && !entry.isContentUri) {
            try {
                val exif = ExifInterface(entry.path)
                val data = buildExifMap(exif)
                emit(base.copy(exifData = data))
            } catch (_: Exception) {
                // No EXIF or unreadable
            }
        }
    }.flowOn(Dispatchers.IO)

    fun removeExif(path: String): Boolean {
        return try {
            val exif = ExifInterface(path)
            EXIF_TAGS.forEach { tag ->
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun buildPermissionsString(file: File): String {
        val sb = StringBuilder()
        sb.append(if (file.canRead()) "r" else "-")
        sb.append(if (file.canWrite()) "w" else "-")
        sb.append(if (file.canExecute()) "x" else "-")
        return sb.toString()
    }

    private fun buildExifMap(exif: ExifInterface): Map<String, String> {
        val map = linkedMapOf<String, String>()
        exif.getAttribute(ExifInterface.TAG_MAKE)?.let { map["Производитель"] = it }
        exif.getAttribute(ExifInterface.TAG_MODEL)?.let { map["Модель камеры"] = it }
        exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)?.let { map["Дата съёмки"] = it }
        exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH)?.let { w ->
            exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH)?.let { h ->
                map["Размер изображения"] = "${w}x${h}"
            }
        }
        exif.getAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS)?.let { map["ISO"] = it }
        exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME)?.let { map["Выдержка"] = it }
        exif.getAttribute(ExifInterface.TAG_F_NUMBER)?.let { map["Диафрагма"] = "f/$it" }
        exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH)?.let { map["Фокусное расстояние"] = it }
        exif.getAttribute(ExifInterface.TAG_WHITE_BALANCE)?.let {
            map["Баланс белого"] = if (it == "0") "Авто" else "Ручной"
        }
        exif.getAttribute(ExifInterface.TAG_FLASH)?.let { map["Вспышка"] = it }
        exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE)?.let { lat ->
            exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE)?.let { lon ->
                val latRef = exif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) ?: ""
                val lonRef = exif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) ?: ""
                map["GPS"] = "$lat $latRef, $lon $lonRef"
            }
        }
        exif.getAttribute(ExifInterface.TAG_SOFTWARE)?.let { map["ПО"] = it }
        return map
    }

    companion object {
        private val EXIF_TAGS = listOf(
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_IMAGE_WIDTH, ExifInterface.TAG_IMAGE_LENGTH,
            ExifInterface.TAG_ISO_SPEED_RATINGS, ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_F_NUMBER, ExifInterface.TAG_FOCAL_LENGTH,
            ExifInterface.TAG_WHITE_BALANCE, ExifInterface.TAG_FLASH,
            ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT, ExifInterface.TAG_USER_COMMENT
        )
    }
}
