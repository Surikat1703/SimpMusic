package com.maxrave.simpmusic.utils

import com.maxrave.simpmusic.ui.navigation.destination.home.AnalyticsDestination
import com.maxrave.simpmusic.ui.navigation.destination.home.HomeDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.LibraryDestination
import com.maxrave.simpmusic.ui.navigation.destination.library.MixForYouDestination
import com.maxrave.simpmusic.ui.navigation.destination.search.SearchDestination

/**
 * Fork: the tab the app opens on.
 *
 * Stored as a raw DataStore string through [com.maxrave.domain.manager.DataStoreManager.putString],
 * so the preference needs no schema change inside the `core` submodule.
 */
object DefaultTabPreference {
    const val KEY = "default_start_tab"

    const val HOME = "HOME"
    const val MIX = "MIX"
    const val ANALYTICS = "ANALYTICS"
    const val LIBRARY = "LIBRARY"
    const val SEARCH = "SEARCH"

    val values = listOf(HOME, MIX, ANALYTICS, LIBRARY, SEARCH)

    /**
     * [MIX] and [ANALYTICS] are gated tabs, so a stored value for one of them can point at a tab
     * that is not on screen. Callers pass the gate as [available] and fall back to Home.
     */
    fun destinationOf(
        value: String?,
        mixAvailable: Boolean,
        analyticsAvailable: Boolean,
    ): Any =
        when (value) {
            MIX -> if (mixAvailable) MixForYouDestination else HomeDestination
            ANALYTICS -> if (analyticsAvailable) AnalyticsDestination else HomeDestination
            LIBRARY -> LibraryDestination
            SEARCH -> SearchDestination
            else -> HomeDestination
        }
}
