package com.vamp.haron.data.saf

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.vamp.core.logger.EcosystemLogger
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.datastore.HaronPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SafUriManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: HaronPreferences
) {
    fun persistUri(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.addSafUri(uri.toString())
            EcosystemLogger.d(HaronConstants.TAG, "SAF URI persisted: $uri")
        } catch (e: Exception) {
            EcosystemLogger.e(HaronConstants.TAG, "Failed to persist SAF URI: ${e.message}")
        }
    }

    fun getPersistedUris(): List<Uri> {
        val saved = preferences.getSafUris()
        val persisted = context.contentResolver.persistedUriPermissions.map { it.uri.toString() }.toSet()
        // Return only URIs that are still valid
        return saved.filter { it in persisted }.map { Uri.parse(it) }
    }

    fun releaseUri(uri: Uri) {
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: Exception) { }
        preferences.removeSafUri(uri.toString())
        EcosystemLogger.d(HaronConstants.TAG, "SAF URI released: $uri")
    }

    fun isUriAccessible(uri: Uri): Boolean {
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }
}
