package com.vamp.haron.domain.model

import com.vamp.haron.R

/**
 * Actions assignable to custom navbar buttons.
 * Each has a label resource and an icon name for display.
 */
enum class NavbarAction(val labelRes: Int, val iconName: String) {
    NONE(R.string.navbar_action_none, "none"),
    BACK(R.string.navbar_action_back, "back"),
    FORWARD(R.string.navbar_action_forward, "forward"),
    UP(R.string.navbar_action_up, "up"),
    HOME(R.string.navbar_action_home, "home"),
    EXIT(R.string.navbar_action_exit, "exit"),
    REFRESH(R.string.navbar_action_refresh, "refresh"),
    SEARCH(R.string.navbar_action_search, "search"),
    SETTINGS(R.string.navbar_action_settings, "settings"),
    TERMINAL(R.string.navbar_action_terminal, "terminal"),
    LIBRARY(R.string.navbar_action_library, "library"),
    TRANSFER(R.string.navbar_action_transfer, "transfer"),
    TRASH(R.string.navbar_action_trash, "trash"),
    STORAGE(R.string.navbar_action_storage, "storage"),
    APPS(R.string.navbar_action_apps, "apps"),
    DUPLICATES(R.string.navbar_action_duplicates, "duplicates"),
    SCANNER(R.string.navbar_action_scanner, "scanner"),
    SELECT_ALL(R.string.navbar_action_select_all, "select_all"),
    TOGGLE_HIDDEN(R.string.navbar_action_hidden, "hidden"),
    CREATE_NEW(R.string.navbar_action_create, "create"),
    COPY(R.string.navbar_action_copy, "copy"),
    MOVE(R.string.navbar_action_move, "move"),
    DELETE(R.string.navbar_action_delete, "delete"),
    RENAME(R.string.navbar_action_rename, "rename"),
    APP_ICON(R.string.navbar_action_app_icon, "app_icon"),
}

/**
 * Single button config: tap action + long-press action.
 */
data class NavbarButton(
    val tapAction: NavbarAction = NavbarAction.NONE,
    val longAction: NavbarAction = NavbarAction.NONE
)

/**
 * One navbar page: 5-7 buttons.
 */
data class NavbarPage(
    val buttons: List<NavbarButton> = List(5) { NavbarButton() }
)

/**
 * Full navbar config: list of pages.
 */
data class NavbarConfig(
    val pages: List<NavbarPage> = listOf(
        // Default page 1: [Back/Exit] [?] [AppIcon] [?] [?]
        NavbarPage(listOf(
            NavbarButton(NavbarAction.BACK, NavbarAction.EXIT),
            NavbarButton(),
            NavbarButton(NavbarAction.APP_ICON),
            NavbarButton(),
            NavbarButton()
        )),
        // Default page 2: empty
        NavbarPage()
    )
)
