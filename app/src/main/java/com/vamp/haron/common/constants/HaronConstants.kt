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
}
