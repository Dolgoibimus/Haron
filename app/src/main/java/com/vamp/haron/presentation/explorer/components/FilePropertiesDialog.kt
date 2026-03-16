package com.vamp.haron.presentation.explorer.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.vamp.haron.R
import com.vamp.haron.presentation.common.ProgressInfoRow
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.usecase.AudioTags
import com.vamp.haron.domain.usecase.CoverResult
import com.vamp.haron.domain.usecase.FileProperties
import com.vamp.haron.domain.usecase.HashResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FilePropertiesDialog(
    properties: FileProperties?,
    hashResult: HashResult?,
    isHashCalculating: Boolean,
    coverResult: CoverResult?,
    onCalculateHash: () -> Unit,
    onCopyHash: (String) -> Unit,
    onRemoveExif: () -> Unit,
    onFetchCover: (String?) -> Unit,
    onSaveAll: (AudioTags?) -> Unit,
    onDismiss: () -> Unit,
    isContentUri: Boolean = false,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                // Title
                Text(
                    text = properties?.name ?: stringResource(R.string.properties_title),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(16.dp))

                // Content
                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // === General section ===
                    item {
                        SectionHeader(stringResource(R.string.general_section))
                    }

                    if (properties != null) {
                        item { PropertyRow(stringResource(R.string.path_label), properties.path, maxLines = 5) }
                        item {
                            PropertyRow(
                                stringResource(R.string.size_label),
                                if (properties.isDirectory) {
                                    stringResource(R.string.size_dir_format, properties.totalSize.toFileSize(), properties.childCount)
                                } else {
                                    stringResource(R.string.size_file_format, properties.size.toFileSize(), properties.size)
                                }
                            )
                        }
                        item {
                            PropertyRow(
                                stringResource(R.string.modified_date_label),
                                SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                                    .format(Date(properties.lastModified))
                            )
                        }
                        item { PropertyRow(stringResource(R.string.mime_type_label), properties.mimeType) }
                        if (properties.permissions.isNotEmpty()) {
                            item { PropertyRow(stringResource(R.string.permissions_label), properties.permissions) }
                        }
                    } else {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }

                    // === EXIF section ===
                    if (properties != null && properties.exifData.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            SectionHeader(stringResource(R.string.exif_section))
                        }

                        val exifEntries = properties.exifData.entries.toList()
                        items(exifEntries, key = { it.key }) { (key, value) ->
                            PropertyRow(key, value)
                        }

                        // Remove EXIF button
                        if (!isContentUri) {
                            item {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = onRemoveExif,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.remove_exif))
                                }
                            }
                        }
                    }

                    // === Audio tags section ===
                    if (properties != null && properties.audioTags != null) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            SectionHeader(stringResource(R.string.audio_section))
                        }

                        item {
                            AudioTagsEditor(
                                tags = properties.audioTags,
                                isContentUri = isContentUri,
                                hasEmbeddedCover = properties.hasEmbeddedCover,
                                coverResult = coverResult,
                                fileName = properties.name,
                                onSaveAll = onSaveAll,
                                onFetchCover = onFetchCover
                            )
                        }
                    }

                    // === Document metadata section (PDF, FB2) ===
                    if (properties != null && properties.documentMetadata.isNotEmpty()) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            SectionHeader(stringResource(R.string.doc_section))
                        }

                        val docEntries = properties.documentMetadata.entries.toList()
                        items(docEntries, key = { it.key }) { (key, value) ->
                            PropertyRow(key, value, maxLines = 5)
                        }
                    }

                    // === Hash section ===
                    if (properties != null && !properties.isDirectory) {
                        item {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider()
                            SectionHeader(stringResource(R.string.hash_section))
                        }

                        if (hashResult == null && !isHashCalculating) {
                            item {
                                Button(
                                    onClick = onCalculateHash,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        Icons.Filled.Tag,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(stringResource(R.string.calculate_hash))
                                }
                            }
                        }

                        if (isHashCalculating && (hashResult == null || hashResult.md5.isEmpty())) {
                            item {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        stringResource(R.string.calculating),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    val hashProgress = hashResult?.progress ?: 0f
                                    LinearProgressIndicator(
                                        progress = { hashProgress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                    ProgressInfoRow(
                                        percent = if (hashProgress > 0f) "${(hashProgress * 100).toInt()}%" else ""
                                    )
                                }
                            }
                        }

                        if (hashResult != null && hashResult.md5.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    HashRow("CRC32", hashResult.crc32, onCopy = { onCopyHash(hashResult.crc32) })
                                    HashRow("MD5", hashResult.md5, onCopy = { onCopyHash(hashResult.md5) })
                                    HashRow("SHA-1", hashResult.sha1, onCopy = { onCopyHash(hashResult.sha1) })
                                    HashRow("SHA-256", hashResult.sha256, onCopy = { onCopyHash(hashResult.sha256) }, maxLines = 3)
                                    HashRow("SHA-512", hashResult.sha512, onCopy = { onCopyHash(hashResult.sha512) }, maxLines = 6)
                                }
                            }
                        }
                    }
                }

                // Close button
                Spacer(Modifier.height(4.dp))
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
private fun PropertyRow(label: String, value: String, maxLines: Int = 3) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HashRow(label: String, hash: String, onCopy: () -> Unit, maxLines: Int = 2) {
    var isComparing by remember { mutableStateOf(false) }
    var compareText by remember { mutableStateOf("") }

    val matchState = when {
        !isComparing || compareText.isBlank() -> null
        compareText.trim().equals(hash, ignoreCase = true) -> true
        else -> false
    }

    val matchColor = when (matchState) {
        true -> Color(0xFF4CAF50)
        false -> MaterialTheme.colorScheme.error
        null -> MaterialTheme.colorScheme.onSurface
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isComparing) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(4.dp))
                val borderColor = when (matchState) {
                    true -> Color(0xFF4CAF50)
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.outline
                }
                val textColor = MaterialTheme.colorScheme.onSurface
                val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                BasicTextField(
                    value = compareText,
                    onValueChange = { compareText = it },
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, borderColor, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        color = textColor
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    decorationBox = { innerTextField ->
                        if (compareText.isEmpty()) {
                            Text(
                                stringResource(R.string.hash_paste_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = hintColor
                            )
                        }
                        innerTextField()
                    }
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
            IconButton(
                onClick = {
                    isComparing = !isComparing
                    if (!isComparing) compareText = ""
                },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.CompareArrows,
                    contentDescription = stringResource(R.string.hash_compare),
                    modifier = Modifier.size(16.dp),
                    tint = if (isComparing) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = stringResource(R.string.copy_action),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Text(
            text = hash,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            color = matchColor,
            modifier = Modifier.padding(end = 28.dp)
        )
    }
}

@Composable
private fun AudioTagsEditor(
    tags: AudioTags,
    isContentUri: Boolean,
    hasEmbeddedCover: Boolean,
    coverResult: CoverResult?,
    fileName: String,
    onSaveAll: (AudioTags?) -> Unit,
    onFetchCover: (String?) -> Unit
) {
    var editTitle by remember(tags.title) { mutableStateOf(tags.title) }
    var editArtist by remember(tags.artist) { mutableStateOf(tags.artist) }
    var editAlbum by remember(tags.album) { mutableStateOf(tags.album) }
    var editYear by remember(tags.year) { mutableStateOf(tags.year) }
    var editGenre by remember(tags.genre) { mutableStateOf(tags.genre) }

    // Auto-fill empty fields when search returns metadata
    LaunchedEffect(coverResult) {
        if (coverResult is CoverResult.Found) {
            if (editTitle.isBlank() && coverResult.title.isNotBlank()) editTitle = coverResult.title
            if (editArtist.isBlank() && coverResult.artist.isNotBlank()) editArtist = coverResult.artist
            if (editAlbum.isBlank() && coverResult.album.isNotBlank()) editAlbum = coverResult.album
            if (editYear.isBlank() && coverResult.year.isNotBlank()) editYear = coverResult.year
            if (editGenre.isBlank() && coverResult.genre.isNotBlank()) editGenre = coverResult.genre
        }
    }

    val hasChanges = editTitle != tags.title || editArtist != tags.artist ||
        editAlbum != tags.album || editYear != tags.year || editGenre != tags.genre

    val hasCoverToSave = coverResult is CoverResult.Found && coverResult.imageBytes.isNotEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
        // Editable fields
        TagField(stringResource(R.string.audio_title), editTitle) { editTitle = it }
        TagField(stringResource(R.string.audio_artist), editArtist) { editArtist = it }
        TagField(stringResource(R.string.audio_album), editAlbum) { editAlbum = it }
        TagField(stringResource(R.string.audio_year), editYear) { editYear = it }
        TagField(stringResource(R.string.audio_genre), editGenre) { editGenre = it }

        // Read-only: duration + bitrate
        if (tags.duration.isNotEmpty()) {
            PropertyRow(stringResource(R.string.audio_duration), tags.duration)
        }
        if (tags.bitrate.isNotEmpty()) {
            PropertyRow(stringResource(R.string.audio_bitrate), tags.bitrate)
        }

        // "Save all" button — saves tags + cover sequentially in one operation
        if (!isContentUri && (hasChanges || hasCoverToSave)) {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val tagsToSave = if (hasChanges) AudioTags(
                        title = editTitle.trim(),
                        artist = editArtist.trim(),
                        album = editAlbum.trim(),
                        year = editYear.trim(),
                        genre = editGenre.trim()
                    ) else null
                    onSaveAll(tagsToSave)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (hasChanges && hasCoverToSave && !hasEmbeddedCover)
                        stringResource(R.string.audio_save_all)
                    else if (hasCoverToSave && !hasEmbeddedCover)
                        stringResource(R.string.audio_save_cover)
                    else stringResource(R.string.audio_save_tags)
                )
            }
        }

        // Cover status
        PropertyRow(
            stringResource(R.string.audio_has_cover),
            if (hasEmbeddedCover) "\u2713" else "\u2717"
        )

        // Cover preview when found
        if (coverResult is CoverResult.Found && coverResult.imageBytes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Image(
                    bitmap = coverResult.bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Cover search section — show when no cover embedded and not yet found
        if (!isContentUri && !hasEmbeddedCover && coverResult !is CoverResult.Found) {
            AudioCoverSection(
                coverResult = coverResult,
                fileName = fileName,
                onFetchCover = onFetchCover
            )
        }
    }
}

@Composable
private fun TagField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            singleLine = true,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            decorationBox = { innerTextField ->
                if (value.isEmpty()) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )
    }
}

@Composable
private fun AudioCoverSection(
    coverResult: CoverResult?,
    fileName: String,
    onFetchCover: (String?) -> Unit
) {
    var manualArtist by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(8.dp))

        if (coverResult is CoverResult.NotFound) {
            Text(
                stringResource(R.string.audio_cover_not_found),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
        }
        if (coverResult is CoverResult.Error) {
            Text(
                coverResult.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
        }

        if (coverResult is CoverResult.Searching) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.audio_searching_cover),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (coverResult is CoverResult.Saved) {
            Text(
                stringResource(R.string.audio_cover_saved),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Search controls — show when not searching and no result yet
        if (coverResult == null || coverResult is CoverResult.NotFound || coverResult is CoverResult.Error) {
            // "Search by filename" button
            OutlinedButton(
                onClick = {
                    val query = parseQueryFromFileName(fileName)
                    onFetchCover(query.ifEmpty { null })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.audio_search_by_filename))
            }

            Spacer(Modifier.height(4.dp))

            // Manual input fields
            OutlinedTextField(
                value = manualArtist,
                onValueChange = { manualArtist = it },
                label = { Text(stringResource(R.string.audio_artist)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = manualTitle,
                onValueChange = { manualTitle = it },
                label = { Text(stringResource(R.string.audio_title)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    val manual = listOf(manualArtist.trim(), manualTitle.trim())
                        .filter { it.isNotEmpty() }
                        .joinToString(" ")
                    onFetchCover(manual.ifEmpty { null })
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Album, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.audio_fetch_cover))
            }
        }
    }
}

/**
 * Parse "Artist - Title" from filename.
 * Handles: "Artist - Title (site.com).mp3", "Artist - Title.mp3", "01. Artist - Title.mp3"
 * Strips: extension, (parenthesized suffixes), [bracketed suffixes], leading track numbers.
 */
private fun parseQueryFromFileName(fileName: String): String {
    var name = fileName.substringBeforeLast('.') // remove extension
    name = name.replace(Regex("\\([^)]*\\)"), "").trim() // remove (zaycev.net) etc.
    name = name.replace(Regex("\\[[^]]*]"), "").trim()   // remove [320kbps] etc.
    name = name.replace(Regex("^\\d{1,3}\\.?\\s*"), "")  // remove leading "01. " track number
    return name.trim()
}
