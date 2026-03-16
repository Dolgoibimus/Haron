package com.vamp.haron.presentation.cloud

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.presentation.common.ProgressInfoRow

data class CloudTransferItem(
    val id: String,
    val fileName: String,
    val percent: Int = 0,
    val isUpload: Boolean = false
)

@Composable
fun CloudTransferDialog(
    transfers: List<CloudTransferItem>,
    onCancel: (String) -> Unit,
    onCancelAll: () -> Unit
) {
    if (transfers.isEmpty()) return
    val hasUploads = transfers.any { it.isUpload }
    val hasDownloads = transfers.any { !it.isUpload }
    val titleRes = when {
        hasUploads && hasDownloads -> R.string.cloud_transferring
        hasUploads -> R.string.cloud_uploading
        else -> R.string.cloud_downloading
    }
    AlertDialog(
        onDismissRequest = { /* not dismissable during transfer */ },
        title = {
            Text(stringResource(titleRes))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                transfers.forEachIndexed { index, transfer ->
                    if (index > 0) Spacer(Modifier.height(2.dp))
                    TransferRow(
                        transfer = transfer,
                        showCancelButton = transfers.size > 1,
                        onCancel = { onCancel(transfer.id) }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancelAll) {
                Text(stringResource(android.R.string.cancel))
            }
        }
    )
}

@Composable
private fun TransferRow(
    transfer: CloudTransferItem,
    showCancelButton: Boolean = false,
    onCancel: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                transfer.fileName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            if (showCancelButton) {
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(android.R.string.cancel),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (transfer.percent > 0) {
            LinearProgressIndicator(
                progress = { transfer.percent / 100f },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        ProgressInfoRow(
            percent = if (transfer.percent > 0) "${transfer.percent}%" else ""
        )
    }
}
