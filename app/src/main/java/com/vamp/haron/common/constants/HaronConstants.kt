package com.vamp.haron.common.constants

import android.os.Environment

object HaronConstants {
    const val TAG = "Haron"
    val ROOT_PATH: String = Environment.getExternalStorageDirectory().absolutePath
    const val TRASH_TTL_DAYS = 30
    const val TRASH_DIR_NAME = ".haron_trash"
    const val TRASH_META_FILE = "meta.json"
    const val PREFS_NAME = "haron_prefs"

    // Secure Folder
    const val SECURE_DIR_NAME = ".haron_secure"
    const val SECURE_INDEX_FILE = "index.enc"
    const val SECURE_KEYSTORE_ALIAS = "haron_secure_key"
    const val SECURE_TEMP_DIR = "secure_temp"
    const val VIRTUAL_SECURE_PATH = "__haron_secure__"

    // Transfer
    const val TRANSFER_PORT_START = 8080
    const val TRANSFER_PORT_END = 8090
    const val NSD_SERVICE_TYPE = "_haron._tcp."
    const val TRANSFER_BUFFER_SIZE = 8192
    const val TRANSFER_MAX_RETRIES = 5
    const val TRANSFER_RETRY_DELAYS = "1000,2000,4000,8000,16000" // ms, comma-separated

    // Cast
    const val CAST_APP_ID = "CC1AD845" // Default Media Receiver

    // SMB
    const val SMB_PREFIX = "smb://"
    const val SMB_CREDENTIAL_KEYSTORE_ALIAS = "haron_smb_cred_key"
    const val SMB_CREDENTIAL_FILE = "smb_credentials.enc"
    const val SMB_CONNECTION_TIMEOUT_SEC = 10L
    const val SMB_IDLE_TIMEOUT_MS = 300_000L

    // SSH
    const val SSH_CREDENTIAL_KEYSTORE_ALIAS = "haron_ssh_cred_key"
    const val SSH_CREDENTIAL_FILE = "ssh_credentials.enc"
}
