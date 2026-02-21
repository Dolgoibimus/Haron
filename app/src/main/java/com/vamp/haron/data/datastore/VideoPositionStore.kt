package com.vamp.haron.data.datastore

import android.content.Context
import com.vamp.haron.common.constants.HaronConstants
import org.json.JSONObject

object VideoPositionStore {
    private const val KEY = "video_positions"
    private const val MAX_ENTRIES = 100

    fun save(context: Context, filePath: String, positionMs: Long) {
        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        val map = try {
            JSONObject(prefs.getString(KEY, "{}") ?: "{}")
        } catch (_: Exception) { JSONObject() }
        if (positionMs > 0) {
            map.put(filePath, positionMs)
            while (map.length() > MAX_ENTRIES) {
                map.remove(map.keys().next())
            }
        } else {
            map.remove(filePath)
        }
        prefs.edit().putString(KEY, map.toString()).apply()
    }

    fun load(context: Context, filePath: String): Long {
        val prefs = context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return try {
            JSONObject(prefs.getString(KEY, "{}") ?: "{}").optLong(filePath, 0L)
        } catch (_: Exception) { 0L }
    }
}
