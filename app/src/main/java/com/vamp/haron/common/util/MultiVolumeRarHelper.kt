package com.vamp.haron.common.util

import com.vamp.core.logger.EcosystemLogger
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.IArchiveOpenVolumeCallback
import net.sf.sevenzipjbinding.IInStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import java.io.File
import java.io.RandomAccessFile

private const val TAG = "MultiVolumeRar"

/**
 * Helper for multi-volume RAR archives.
 *
 * Supports two naming schemes:
 * - New style: file.part1.rar, file.part2.rar, ...
 * - Old style: file.rar, file.r00, file.r01, ...
 */
object MultiVolumeRarHelper {

    /** Regex for new-style: name.part1.rar, name.part2.rar */
    private val NEW_STYLE_REGEX = Regex("""^(.+)\.part(\d+)\.rar$""", RegexOption.IGNORE_CASE)

    /** Regex for old-style secondary volumes: name.r00, name.r01 */
    private val OLD_STYLE_PART_REGEX = Regex("""^(.+)\.r(\d{2,})$""", RegexOption.IGNORE_CASE)

    /**
     * Check if a file is part of a multi-volume RAR archive.
     */
    fun isMultiVolumeRar(file: File): Boolean {
        val name = file.name
        // New style: .partN.rar where N > 1
        NEW_STYLE_REGEX.matchEntire(name)?.let { match ->
            val partNum = match.groupValues[2].toIntOrNull() ?: return false
            if (partNum > 1) return true
            // part1 — check if part2 exists
            val baseName = match.groupValues[1]
            val part2 = File(file.parentFile, "$baseName.part2.rar")
            return part2.exists()
        }
        // Old style: .r00, .r01, etc. — always a secondary volume
        OLD_STYLE_PART_REGEX.matchEntire(name)?.let {
            return true
        }
        // Old style: .rar — check if .r00 exists (meaning this is the first volume)
        if (name.lowercase().endsWith(".rar")) {
            val baseName = name.substring(0, name.length - 4)
            val r00 = File(file.parentFile, "$baseName.r00")
            if (r00.exists()) return true
            // Also check case-insensitive
            val parent = file.parentFile ?: return false
            return parent.listFiles()?.any {
                it.name.equals("$baseName.r00", ignoreCase = true)
            } == true
        }
        return false
    }

    /**
     * Find the first volume of a multi-volume RAR.
     * Returns the first volume file, or the file itself if not multi-volume.
     */
    fun findFirstVolume(file: File): File {
        val name = file.name
        // New style: name.partN.rar → name.part1.rar
        NEW_STYLE_REGEX.matchEntire(name)?.let { match ->
            val baseName = match.groupValues[1]
            val first = File(file.parentFile, "$baseName.part1.rar")
            if (first.exists()) {
                EcosystemLogger.d(TAG, "findFirstVolume: new-style, first=$first")
                return first
            }
            // Try case-insensitive
            val parent = file.parentFile ?: return file
            parent.listFiles()?.firstOrNull {
                NEW_STYLE_REGEX.matchEntire(it.name)?.let { m ->
                    m.groupValues[1].equals(baseName, ignoreCase = true) &&
                        m.groupValues[2].toIntOrNull() == 1
                } == true
            }?.let { return it }
        }
        // Old style: name.rNN → name.rar
        OLD_STYLE_PART_REGEX.matchEntire(name)?.let { match ->
            val baseName = match.groupValues[1]
            val first = File(file.parentFile, "$baseName.rar")
            if (first.exists()) {
                EcosystemLogger.d(TAG, "findFirstVolume: old-style, first=$first")
                return first
            }
            // Try case-insensitive
            val parent = file.parentFile ?: return file
            parent.listFiles()?.firstOrNull {
                it.name.equals("$baseName.rar", ignoreCase = true)
            }?.let { return it }
        }
        // Already the first volume or single-file
        return file
    }

    /**
     * Count total volumes for a multi-volume RAR.
     */
    fun countVolumes(firstVolume: File): Int {
        val name = firstVolume.name
        val parent = firstVolume.parentFile ?: return 1
        val files = parent.listFiles() ?: return 1

        // New style
        NEW_STYLE_REGEX.matchEntire(name)?.let { match ->
            val baseName = match.groupValues[1]
            return files.count { f ->
                NEW_STYLE_REGEX.matchEntire(f.name)?.let { m ->
                    m.groupValues[1].equals(baseName, ignoreCase = true)
                } == true
            }
        }

        // Old style: .rar + .r00, .r01, ...
        if (name.lowercase().endsWith(".rar")) {
            val baseName = name.substring(0, name.length - 4)
            val parts = files.count { f ->
                OLD_STYLE_PART_REGEX.matchEntire(f.name)?.let { m ->
                    m.groupValues[1].equals(baseName, ignoreCase = true)
                } == true
            }
            return 1 + parts // .rar + .rNN files
        }

        return 1
    }

    /**
     * Check if all volumes are present. Returns list of missing volume names.
     */
    fun findMissingVolumes(firstVolume: File): List<String> {
        val name = firstVolume.name
        val parent = firstVolume.parentFile ?: return emptyList()
        val files = parent.listFiles()?.map { it.name.lowercase() }?.toSet() ?: return emptyList()
        val missing = mutableListOf<String>()

        // New style: check consecutive part numbers
        NEW_STYLE_REGEX.matchEntire(name)?.let { match ->
            val baseName = match.groupValues[1]
            var partNum = 1
            while (true) {
                val expected = "$baseName.part$partNum.rar"
                if (expected.lowercase() !in files) {
                    if (partNum == 1) break // no volumes at all
                    // Check if next part exists (gap detection)
                    val nextExpected = "$baseName.part${partNum + 1}.rar"
                    if (nextExpected.lowercase() in files) {
                        missing.add(expected)
                    } else {
                        break // reached the end
                    }
                }
                partNum++
                if (partNum > 999) break // safety
            }
            return missing
        }

        // Old style: check consecutive .rNN
        if (name.lowercase().endsWith(".rar")) {
            val baseName = name.substring(0, name.length - 4)
            var num = 0
            while (true) {
                val expected = "$baseName.r${num.toString().padStart(2, '0')}"
                if (expected.lowercase() !in files) {
                    val nextExpected = "$baseName.r${(num + 1).toString().padStart(2, '0')}"
                    if (nextExpected.lowercase() in files) {
                        missing.add(expected)
                    } else {
                        break
                    }
                }
                num++
                if (num > 999) break
            }
            return missing
        }

        return emptyList()
    }

    /**
     * Combined IArchiveOpenVolumeCallback + IArchiveOpenCallback for 7-Zip-JBinding.
     * Opens volume files by name as the engine requests them.
     * Must implement both interfaces to be passed to openInArchive().
     */
    class VolumeCallback(
        private val firstVolume: File
    ) : IArchiveOpenVolumeCallback, IArchiveOpenCallback {

        private val openStreams = mutableMapOf<String, RandomAccessFile>()
        private val directory = firstVolume.parentFile!!

        // --- IArchiveOpenVolumeCallback ---

        override fun getProperty(propID: PropID): Any? {
            return when (propID) {
                PropID.NAME -> firstVolume.absolutePath
                else -> null
            }
        }

        override fun getStream(filename: String): IInStream? {
            // 7z may pass just the filename or a full path
            val volumeFile = if (File(filename).isAbsolute) {
                File(filename)
            } else {
                File(directory, filename)
            }

            if (!volumeFile.exists()) {
                // Try case-insensitive lookup
                val found = directory.listFiles()?.firstOrNull {
                    it.name.equals(volumeFile.name, ignoreCase = true)
                }
                if (found == null) {
                    EcosystemLogger.d(TAG, "VolumeCallback: volume not found: ${volumeFile.name}")
                    return null // 7z will handle missing volume
                }
                val raf = RandomAccessFile(found, "r")
                openStreams[found.absolutePath] = raf
                EcosystemLogger.d(TAG, "VolumeCallback: opened volume (case-insensitive): ${found.name}")
                return RandomAccessFileInStream(raf)
            }

            val raf = RandomAccessFile(volumeFile, "r")
            openStreams[volumeFile.absolutePath] = raf
            EcosystemLogger.d(TAG, "VolumeCallback: opened volume: ${volumeFile.name}")
            return RandomAccessFileInStream(raf)
        }

        // --- IArchiveOpenCallback ---

        override fun setTotal(files: Long?, bytes: Long?) {
            // no-op, used for progress reporting
        }

        override fun setCompleted(files: Long?, bytes: Long?) {
            // no-op
        }

        /**
         * Close all opened streams.
         */
        fun close() {
            openStreams.values.forEach { raf ->
                try { raf.close() } catch (_: Exception) {}
            }
            openStreams.clear()
        }
    }
}
