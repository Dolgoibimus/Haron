package com.vamp.haron.common.constants

import com.vamp.haron.BuildConfig

/**
 * Feature flags based on build flavor.
 * full: all features (RuStore, 4PDA, GitHub)
 * play: restricted (Google Play safe)
 */
object FeatureFlags {
    /** Torrent client — sequential download + streaming */
    val hasTorrent: Boolean get() = BuildConfig.HAS_TORRENT

    /** Root access — browse /system, /data etc. */
    val hasRootAccess: Boolean get() = BuildConfig.HAS_ROOT_ACCESS

    /** Front camera photo on wrong PIN attempt */
    val hasHiddenCamera: Boolean get() = BuildConfig.HAS_HIDDEN_CAMERA

    /** Floating mini-explorer over other apps */
    val hasFloatingWindow: Boolean get() = BuildConfig.HAS_FLOATING_WINDOW

    /** true if this is the full (unrestricted) build */
    val isFullBuild: Boolean get() = BuildConfig.FLAVOR == "full"
}
