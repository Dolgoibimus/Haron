package com.vamp.haron.presentation.transfer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.presentation.common.ProgressInfoRow
import com.vamp.haron.common.util.toFileSize
import com.vamp.haron.domain.model.TransferProgressInfo

@Composable
fun TransferProgressCard(
    progress: TransferProgressInfo,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        progress.currentFileName.ifEmpty { stringResource(R.string.transfer_preparing) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(
                            R.string.transfer_sending,
                            progress.currentFileIndex + 1,
                            progress.totalFiles,
                            progress.currentFileName
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onCancel) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )

            ProgressInfoRow(
                speed = if (progress.speedBytesPerSec > 0) "${progress.speedBytesPerSec.toFileSize()}/s" else "",
                counter = if (progress.totalFiles > 1) "${progress.currentFileIndex + 1}/${progress.totalFiles}" else "${progress.bytesTransferred.toFileSize()} / ${progress.totalBytes.toFileSize()}",
                percent = if (progress.totalBytes > 0) "${(progress.fraction * 100).toInt()}%" else ""
            )
        }
    }
}

