package com.loafofpiecrust.turntable.prefs

import activitystarter.MakeActivityStarter
import android.os.Bundle
import android.preference.PreferenceFragment
import com.loafofpiecrust.turntable.BuildConfig
import com.loafofpiecrust.turntable.R


@MakeActivityStarter
class MainPrefsFragment : PreferenceFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Prefs needed:
        // Color palette/theme (primary, secondary, accent?)
        // Dark or light
        // Library tabs
        // lockscreen artwork (boolean = true)
        // ignore playlist duplicates (boolean = true)
        // Download artwork on wi-fi only
        // trigger artwork download
        // clean artwork cache
        // Headphones: pause on disconnect, resume on connect
        // bluetooth: ^^
        // enable/disable scrobbling
        // library shuffle blacklist
        // Page to start on: A tab or last opened tab
        // reduce volume on focus loss (bool = true)
        // only allow sync mode on wifi (bool = false)
        //
        preferences {
            category("Theme") {
                switch(UserPrefs.useDarkTheme, R.string.pref_dark_theme)
                color(UserPrefs.primaryColor, "Primary Color")
                color(UserPrefs.accentColor, "Accent Color")

                // FIXME: Use a preference type that allows editing a List<T>
//                multiSelectList(LibraryFragment.tabs, "Library Tabs") {
//                    entries = arrayOf("Songs", "Albums", "Artists", "Playlists", "Friends", "Recommendations")
//                    entryValues = entries
//                }
            }

            category("Headphones") {
                switch(UserPrefs.pauseOnUnplug, "Pause on unplug")
                switch(UserPrefs.resumeOnPlug, "Resume on replug")
            }

            category("Album/Artist Artwork") {
                switch(UserPrefs.downloadArtworkAuto, "Download missing artwork")
                switch(UserPrefs.reduceVolumeOnFocusLoss, "Reduce volume on focus loss")
                if (BuildConfig.DEBUG) {
                    switch(UserPrefs.artworkOnLockscreen, "Show on lockscreen")
                }
            }

            list(
                UserPrefs.hqStreamingMode,
                "High Quality Streaming",
                transform = { UserPrefs.HQStreamingMode.valueOf(it) }
            ) {
                entries = UserPrefs.HQStreamingMode.values().map { it.name }.toTypedArray()
                entryValues = entries
            }
        }
    }
}