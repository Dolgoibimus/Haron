package com.vamp.haron.presentation.cast.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.ScreenShare
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.domain.model.CastMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastModeSheet(
    availableModes: List<CastMode>,
    onModeSelected: (CastMode) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Text(
                stringResource(R.string.cast_mode_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(8.dp))

            availableModes.forEach { mode ->
                CastModeItem(
                    icon = mode.icon(),
                    title = stringResource(mode.titleRes()),
                    subtitle = stringResource(mode.subtitleRes()),
                    onClick = { onModeSelected(mode) }
                )
            }
        }
    }
}

@Composable
private fun CastModeItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(16.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun CastMode.icon(): ImageVector = when (this) {
    CastMode.SINGLE_MEDIA -> Icons.Filled.PlayCircle
    CastMode.SLIDESHOW -> Icons.Filled.Slideshow
    CastMode.PDF_PRESENTATION -> Icons.Filled.PictureAsPdf
    CastMode.SCREEN_MIRROR -> Icons.Filled.ScreenShare
}

private fun CastMode.titleRes(): Int = when (this) {
    CastMode.SINGLE_MEDIA -> R.string.cast_mode_single
    CastMode.SLIDESHOW -> R.string.cast_mode_slideshow
    CastMode.PDF_PRESENTATION -> R.string.cast_mode_pdf
    CastMode.SCREEN_MIRROR -> R.string.cast_mode_mirror
}

private fun CastMode.subtitleRes(): Int = when (this) {
    CastMode.SINGLE_MEDIA -> R.string.cast_mode_single_desc
    CastMode.SLIDESHOW -> R.string.cast_mode_slideshow_desc
    CastMode.PDF_PRESENTATION -> R.string.cast_mode_pdf_desc
    CastMode.SCREEN_MIRROR -> R.string.cast_mode_mirror_desc
}
