package com.vamp.haron.presentation.transfer.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.vamp.haron.R
import com.vamp.haron.data.datastore.HaronPreferences

@Composable
fun QrCodeDialog(
    url: String,
    hotspotSsid: String? = null,
    hotspotPassword: String? = null,
    onDismiss: () -> Unit,
    onStopServer: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { HaronPreferences(context) }

    // Determine Wi-Fi QR source: auto-hotspot or manual settings
    val autoHotspot = hotspotSsid != null
    var savedSsid by remember { mutableStateOf(prefs.hotspotSsid) }
    var savedPassword by remember { mutableStateOf(prefs.hotspotPassword) }
    var editingSsid by remember { mutableStateOf(prefs.hotspotSsid) }
    var editingPassword by remember { mutableStateOf(prefs.hotspotPassword) }
    var showManualSetup by remember { mutableStateOf(false) }
    var passwordVisible by remember { mutableStateOf(false) }

    val wifiSsid = if (autoHotspot) hotspotSsid else savedSsid.ifEmpty { null }
    val wifiPassword = if (autoHotspot) hotspotPassword else savedPassword.ifEmpty { null }

    val wifiQrContent = wifiSsid?.let { ssid ->
        if (!wifiPassword.isNullOrBlank()) {
            "WIFI:T:WPA;S:$ssid;P:$wifiPassword;;"
        } else {
            "WIFI:T:nopass;S:$ssid;;"
        }
    }

    val hasTwoQr = wifiQrContent != null && !showManualSetup
    // Step 1 = Wi-Fi QR active (download blurred), Step 2 = Download QR active (Wi-Fi blurred)
    var activeStep by remember { mutableIntStateOf(1) }

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
                // --- Wi-Fi QR section ---
                if (hasTwoQr) {
                    Text(
                        stringResource(R.string.qr_step1_wifi),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (activeStep == 1) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        wifiSsid!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    val wifiBitmap = remember(wifiQrContent) { generateQrBitmap(wifiQrContent!!) }
                    if (wifiBitmap != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(160.dp)
                                .clip(RoundedCornerShape(8.dp))
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
                                        .background(
                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                        )
                                )
                            }
                        }
                    }

                    if (!autoHotspot) {
                        TextButton(onClick = { showManualSetup = true }) {
                            Text(
                                stringResource(R.string.qr_hotspot_setup),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    // Toggle button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f))
                        IconButton(
                            onClick = { activeStep = if (activeStep == 1) 2 else 1 },
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
                } else if (!autoHotspot) {
                    // Manual setup form
                    if (showManualSetup || wifiQrContent == null) {
                        Text(
                            stringResource(R.string.qr_hotspot_setup),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editingSsid,
                            onValueChange = { editingSsid = it },
                            label = { Text(stringResource(R.string.qr_hotspot_ssid)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = editingPassword,
                            onValueChange = { editingPassword = it },
                            label = { Text(stringResource(R.string.qr_hotspot_password)) },
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None
                                else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Filled.Visibility
                                        else Icons.Filled.VisibilityOff,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        TextButton(
                            onClick = {
                                prefs.hotspotSsid = editingSsid
                                prefs.hotspotPassword = editingPassword
                                savedSsid = editingSsid
                                savedPassword = editingPassword
                                showManualSetup = false
                            },
                            enabled = editingSsid.isNotBlank()
                        ) {
                            Text(stringResource(R.string.qr_hotspot_save))
                        }

                        Spacer(Modifier.height(4.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // --- Download QR section ---
                Text(
                    if (hasTwoQr) stringResource(R.string.qr_step2_download)
                    else stringResource(R.string.transfer_qr_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (!hasTwoQr || activeStep == 2) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.transfer_qr_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))

                val qrBitmap = remember(url) { generateQrBitmap(url) }
                if (qrBitmap != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Code",
                            modifier = Modifier
                                .size(200.dp)
                                .then(
                                    if (hasTwoQr && activeStep != 2) Modifier.blur(16.dp)
                                    else Modifier
                                )
                        )
                        if (hasTwoQr && activeStep != 2) {
                            Box(
                                Modifier
                                    .matchParentSize()
                                    .background(
                                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    url,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!hasTwoQr || activeStep == 2) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
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
