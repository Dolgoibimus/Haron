package com.vamp.haron.presentation.transfer.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.vamp.haron.R
import com.vamp.haron.data.ftp.FtpCredential

@Composable
fun FtpAuthDialog(
    host: String,
    port: Int,
    isConnecting: Boolean,
    error: String?,
    onConnect: (FtpCredential, Boolean) -> Unit,
    onConnectAnonymous: () -> Unit,
    onDismiss: () -> Unit
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var saveCredentials by remember { mutableStateOf(true) }
    var passwordVisible by remember { mutableStateOf(false) }
    var useFtps by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!isConnecting) onDismiss() },
        title = {
            Text(stringResource(R.string.ftp_auth_title, host))
        },
        text = {
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.ftp_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.ftp_password)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting,
                    visualTransformation = if (passwordVisible)
                        VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Filled.VisibilityOff
                                else Icons.Filled.Visibility,
                                contentDescription = null
                            )
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = useFtps,
                        onCheckedChange = { useFtps = it },
                        enabled = !isConnecting
                    )
                    Text("FTPS", style = MaterialTheme.typography.bodyMedium)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = saveCredentials,
                        onCheckedChange = { saveCredentials = it },
                        enabled = !isConnecting
                    )
                    Text(
                        stringResource(R.string.ftp_save_credentials),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (isConnecting) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ftp_connecting),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConnect(
                        FtpCredential(
                            host = host,
                            port = port,
                            username = username,
                            password = password,
                            useFtps = useFtps
                        ),
                        saveCredentials
                    )
                },
                enabled = !isConnecting && username.isNotBlank()
            ) {
                Text(stringResource(R.string.ftp_connect))
            }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = onConnectAnonymous,
                    enabled = !isConnecting
                ) {
                    Text(stringResource(R.string.ftp_anonymous))
                }
                TextButton(
                    onClick = onDismiss,
                    enabled = !isConnecting
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        }
    )
}
