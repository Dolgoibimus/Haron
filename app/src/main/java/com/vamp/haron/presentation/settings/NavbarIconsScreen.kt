package com.vamp.haron.presentation.settings

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vamp.haron.R
import com.vamp.haron.common.util.swipeBackFromLeft
import com.vamp.haron.domain.model.NavbarAction
import java.io.File

private const val ICON_SIZE = 96
private val CROP_SHAPES = listOf("square", "circle")

/** Directory for custom navbar icons */
fun navbarIconsDir(context: android.content.Context): File {
    return File(context.filesDir, "navbar_icons").also { it.mkdirs() }
}

/** Load custom icon bitmap for an action, or null */
fun loadCustomNavbarIcon(context: android.content.Context, action: NavbarAction): Bitmap? {
    val dir = navbarIconsDir(context)
    val file = File(dir, "${action.iconName}.png")
    return if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavbarIconsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Actions that can have custom icons (exclude internal/decorative)
    val actions = remember {
        NavbarAction.entries.filter {
            it != NavbarAction.NONE &&
            it != NavbarAction.FORCE_DELETE && it != NavbarAction.CREATE_FILE &&
            it != NavbarAction.SWITCH_PANEL && it != NavbarAction.ENTER_FOLDER &&
            it != NavbarAction.TOGGLE_SHIFT &&
            it != NavbarAction.CURSOR_LEFT && it != NavbarAction.CURSOR_RIGHT
        }
    }

    // Track which actions have custom icons
    var customIcons by remember {
        mutableStateOf(
            actions.associateWith { loadCustomNavbarIcon(context, it) != null }
        )
    }

    // Crop editor state
    var editingAction by remember { mutableStateOf<NavbarAction?>(null) }
    var editingBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Image picker
    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null && editingAction != null) {
            try {
                val stream = context.contentResolver.openInputStream(uri)
                val bmp = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (bmp != null) editingBitmap = bmp
            } catch (_: Exception) {}
        }
    }

    // Crop editor dialog
    if (editingBitmap != null && editingAction != null) {
        CropEditorDialog(
            bitmap = editingBitmap!!,
            onSave = { croppedBitmap, shape ->
                val finalBmp = applyCropShape(croppedBitmap, shape)
                saveNavbarIcon(context, editingAction!!, finalBmp)
                customIcons = customIcons.toMutableMap().apply { put(editingAction!!, true) }
                editingBitmap = null
                editingAction = null
            },
            onDismiss = {
                editingBitmap = null
                editingAction = null
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.navbar_icons_title), fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                expandedHeight = 36.dp
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .swipeBackFromLeft(onBack = onBack)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(actions) { action ->
                val hasCustom = customIcons[action] == true
                val customBmp = remember(hasCustom) {
                    if (hasCustom) loadCustomNavbarIcon(context, action) else null
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingAction = action
                            pickImageLauncher.launch("image/*")
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon preview (custom or default placeholder)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (customBmp != null) {
                            Image(
                                bitmap = customBmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = "—",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                fontSize = 16.sp
                            )
                        }
                    }

                    Spacer(Modifier.width(12.dp))

                    // Action name
                    Text(
                        text = stringResource(action.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                        color = if (hasCustom) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Reset button (if custom icon exists)
                    if (hasCustom) {
                        IconButton(
                            onClick = {
                                deleteNavbarIcon(context, action)
                                customIcons = customIcons.toMutableMap().apply { put(action, false) }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.navbar_icons_reset),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CropEditorDialog(
    bitmap: Bitmap,
    onSave: (Bitmap, String) -> Unit,
    onDismiss: () -> Unit
) {
    var cropShape by remember { mutableStateOf("square") }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    stringResource(R.string.navbar_icons_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Crop preview area
                val cropAreaSize = 240.dp
                Box(
                    modifier = Modifier
                        .size(cropAreaSize)
                        .background(Color.Black.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Image with transform
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            },
                        contentScale = ContentScale.Fit
                    )

                    // Crop frame overlay
                    val frameColor = MaterialTheme.colorScheme.primary
                    Box(
                        modifier = Modifier
                            .size(cropAreaSize - 40.dp)
                            .drawWithContent {
                                drawContent()
                                if (cropShape == "circle") {
                                    drawCircle(
                                        color = frameColor,
                                        radius = size.minDimension / 2,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                } else {
                                    drawRect(
                                        color = frameColor,
                                        style = Stroke(width = 2.dp.toPx())
                                    )
                                }
                            }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Shape selector
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = cropShape == "square",
                        onClick = { cropShape = "square" },
                        label = { Text(stringResource(R.string.navbar_icons_crop_square)) }
                    )
                    FilterChip(
                        selected = cropShape == "circle",
                        onClick = { cropShape = "circle" },
                        label = { Text(stringResource(R.string.navbar_icons_crop_circle)) }
                    )
                }

                Spacer(Modifier.height(12.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = {
                        val cropped = cropBitmap(bitmap, scale, offsetX, offsetY, cropShape)
                        onSave(cropped, cropShape)
                    }) {
                        Text(stringResource(R.string.navbar_icons_save))
                    }
                }
            }
        }
    }
}

/**
 * Crop the bitmap based on the transform (scale, offset) and crop area.
 * The crop area is centered in a 240dp preview, frame is 200dp (240-40).
 * We map the frame coordinates back to bitmap space.
 */
private fun cropBitmap(
    source: Bitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    shape: String
): Bitmap {
    // Preview area: 240dp, frame: 200dp → frame is centered
    // In pixel space of the preview, the image is scaled and translated
    // Frame center = preview center = (previewSize/2, previewSize/2)
    // Image center in preview = (previewSize/2 + offsetX, previewSize/2 + offsetY)
    // Image pixel at preview coord (px, py) = ((px - imgCenterX) / imgScale + srcW/2, ...)

    val srcW = source.width.toFloat()
    val srcH = source.height.toFloat()

    // The image is fit into the preview (ContentScale.Fit)
    // Preview is 240dp but we work in arbitrary units — the frame is 200/240 of the preview
    val previewFraction = 200f / 240f

    // In the ContentScale.Fit space, the image fills the box
    val fitScale = minOf(1f, 1f) // normalized to 1 in image coords
    val effectiveScale = scale

    // Frame size in image pixels
    val frameSizeInImagePx = (minOf(srcW, srcH) * previewFraction) / effectiveScale

    // Frame center in image coords
    val frameCenterX = srcW / 2f - offsetX / effectiveScale * (srcW / 240f * 0.5f)
    val frameCenterY = srcH / 2f - offsetY / effectiveScale * (srcH / 240f * 0.5f)

    // Simple approach: take a centered square of the image, adjusted by scale/offset
    val cropSize = (minOf(srcW, srcH) / effectiveScale).coerceIn(1f, minOf(srcW, srcH))
    val cx = (srcW / 2f - offsetX * srcW / (240f * effectiveScale)).coerceIn(cropSize / 2f, srcW - cropSize / 2f)
    val cy = (srcH / 2f - offsetY * srcH / (240f * effectiveScale)).coerceIn(cropSize / 2f, srcH - cropSize / 2f)

    val left = (cx - cropSize / 2f).toInt().coerceIn(0, source.width - 1)
    val top = (cy - cropSize / 2f).toInt().coerceIn(0, source.height - 1)
    val size = cropSize.toInt().coerceIn(1, minOf(source.width - left, source.height - top))

    val cropped = Bitmap.createBitmap(source, left, top, size, size)
    return Bitmap.createScaledBitmap(cropped, ICON_SIZE, ICON_SIZE, true)
}

/** Apply circle mask if needed */
private fun applyCropShape(bitmap: Bitmap, shape: String): Bitmap {
    if (shape != "circle") return bitmap

    val output = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    val path = Path()
    path.addOval(RectF(0f, 0f, ICON_SIZE.toFloat(), ICON_SIZE.toFloat()), Path.Direction.CW)
    canvas.clipPath(path)
    canvas.drawBitmap(bitmap, 0f, 0f, paint)

    return output
}

private fun saveNavbarIcon(context: android.content.Context, action: NavbarAction, bitmap: Bitmap) {
    val dir = navbarIconsDir(context)
    val file = File(dir, "${action.iconName}.png")
    file.outputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
}

private fun deleteNavbarIcon(context: android.content.Context, action: NavbarAction) {
    val dir = navbarIconsDir(context)
    File(dir, "${action.iconName}.png").delete()
}
