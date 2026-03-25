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

    // --- Marquee (scrolling long file names) ---

    var marqueeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MARQUEE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MARQUEE_ENABLED, value).apply()

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

    fun getLibraryGridColumns(tab: Int): Int = prefs.getInt("${KEY_LIBRARY_GRID_COLUMNS}_$tab", 3)
    fun setLibraryGridColumns(tab: Int, columns: Int) = prefs.edit().putInt("${KEY_LIBRARY_GRID_COLUMNS}_$tab", columns).apply()

    /** Folders manually removed by user — won't reappear after rescan */
    var libraryRemovedFolders: Set<String>
        get() = prefs.getStringSet("library_removed_folders", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("library_removed_folders", value).apply()

    /** Folders excluded from scanning */
    var libraryExcludedFolders: Set<String>
        get() = prefs.getStringSet("library_excluded_folders", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("library_excluded_folders", value).apply()

    /** Custom navbar configuration (JSON) */
    fun getNavbarConfig(): com.vamp.haron.domain.model.NavbarConfig {
        val json = prefs.getString("navbar_config", null) ?: return com.vamp.haron.domain.model.NavbarConfig()
        return try {
            val root = JSONObject(json)
            val pagesArr = root.getJSONArray("pages")
            val pages = (0 until pagesArr.length()).map { pi ->
                val pageObj = pagesArr.getJSONObject(pi)
                val btnsArr = pageObj.getJSONArray("buttons")
                val buttons = (0 until btnsArr.length()).map { bi ->
                    val btnObj = btnsArr.getJSONObject(bi)
                    com.vamp.haron.domain.model.NavbarButton(
                        tapAction = com.vamp.haron.domain.model.NavbarAction.valueOf(btnObj.optString("tap", "NONE")),
                        longAction = com.vamp.haron.domain.model.NavbarAction.valueOf(btnObj.optString("long", "NONE"))
                    )
                }
                com.vamp.haron.domain.model.NavbarPage(buttons)
            }
            com.vamp.haron.domain.model.NavbarConfig(pages)
        } catch (_: Exception) {
            com.vamp.haron.domain.model.NavbarConfig()
        }
    }

    fun setNavbarConfig(config: com.vamp.haron.domain.model.NavbarConfig) {
        val root = JSONObject()
        val pagesArr = JSONArray()
        for (page in config.pages) {
            val pageObj = JSONObject()
            val btnsArr = JSONArray()
            for (btn in page.buttons) {
                btnsArr.put(JSONObject().apply {
                    put("tap", btn.tapAction.name)
                    put("long", btn.longAction.name)
                })
            }
            pageObj.put("buttons", btnsArr)
            pagesArr.put(pageObj)
        }
        root.put("pages", pagesArr)
        prefs.edit().putString("navbar_config", root.toString()).apply()
    }

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

    var securityQuestion: String?
        get() = prefs.getString(KEY_SECURITY_QUESTION, null)
        set(value) = prefs.edit().putString(KEY_SECURITY_QUESTION, value).apply()

    var securityAnswerHash: String?
        get() = prefs.getString(KEY_SECURITY_ANSWER_HASH, null)
        set(value) = prefs.edit().putString(KEY_SECURITY_ANSWER_HASH, value).apply()

    var requirePinOnLaunch: Boolean
        get() = prefs.getBoolean(KEY_REQUIRE_PIN_ON_LAUNCH, true)
        set(value) = prefs.edit().putBoolean(KEY_REQUIRE_PIN_ON_LAUNCH, value).apply()

    var lockTimeoutMinutes: Int
        get() = prefs.getInt(KEY_LOCK_TIMEOUT_MINUTES, 30)
        set(value) = prefs.edit().putInt(KEY_LOCK_TIMEOUT_MINUTES, value).apply()

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

    var wakeWordEnabled: Boolean
        get() = prefs.getBoolean(KEY_WAKE_WORD_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_WAKE_WORD_ENABLED, value).apply()

    // --- Content-indexed folders cache ---

    fun getContentIndexedFolders(): Set<String> =
        prefs.getStringSet(KEY_CONTENT_INDEXED_FOLDERS, emptySet()) ?: emptySet()

    fun addContentIndexedFolder(path: String) {
        val set = getContentIndexedFolders().toMutableSet()
        set.add(path)
        prefs.edit().putStringSet(KEY_CONTENT_INDEXED_FOLDERS, set).apply()
    }

    fun removeContentIndexedFolder(path: String) {
        val set = getContentIndexedFolders().toMutableSet()
        if (set.remove(path)) {
            prefs.edit().putStringSet(KEY_CONTENT_INDEXED_FOLDERS, set).apply()
        }
    }

    var hotspotSsid: String
        get() = prefs.getString(KEY_HOTSPOT_SSID, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOTSPOT_SSID, value).apply()

    var hotspotPassword: String
        get() = prefs.getString(KEY_HOTSPOT_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_HOTSPOT_PASSWORD, value).apply()

    // --- Transcode cache ---

    var transcodeCacheTtlHours: Int
        get() = prefs.getInt(KEY_TRANSCODE_CACHE_TTL_HOURS, 24)
        set(value) = prefs.edit().putInt(KEY_TRANSCODE_CACHE_TTL_HOURS, value).apply()

    // --- Player DND settings (separate for video/audio) ---

    private fun dndKey(base: String, isVideo: Boolean) = "${if (isVideo) "v" else "a"}dnd_$base"

    fun playerDndEnabled(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("enabled", isVideo), false)
    fun setPlayerDndEnabled(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("enabled", isVideo), value).apply()

    fun playerDndAllowCalls(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("calls", isVideo), false)
    fun setPlayerDndAllowCalls(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("calls", isVideo), value).apply()

    fun playerDndAllowMessages(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("messages", isVideo), false)
    fun setPlayerDndAllowMessages(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("messages", isVideo), value).apply()

    fun playerDndAllowAlarms(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("alarms", isVideo), true)
    fun setPlayerDndAllowAlarms(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("alarms", isVideo), value).apply()

    fun playerDndAllowRepeatCallers(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("repeat", isVideo), false)
    fun setPlayerDndAllowRepeatCallers(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("repeat", isVideo), value).apply()

    /** 0=none, 1=starred, 2=contacts, 3=any */
    fun playerDndCallSenders(isVideo: Boolean): Int = prefs.getInt(dndKey("senders", isVideo), 0)
    fun setPlayerDndCallSenders(isVideo: Boolean, value: Int) = prefs.edit().putInt(dndKey("senders", isVideo), value).apply()

    fun playerDndSuppressHeadsUp(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("heads_up", isVideo), true)
    fun setPlayerDndSuppressHeadsUp(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("heads_up", isVideo), value).apply()

    fun playerDndSuppressStatusBar(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("status_bar", isVideo), false)
    fun setPlayerDndSuppressStatusBar(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("status_bar", isVideo), value).apply()

    fun playerDndSuppressScreenOn(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("screen_on", isVideo), true)
    fun setPlayerDndSuppressScreenOn(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("screen_on", isVideo), value).apply()

    fun playerDndSilentMode(isVideo: Boolean): Boolean = prefs.getBoolean(dndKey("silent", isVideo), true)
    fun setPlayerDndSilentMode(isVideo: Boolean, value: Boolean) = prefs.edit().putBoolean(dndKey("silent", isVideo), value).apply()

    /** Archive thumbnail cache max size in MB (0 = no limit). Default 100 MB. */
    var archiveThumbCacheSizeMb: Int
        get() = prefs.getInt(KEY_ARCHIVE_THUMB_CACHE_SIZE_MB, 100)
        set(value) = prefs.edit().putInt(KEY_ARCHIVE_THUMB_CACHE_SIZE_MB, value.coerceAtLeast(0)).apply()

    // --- Power saving ---

    /** Global power save mode: disables animations, reduces polling frequency */
    var powerSaveEnabled: Boolean
        get() = prefs.getBoolean(KEY_POWER_SAVE, false)
        set(value) = prefs.edit().putBoolean(KEY_POWER_SAVE, value).apply()

    /** Battery threshold (%) below which animations auto-disable. 0 = off. Default 15%. */
    var animBatteryThreshold: Int
        get() = prefs.getInt(KEY_ANIM_BATTERY_THRESHOLD, 15)
        set(value) = prefs.edit().putInt(KEY_ANIM_BATTERY_THRESHOLD, value.coerceIn(0, 100)).apply()

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
        const val KEY_MARQUEE_ENABLED = "marquee_enabled"
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
        const val KEY_WAKE_WORD_ENABLED = "wake_word_enabled"
        const val KEY_SECURITY_QUESTION = "security_question"
        const val KEY_SECURITY_ANSWER_HASH = "security_answer_hash"
        const val KEY_REQUIRE_PIN_ON_LAUNCH = "require_pin_on_launch"
        const val KEY_LOCK_TIMEOUT_MINUTES = "lock_timeout_minutes"
        const val KEY_CONTENT_INDEXED_FOLDERS = "content_indexed_folders"
        const val KEY_HOTSPOT_SSID = "hotspot_ssid"
        const val KEY_HOTSPOT_PASSWORD = "hotspot_password"
        const val KEY_TRANSCODE_CACHE_TTL_HOURS = "transcode_cache_ttl_hours"
        const val KEY_ARCHIVE_THUMB_CACHE_SIZE_MB = "archive_thumb_cache_size_mb"
        const val KEY_LIBRARY_GRID_COLUMNS = "library_grid_columns"
        const val KEY_MATRIX_ENABLED = "matrix_enabled"
        const val KEY_MATRIX_MODE = "matrix_mode"
        const val KEY_MATRIX_COLOR = "matrix_color"
        const val KEY_MATRIX_SPEED = "matrix_speed"
        const val KEY_MATRIX_DENSITY = "matrix_density"
        const val KEY_MATRIX_OPACITY = "matrix_opacity"
        const val KEY_MATRIX_CHARSET = "matrix_charset"
        const val KEY_MATRIX_ONLY_CHARGING = "matrix_only_charging"
        const val KEY_SNOWFALL_ENABLED = "snowfall_enabled"
        const val KEY_SNOWFALL_SPEED = "snowfall_speed"
        const val KEY_SNOWFALL_DENSITY = "snowfall_density"
        const val KEY_SNOWFALL_OPACITY = "snowfall_opacity"
        const val KEY_SNOWFALL_SIZE = "snowfall_size"
        const val KEY_SNOWFALL_ONLY_CHARGING = "snowfall_only_charging"
        const val KEY_STARFIELD_ENABLED = "starfield_enabled"
        const val KEY_STARFIELD_SPEED = "starfield_speed"
        const val KEY_STARFIELD_DENSITY = "starfield_density"
        const val KEY_STARFIELD_OPACITY = "starfield_opacity"
        const val KEY_STARFIELD_SIZE = "starfield_size"
        const val KEY_STARFIELD_ONLY_CHARGING = "starfield_only_charging"
        const val KEY_DUST_ENABLED = "dust_enabled"
        const val KEY_DUST_SPEED = "dust_speed"
        const val KEY_DUST_DENSITY = "dust_density"
        const val KEY_DUST_OPACITY = "dust_opacity"
        const val KEY_DUST_SIZE = "dust_size"
        const val KEY_DUST_ONLY_CHARGING = "dust_only_charging"
        const val KEY_POWER_SAVE = "power_save_enabled"
        const val KEY_ANIM_BATTERY_THRESHOLD = "anim_battery_threshold"
        const val MAX_RECENT = 5
        const val MAX_RENAME_PATTERNS = 10
    }

    // --- Matrix Rain ---

    var matrixEnabled: Boolean
        get() = prefs.getBoolean(KEY_MATRIX_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_MATRIX_ENABLED, value).apply()

    var matrixMode: Int
        get() = prefs.getInt(KEY_MATRIX_MODE, 1)
        set(value) = prefs.edit().putInt(KEY_MATRIX_MODE, value).apply()

    var matrixColor: Long
        get() = prefs.getLong(KEY_MATRIX_COLOR, 0xFF00FF00L)
        set(value) = prefs.edit().putLong(KEY_MATRIX_COLOR, value).apply()

    var matrixSpeed: Float
        get() = prefs.getFloat(KEY_MATRIX_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_MATRIX_SPEED, value).apply()

    var matrixDensity: Float
        get() = prefs.getFloat(KEY_MATRIX_DENSITY, 0.6f)
        set(value) = prefs.edit().putFloat(KEY_MATRIX_DENSITY, value).apply()

    var matrixOpacity: Float
        get() = prefs.getFloat(KEY_MATRIX_OPACITY, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_MATRIX_OPACITY, value).apply()

    var matrixCharset: String
        get() = prefs.getString(KEY_MATRIX_CHARSET, "katakana") ?: "katakana"
        set(value) = prefs.edit().putString(KEY_MATRIX_CHARSET, value).apply()

    var matrixOnlyCharging: Boolean
        get() = prefs.getBoolean(KEY_MATRIX_ONLY_CHARGING, false)
        set(value) = prefs.edit().putBoolean(KEY_MATRIX_ONLY_CHARGING, value).apply()

    // --- Snowfall ---

    var snowfallEnabled: Boolean
        get() = prefs.getBoolean(KEY_SNOWFALL_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SNOWFALL_ENABLED, value).apply()

    var snowfallSpeed: Float
        get() = prefs.getFloat(KEY_SNOWFALL_SPEED, 1f)
        set(value) = prefs.edit().putFloat(KEY_SNOWFALL_SPEED, value).apply()

    var snowfallDensity: Float
        get() = prefs.getFloat(KEY_SNOWFALL_DENSITY, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_SNOWFALL_DENSITY, value).apply()

    var snowfallOpacity: Float
        get() = prefs.getFloat(KEY_SNOWFALL_OPACITY, 0.7f)
        set(value) = prefs.edit().putFloat(KEY_SNOWFALL_OPACITY, value).apply()

    var snowfallSize: Float
        get() = prefs.getFloat(KEY_SNOWFALL_SIZE, 1f)
        set(value) = prefs.edit().putFloat(KEY_SNOWFALL_SIZE, value).apply()

    var snowfallOnlyCharging: Boolean
        get() = prefs.getBoolean(KEY_SNOWFALL_ONLY_CHARGING, false)
        set(value) = prefs.edit().putBoolean(KEY_SNOWFALL_ONLY_CHARGING, value).apply()

    // --- Starfield ---
    var starfieldEnabled: Boolean get() = prefs.getBoolean(KEY_STARFIELD_ENABLED, false); set(v) = prefs.edit().putBoolean(KEY_STARFIELD_ENABLED, v).apply()
    var starfieldSpeed: Float get() = prefs.getFloat(KEY_STARFIELD_SPEED, 1f); set(v) = prefs.edit().putFloat(KEY_STARFIELD_SPEED, v).apply()
    var starfieldDensity: Float get() = prefs.getFloat(KEY_STARFIELD_DENSITY, 0.5f); set(v) = prefs.edit().putFloat(KEY_STARFIELD_DENSITY, v).apply()
    var starfieldOpacity: Float get() = prefs.getFloat(KEY_STARFIELD_OPACITY, 0.6f); set(v) = prefs.edit().putFloat(KEY_STARFIELD_OPACITY, v).apply()
    var starfieldSize: Float get() = prefs.getFloat(KEY_STARFIELD_SIZE, 1f); set(v) = prefs.edit().putFloat(KEY_STARFIELD_SIZE, v).apply()
    var starfieldOnlyCharging: Boolean get() = prefs.getBoolean(KEY_STARFIELD_ONLY_CHARGING, false); set(v) = prefs.edit().putBoolean(KEY_STARFIELD_ONLY_CHARGING, v).apply()

    // --- Dust ---
    var dustEnabled: Boolean get() = prefs.getBoolean(KEY_DUST_ENABLED, false); set(v) = prefs.edit().putBoolean(KEY_DUST_ENABLED, v).apply()
    var dustSpeed: Float get() = prefs.getFloat(KEY_DUST_SPEED, 1f); set(v) = prefs.edit().putFloat(KEY_DUST_SPEED, v).apply()
    var dustDensity: Float get() = prefs.getFloat(KEY_DUST_DENSITY, 0.5f); set(v) = prefs.edit().putFloat(KEY_DUST_DENSITY, v).apply()
    var dustOpacity: Float get() = prefs.getFloat(KEY_DUST_OPACITY, 0.5f); set(v) = prefs.edit().putFloat(KEY_DUST_OPACITY, v).apply()
    var dustSize: Float get() = prefs.getFloat(KEY_DUST_SIZE, 1f); set(v) = prefs.edit().putFloat(KEY_DUST_SIZE, v).apply()
    var dustOnlyCharging: Boolean get() = prefs.getBoolean(KEY_DUST_ONLY_CHARGING, false); set(v) = prefs.edit().putBoolean(KEY_DUST_ONLY_CHARGING, v).apply()
}
