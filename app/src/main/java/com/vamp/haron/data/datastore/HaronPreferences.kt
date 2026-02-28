package com.vamp.haron.data.datastore

import android.content.Context
import android.content.SharedPreferences
import com.vamp.haron.common.constants.HaronConstants
import com.vamp.haron.data.model.SortDirection
import com.vamp.haron.data.model.SortField
import com.vamp.haron.data.model.SortOrder
import com.vamp.haron.domain.model.FileTag
import com.vamp.haron.domain.model.GestureAction
import com.vamp.haron.domain.model.GestureType
import com.vamp.haron.domain.model.ShelfItem
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
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
        set(value) = prefs.edit().putInt(KEY_GRID_COLUMNS, value.coerceIn(1, 6)).apply()

    var panelRatio: Float
        get() = prefs.getFloat(KEY_PANEL_RATIO, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_PANEL_RATIO, value.coerceIn(0.2f, 0.8f)).apply()

    /** Trash max size in MB (0 = unlimited). Default 500 MB. */
    var trashMaxSizeMb: Int
        get() = prefs.getInt(KEY_TRASH_MAX_SIZE_MB, 500)
        set(value) = prefs.edit().putInt(KEY_TRASH_MAX_SIZE_MB, value.coerceAtLeast(0)).apply()

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

    // --- SAF URIs ---

    fun getSafUris(): List<String> {
        val json = prefs.getString(KEY_SAF_URIS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addSafUri(uri: String) {
        val list = getSafUris().toMutableList()
        if (uri !in list) {
            list.add(uri)
            prefs.edit().putString(KEY_SAF_URIS, JSONArray(list).toString()).apply()
        }
    }

    fun removeSafUri(uri: String) {
        val list = getSafUris().toMutableList()
        if (list.remove(uri)) {
            prefs.edit().putString(KEY_SAF_URIS, JSONArray(list).toString()).apply()
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

    // --- Shelf ---

    fun getShelfItems(): List<ShelfItem> {
        val json = prefs.getString(KEY_SHELF_ITEMS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                ShelfItem(
                    name = obj.getString("name"),
                    path = obj.getString("path"),
                    isDirectory = obj.getBoolean("isDirectory"),
                    size = obj.getLong("size")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveShelfItems(items: List<ShelfItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("name", item.name)
                put("path", item.path)
                put("isDirectory", item.isDirectory)
                put("size", item.size)
            })
        }
        prefs.edit().putString(KEY_SHELF_ITEMS, arr.toString()).apply()
    }

    fun addShelfItems(newItems: List<ShelfItem>) {
        val existing = getShelfItems().toMutableList()
        val existingPaths = existing.map { it.path }.toSet()
        newItems.filter { it.path !in existingPaths }.let { existing.addAll(it) }
        saveShelfItems(existing)
    }

    fun removeShelfItem(path: String) {
        val items = getShelfItems().filter { it.path != path }
        saveShelfItems(items)
    }

    fun clearShelf() {
        prefs.edit().remove(KEY_SHELF_ITEMS).apply()
    }

    // --- Duplicate detector: original overrides & folders ---

    fun saveOriginalOverrides(overrides: Map<String, String>) {
        val obj = JSONObject()
        overrides.forEach { (hash, path) -> obj.put(hash, path) }
        prefs.edit().putString(KEY_ORIGINAL_OVERRIDES, obj.toString()).apply()
    }

    fun getOriginalOverrides(): Map<String, String> {
        val json = prefs.getString(KEY_ORIGINAL_OVERRIDES, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, String>()
            obj.keys().forEach { key -> map[key] = obj.getString(key) }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun saveOriginalFolders(folders: Set<String>) {
        prefs.edit().putString(KEY_ORIGINAL_FOLDERS, JSONArray(folders.toList()).toString()).apply()
    }

    fun getOriginalFolders(): Set<String> {
        val json = prefs.getString(KEY_ORIGINAL_FOLDERS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    // --- Haptic ---

    var hapticEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTIC_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTIC_ENABLED, value).apply()

    // --- Night mode ---

    var nightModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_NIGHT_MODE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_NIGHT_MODE_ENABLED, value).apply()

    var nightModeStartHour: Int
        get() = prefs.getInt(KEY_NIGHT_MODE_START_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE_START_HOUR, value).apply()

    var nightModeStartMinute: Int
        get() = prefs.getInt(KEY_NIGHT_MODE_START_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE_START_MINUTE, value).apply()

    var nightModeEndHour: Int
        get() = prefs.getInt(KEY_NIGHT_MODE_END_HOUR, 7)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE_END_HOUR, value).apply()

    var nightModeEndMinute: Int
        get() = prefs.getInt(KEY_NIGHT_MODE_END_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_NIGHT_MODE_END_MINUTE, value).apply()

    // --- Font / Icon scaling ---

    var fontScale: Float
        get() = prefs.getFloat(KEY_FONT_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_FONT_SCALE, value.coerceIn(0.6f, 1.4f)).apply()

    var iconScale: Float
        get() = prefs.getFloat(KEY_ICON_SCALE, 1.0f)
        set(value) = prefs.edit().putFloat(KEY_ICON_SCALE, value.coerceIn(0.6f, 1.4f)).apply()

    // --- Last opened files (for tools pie menu) ---

    var lastMediaFile: String?
        get() = prefs.getString(KEY_LAST_MEDIA_FILE, null)
        set(value) = prefs.edit().putString(KEY_LAST_MEDIA_FILE, value).apply()

    var lastDocumentFile: String?
        get() = prefs.getString(KEY_LAST_DOCUMENT_FILE, null)
        set(value) = prefs.edit().putString(KEY_LAST_DOCUMENT_FILE, value).apply()

    // --- Bookmarks (digit slots 1-9) ---

    fun getBookmarks(): Map<Int, String> {
        val json = prefs.getString(KEY_BOOKMARKS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Int, String>()
            obj.keys().forEach { key ->
                val slot = key.toIntOrNull()
                if (slot != null) map[slot] = obj.getString(key)
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun setBookmark(slot: Int, path: String) {
        val map = getBookmarks().toMutableMap()
        map[slot] = path
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString(KEY_BOOKMARKS, obj.toString()).apply()
    }

    fun removeBookmark(slot: Int) {
        val map = getBookmarks().toMutableMap()
        map.remove(slot)
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        prefs.edit().putString(KEY_BOOKMARKS, obj.toString()).apply()
    }

    // --- Batch rename pattern history ---

    fun getRenamePatterns(): List<String> {
        val json = prefs.getString(KEY_RENAME_PATTERNS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun addRenamePattern(pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return
        val list = getRenamePatterns().toMutableList()
        list.remove(trimmed)
        list.add(0, trimmed)
        val capped = list.take(MAX_RENAME_PATTERNS)
        prefs.edit().putString(KEY_RENAME_PATTERNS, JSONArray(capped).toString()).apply()
    }

    // --- Tag definitions ---

    fun getTagDefinitions(): List<FileTag> {
        val json = prefs.getString(KEY_TAG_DEFINITIONS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                FileTag(
                    name = obj.getString("name"),
                    colorIndex = obj.getInt("colorIndex")
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveTagDefinitions(tags: List<FileTag>) {
        val arr = JSONArray()
        tags.forEach { tag ->
            arr.put(JSONObject().apply {
                put("name", tag.name)
                put("colorIndex", tag.colorIndex)
            })
        }
        prefs.edit().putString(KEY_TAG_DEFINITIONS, arr.toString()).apply()
    }

    fun addTagDefinition(tag: FileTag) {
        val list = getTagDefinitions().toMutableList()
        if (list.none { it.name == tag.name }) {
            list.add(tag)
            saveTagDefinitions(list)
        }
    }

    fun removeTagDefinition(name: String) {
        val list = getTagDefinitions().filter { it.name != name }
        saveTagDefinitions(list)
        // Also remove from all file mappings
        val mappings = getFileTagMappings().toMutableMap()
        var changed = false
        mappings.forEach { (path, tags) ->
            if (name in tags) {
                mappings[path] = tags - name
                changed = true
            }
        }
        if (changed) {
            // Remove entries with empty tag lists
            val cleaned = mappings.filter { it.value.isNotEmpty() }
            saveFileTagMappings(cleaned)
        }
    }

    fun updateTagDefinition(oldName: String, newTag: FileTag) {
        val list = getTagDefinitions().toMutableList()
        val idx = list.indexOfFirst { it.name == oldName }
        if (idx >= 0) {
            list[idx] = newTag
            saveTagDefinitions(list)
            // Update mappings if name changed
            if (oldName != newTag.name) {
                val mappings = getFileTagMappings().toMutableMap()
                var changed = false
                mappings.forEach { (path, tags) ->
                    if (oldName in tags) {
                        mappings[path] = tags.map { if (it == oldName) newTag.name else it }
                        changed = true
                    }
                }
                if (changed) saveFileTagMappings(mappings)
            }
        }
    }

    // --- File-tag mappings ---

    fun getFileTagMappings(): Map<String, List<String>> {
        val json = prefs.getString(KEY_FILE_TAG_MAPPINGS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<String, List<String>>()
            obj.keys().forEach { path ->
                val arr = obj.getJSONArray(path)
                val tags = (0 until arr.length()).map { arr.getString(it) }
                if (tags.isNotEmpty()) map[path] = tags
            }
            map
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveFileTagMappings(mappings: Map<String, List<String>>) {
        val obj = JSONObject()
        mappings.forEach { (path, tags) ->
            if (tags.isNotEmpty()) {
                obj.put(path, JSONArray(tags))
            }
        }
        prefs.edit().putString(KEY_FILE_TAG_MAPPINGS, obj.toString()).apply()
    }

    fun setFileTags(path: String, tagNames: List<String>) {
        val mappings = getFileTagMappings().toMutableMap()
        if (tagNames.isEmpty()) {
            mappings.remove(path)
        } else {
            mappings[path] = tagNames
        }
        saveFileTagMappings(mappings)
    }

    fun removeFileTags(path: String) {
        val mappings = getFileTagMappings().toMutableMap()
        if (mappings.remove(path) != null) {
            saveFileTagMappings(mappings)
        }
    }

    fun migrateFileTags(oldPath: String, newPath: String) {
        val mappings = getFileTagMappings().toMutableMap()
        val tags = mappings.remove(oldPath) ?: return
        mappings[newPath] = tags
        saveFileTagMappings(mappings)
    }

    // --- App Lock ---

    var appLockMethod: Int
        get() = prefs.getInt(KEY_APP_LOCK_METHOD, 0)
        set(value) = prefs.edit().putInt(KEY_APP_LOCK_METHOD, value).apply()

    var pinHash: String?
        get() = prefs.getString(KEY_PIN_HASH, null)
        set(value) = prefs.edit().putString(KEY_PIN_HASH, value).apply()

    var pinLength: Int
        get() = prefs.getInt(KEY_PIN_LENGTH, 4)
        set(value) = prefs.edit().putInt(KEY_PIN_LENGTH, value).apply()

    // --- Gesture mappings ---

    fun getGestureAction(type: GestureType): GestureAction {
        val name = prefs.getString("${KEY_GESTURE_PREFIX}${type.name}", null)
        return if (name != null) {
            try { GestureAction.valueOf(name) } catch (_: Exception) { type.defaultAction }
        } else {
            type.defaultAction
        }
    }

    fun setGestureAction(type: GestureType, action: GestureAction) {
        prefs.edit().putString("${KEY_GESTURE_PREFIX}${type.name}", action.name).apply()
    }

    fun getGestureMappings(): Map<GestureType, GestureAction> {
        return GestureType.entries.associateWith { getGestureAction(it) }
    }

    // --- Device aliases & trusted devices ---

    fun getDeviceAlias(nsdName: String): String? {
        val json = prefs.getString(KEY_DEVICE_ALIASES, null) ?: return null
        return try {
            JSONObject(json).optString(nsdName, null)
        } catch (_: Exception) {
            null
        }
    }

    fun setDeviceAlias(nsdName: String, alias: String) {
        val obj = try {
            JSONObject(prefs.getString(KEY_DEVICE_ALIASES, null) ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        obj.put(nsdName, alias)
        prefs.edit().putString(KEY_DEVICE_ALIASES, obj.toString()).apply()
    }

    fun removeDeviceAlias(nsdName: String) {
        val obj = try {
            JSONObject(prefs.getString(KEY_DEVICE_ALIASES, null) ?: "{}")
        } catch (_: Exception) {
            JSONObject()
        }
        obj.remove(nsdName)
        prefs.edit().putString(KEY_DEVICE_ALIASES, obj.toString()).apply()
    }

    fun getTrustedDevices(): Set<String> {
        return prefs.getStringSet(KEY_TRUSTED_DEVICES, emptySet()) ?: emptySet()
    }

    fun isDeviceTrusted(nsdName: String): Boolean {
        return nsdName in getTrustedDevices()
    }

    fun setDeviceTrusted(nsdName: String, trusted: Boolean) {
        val set = getTrustedDevices().toMutableSet()
        if (trusted) set.add(nsdName) else set.remove(nsdName)
        prefs.edit().putStringSet(KEY_TRUSTED_DEVICES, set).apply()
    }

    // --- Panel paths ---

    var topPanelPath: String
        get() = prefs.getString(KEY_TOP_PANEL_PATH, HaronConstants.ROOT_PATH) ?: HaronConstants.ROOT_PATH
        set(value) = prefs.edit().putString(KEY_TOP_PANEL_PATH, value).apply()

    var bottomPanelPath: String
        get() = prefs.getString(KEY_BOTTOM_PANEL_PATH, HaronConstants.ROOT_PATH) ?: HaronConstants.ROOT_PATH
        set(value) = prefs.edit().putString(KEY_BOTTOM_PANEL_PATH, value).apply()

    var micFabOffsetX: Float
        get() = prefs.getFloat(KEY_MIC_FAB_OFFSET_X, 0f)
        set(value) = prefs.edit().putFloat(KEY_MIC_FAB_OFFSET_X, value).apply()

    var micFabOffsetY: Float
        get() = prefs.getFloat(KEY_MIC_FAB_OFFSET_Y, 0f)
        set(value) = prefs.edit().putFloat(KEY_MIC_FAB_OFFSET_Y, value).apply()

    var micHintShown: Boolean
        get() = prefs.getBoolean(KEY_MIC_HINT_SHOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_MIC_HINT_SHOWN, value).apply()

    private companion object {
        const val KEY_SORT_FIELD = "sort_field"
        const val KEY_SORT_DIRECTION = "sort_direction"
        const val KEY_SHOW_HIDDEN = "show_hidden"
        const val KEY_GRID_COLUMNS = "grid_columns"
        const val KEY_PANEL_RATIO = "panel_ratio"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RECENT_PATHS = "recent_paths"
        const val KEY_TRASH_MAX_SIZE_MB = "trash_max_size_mb"
        const val KEY_SAF_URIS = "saf_uris"
        const val KEY_SHELF_ITEMS = "shelf_items"
        const val KEY_TOP_PANEL_PATH = "top_panel_path"
        const val KEY_BOTTOM_PANEL_PATH = "bottom_panel_path"
        const val KEY_ORIGINAL_OVERRIDES = "original_overrides"
        const val KEY_ORIGINAL_FOLDERS = "original_folders"
        const val KEY_HAPTIC_ENABLED = "haptic_enabled"
        const val KEY_NIGHT_MODE_ENABLED = "night_mode_enabled"
        const val KEY_NIGHT_MODE_START_HOUR = "night_mode_start_hour"
        const val KEY_NIGHT_MODE_START_MINUTE = "night_mode_start_minute"
        const val KEY_NIGHT_MODE_END_HOUR = "night_mode_end_hour"
        const val KEY_NIGHT_MODE_END_MINUTE = "night_mode_end_minute"
        const val KEY_FONT_SCALE = "font_scale"
        const val KEY_ICON_SCALE = "icon_scale"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_LAST_MEDIA_FILE = "last_media_file"
        const val KEY_LAST_DOCUMENT_FILE = "last_document_file"
        const val KEY_RENAME_PATTERNS = "rename_patterns"
        const val KEY_TAG_DEFINITIONS = "tag_definitions"
        const val KEY_FILE_TAG_MAPPINGS = "file_tag_mappings"
        const val KEY_GESTURE_PREFIX = "gesture_"
        const val KEY_APP_LOCK_METHOD = "app_lock_method"
        const val KEY_PIN_HASH = "pin_hash"
        const val KEY_PIN_LENGTH = "pin_length"
        const val KEY_DEVICE_ALIASES = "device_aliases"
        const val KEY_TRUSTED_DEVICES = "trusted_devices"
        const val KEY_MIC_FAB_OFFSET_X = "mic_fab_offset_x"
        const val KEY_MIC_FAB_OFFSET_Y = "mic_fab_offset_y"
        const val KEY_MIC_HINT_SHOWN = "mic_hint_shown"
        const val MAX_RECENT = 5
        const val MAX_RENAME_PATTERNS = 10
    }
}
