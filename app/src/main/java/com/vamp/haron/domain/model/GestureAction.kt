package com.vamp.haron.domain.model

import com.vamp.haron.R

/**
 * Actions that can be assigned to gestures.
 * Each action has a string resource key for display in settings.
 */
enum class GestureAction(val labelRes: Int) {
    NONE(R.string.gesture_action_none),
    OPEN_DRAWER(R.string.gesture_action_open_drawer),
    OPEN_SHELF(R.string.gesture_action_open_shelf),
    TOGGLE_HIDDEN(R.string.gesture_action_toggle_hidden),
    CREATE_NEW(R.string.gesture_action_create_new),
    GLOBAL_SEARCH(R.string.gesture_action_global_search),
    OPEN_TERMINAL(R.string.gesture_action_terminal),
    SELECT_ALL(R.string.gesture_action_select_all),
    REFRESH(R.string.gesture_action_refresh),
    GO_HOME(R.string.gesture_action_go_home),
    SORT_CYCLE(R.string.gesture_action_sort_cycle),
    OPEN_SETTINGS(R.string.gesture_action_settings),
    OPEN_TRANSFER(R.string.gesture_action_transfer),
    OPEN_TRASH(R.string.gesture_action_trash),
    OPEN_STORAGE(R.string.gesture_action_storage),
    OPEN_DUPLICATES(R.string.gesture_action_duplicates),
    OPEN_APPS(R.string.gesture_action_apps),
    OPEN_SCANNER(R.string.gesture_action_scanner),
    OPEN_LOGS(R.string.gesture_action_logs),
    LOGS_PAUSE(R.string.gesture_action_logs_pause),
    LOGS_RESUME(R.string.gesture_action_logs_resume),
    /** Voice-only: sort with specific field/direction parsed from speech. */
    SORT_SPECIFIC(R.string.gesture_action_sort_cycle),

    // --- Voice Level 1 + Level 2 ---
    NAVIGATE_BACK(R.string.gesture_action_navigate_back),
    NAVIGATE_UP(R.string.gesture_action_navigate_up),
    DELETE_SELECTED(R.string.gesture_action_delete_selected),
    COPY_SELECTED(R.string.gesture_action_copy_selected),
    MOVE_SELECTED(R.string.gesture_action_move_selected),
    RENAME(R.string.gesture_action_rename),
    CREATE_ARCHIVE(R.string.gesture_action_create_archive),
    EXTRACT_ARCHIVE(R.string.gesture_action_extract_archive),
    FILE_PROPERTIES(R.string.gesture_action_file_properties),
    DESELECT_ALL(R.string.gesture_action_deselect_all),
    /** Voice-only: navigate to folder by name with fuzzy matching. */
    NAVIGATE_TO_FOLDER(R.string.gesture_action_navigate_to_folder);

    /** Actions that navigate to a separate screen (handled at NavHost level for voice). */
    val isScreenNavigation: Boolean get() = this in SCREEN_NAV_ACTIONS

    companion object {
        private val SCREEN_NAV_ACTIONS = setOf(
            OPEN_SETTINGS, OPEN_TERMINAL, OPEN_TRANSFER, GLOBAL_SEARCH,
            OPEN_STORAGE, OPEN_DUPLICATES, OPEN_APPS, OPEN_SCANNER,
            OPEN_LOGS
        )
        /** Actions that don't navigate but are handled globally */
        val GLOBAL_ACTIONS = setOf(LOGS_PAUSE, LOGS_RESUME)
    }
}

/**
 * Gesture types: 4 edge-swipe zones.
 * Each has a display label and a default action.
 */
enum class GestureType(val labelRes: Int, val defaultAction: GestureAction) {
    LEFT_EDGE_TOP(R.string.gesture_left_edge_top, GestureAction.OPEN_SHELF),
    LEFT_EDGE_BOTTOM(R.string.gesture_left_edge_bottom, GestureAction.OPEN_DRAWER),
    RIGHT_EDGE_TOP(R.string.gesture_right_edge_top, GestureAction.TOGGLE_HIDDEN),
    RIGHT_EDGE_BOTTOM(R.string.gesture_right_edge_bottom, GestureAction.REFRESH)
}
