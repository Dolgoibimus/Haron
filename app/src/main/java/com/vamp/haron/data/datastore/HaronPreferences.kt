package com.vamp.haron.data.datastore

import android.content.Context
import android.content.SharedPreferences
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HaronPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(HaronConstants.PREFS_NAME, Context.MODE_PRIVATE)

    var sortField: SortField
        get() = SortField.entries.getOrElse(
            prefs.getInt(KEY_SORT_FIELD, 0)
        ) { SortField.NAME }
        set(value) = prefs.edit().putInt(KEY_SORT_FIELD, value.ordinal).apply()

    var sortDirection: SortDirection
        get() = SortDirection.entries.getOrElse(
            prefs.getInt(KEY_SORT_DIRECTION, 0)
        ) { SortDirection.ASCENDING }
        set(value) = prefs.edit().putInt(KEY_SORT_DIRECTION, value.ordinal).apply()

    var showHidden: Boolean
        get() = prefs.getBoolean(KEY_SHOW_HIDDEN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_HIDDEN, value).apply()

    var gridColumns: Int
        get() = prefs.getInt(KEY_GRID_COLUMNS, 1)
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(1, 4)).apply()

    var panelRatio: Float
        get() = prefs.getFloat(KEY_PANEL_RATIO, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_PANEL_RATIO, value.coerceIn(0.2f, 0.8f)).apply()

    fun getSortOrder(): SortOrder = SortOrder(sortField, sortDirection)

    fun saveSortOrder(order: SortOrder) {
        sortField = order.field
        sortDirection = order.direction
    }

    // --- Favorites ---

    fun getFavorites(): List<String> {
        val json = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addFavorite(path: String) {
        val list = getFavorites().toMutableList()
        if (path !in list) {
            list.add(path)
            prefs.edit().putString(KEY_FAVORITES, JSONArray(list).toString()).apply()
        }
    }

    fun removeFavorite(path: String) {
        val list = getFavorites().toMutableList()
        if (list.remove(path)) {
            prefs.edit().putString(KEY_FAVORITES, JSONArray(list).toString()).apply()
        }
    }

    // --- Recent paths ---

    fun getRecentPaths(): List<String> {
        val json = prefs.getString(KEY_RECENT_PATHS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRecentPath(path: String) {
        val list = getRecentPaths().toMutableList()
        list.remove(path)
        list.add(0, path)
        val trimmed = list.take(MAX_RECENT)
        prefs.edit().putString(KEY_RECENT_PATHS, JSONArray(trimmed).toString()).apply()
    }

    private companion object {
        const val KEY_SORT_FIELD = "sort_field"
        const val KEY_SORT_DIRECTION = "sort_direction"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_PANEL_RATIO = "panel_ratio"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_PATHS = "recent_paths"
        const val MAX_RECENT = 10
    }
}
