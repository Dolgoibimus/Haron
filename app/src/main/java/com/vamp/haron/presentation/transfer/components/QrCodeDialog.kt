package com.vamp.haron.presentation.transfer.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vamp.haron.R

@Composable
fun QrCodeDialog(
    url: String,
    hotspotSsid: String? = null,
    hotspotPassword: String? = null,
    hotspotUrl: String? = null,
    isHotspotMode: Boolean = false,
    onToggleHotspot: () -> Unit = {},
    onDismiss: () -> Unit,
    onStopServer: () -> Unit
) {
    // In hotspot mode: tab 0 = Haron, tab 1 = Others
    var hotspotTab by remember { mutableIntStateOf(0) }
    // For "Others" tab: step 1 = Wi-Fi QR, step 2 = URL QR
    var othersActiveStep by remember { mutableIntStateOf(1) }

    // The URL to display: in hotspot mode use hotspotUrl, otherwise regular url
    val displayUrl = if (isHotspotMode && hotspotUrl != null) hotspotUrl else url

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    stringResource(R.string.transfer_qr_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))

                if (!isHotspotMode) {
                    // ========== Wi-Fi MODE ==========
                    WifiModeContent(
                        url = displayUrl,
                        onTapQr = onToggleHotspot
                    )
                } else {
                    // ========== HOTSPOT MODE ==========
                    // Mode indicator
                    Text(
                        stringResource(R.string.qr_mode_hotspot),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    Text(
                        stringResource(R.string.qr_tap_to_switch),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(8.dp))

                    // Tabs: Haron / Others
                    TabRow(
                        selectedTabIndex = hotspotTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = hotspotTab == 0,
                            onClick = { hotspotTab = 0 },
                            text = { Text("Haron") }
                        )
                        Tab(
                            selected = hotspotTab == 1,
                            onClick = { hotspotTab = 1 },
                            text = { Text(stringResource(R.string.qr_tab_others)) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    when (hotspotTab) {
                        0 -> HaronTabContent(
                            ssid = hotspotSsid ?: "",
                            password = hotspotPassword,
                            url = displayUrl,
                            onTapQr = onToggleHotspot
                        )
                        1 -> OthersTabContent(
                            ssid = hotspotSsid ?: "",
                            password = hotspotPassword,
                            url = displayUrl,
                            activeStep = othersActiveStep,
                            onStepChange = { othersActiveStep = it },
                            onTapQr = onToggleHotspot
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row {
                    TextButton(onClick = onStopServer) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
}

/**
 * Wi-Fi mode: single QR with URL. Tap to switch to hotspot.
 */
@Composable
private fun WifiModeContent(
    url: String,
    onTapQr: () -> Unit
) {
    // Mode indicator
    Text(
        stringResource(R.string.qr_mode_wifi),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    Text(
        stringResource(R.string.qr_mode_wifi_hint),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(8.dp))

    val qrBitmap = remember(url) { generateQrBitmap(url) }
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "QR Code",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onTapQr() }
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.qr_tap_to_switch),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
}

/**
 * Haron tab in hotspot mode: combined JSON QR with ssid+pass+url.
 */
@Composable
private fun HaronTabContent(
    ssid: String,
    password: String?,
    url: String,
    onTapQr: () -> Unit
) {
    // Build combined JSON for Haron-to-Haron auto-connect
    val combinedJson = buildString {
        append("{\"haron\":1,\"ssid\":\"")
        append(ssid.replace("\"", "\\\""))
        append("\",\"pass\":\"")
        append((password ?: "").replace("\"", "\\\""))
        append("\",\"url\":\"")
        append(url.replace("\"", "\\\""))
        append("\"}")
    }

    val qrBitmap = remember(combinedJson) { generateQrBitmap(combinedJson) }
    if (qrBitmap != null) {
        Image(
            bitmap = qrBitmap.asImageBitmap(),
            contentDescription = "Haron QR",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onTapQr() }
        )
    }

    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.qr_mode_hotspot_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Others tab in hotspot mode: two QR codes (WIFI: + URL) with SwapVert toggle.
 * Same as the old two-step layout.
 */
@Composable
private fun OthersTabContent(
    ssid: String,
    password: String?,
    url: String,
    activeStep: Int,
    onStepChange: (Int) -> Unit,
    onTapQr: () -> Unit
) {
    val wifiQrContent = if (!password.isNullOrBlank()) {
        "WIFI:T:WPA;S:$ssid;P:$password;;"
    } else {
        "WIFI:T:nopass;S:$ssid;;"
    }

    // Step 1: Wi-Fi QR
    Text(
        stringResource(R.string.qr_step1_wifi),
        style = MaterialTheme.typography.titleSmall,
        color = if (activeStep == 1) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
    Spacer(Modifier.height(2.dp))
    Text(
        ssid,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(4.dp))

    val wifiBitmap = remember(wifiQrContent) { generateQrBitmap(wifiQrContent) }
    if (wifiBitmap != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onTapQr() }
        ) {
            Image(
                bitmap = wifiBitmap.asImageBitmap(),
                contentDescription = "Wi-Fi QR",
                modifier = Modifier
                    .size(160.dp)
                    .then(if (activeStep != 1) Modifier.blur(16.dp) else Modifier)
            )
            if (activeStep != 1) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))

    // Toggle between steps
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { onStepChange(if (activeStep == 1) 2 else 1) },
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Filled.SwapVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
        HorizontalDivider(modifier = Modifier.weight(1f))
    }

    Spacer(Modifier.height(4.dp))

    // Step 2: Download QR
    Text(
        stringResource(R.string.qr_step2_download),
        style = MaterialTheme.typography.titleSmall,
        color = if (activeStep == 2) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
    Spacer(Modifier.height(2.dp))
    Text(
        stringResource(R.string.transfer_qr_subtitle),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(4.dp))

    val urlBitmap = remember(url) { generateQrBitmap(url) }
    if (urlBitmap != null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onTapQr() }
        ) {
            Image(
                bitmap = urlBitmap.asImageBitmap(),
                contentDescription = "URL QR",
                modifier = Modifier
                    .size(160.dp)
                    .then(if (activeStep != 2) Modifier.blur(16.dp) else Modifier)
            )
            if (activeStep != 2) {
                Box(
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                )
            }
        }
    }

    Spacer(Modifier.height(4.dp))
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium,
        color = if (activeStep == 2) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}

private fun generateQrBitmap(text: String): Bitmap? {
    return try {
        val size = 512
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        bitmap
    } catch (_: Exception) {
        null
    }
}
