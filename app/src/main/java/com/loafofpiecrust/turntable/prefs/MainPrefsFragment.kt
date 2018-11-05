package com.loafofpiecrust.turntable.prefs

import activitystarter.MakeActivityStarter
import android.os.Bundle
import android.preference.PreferenceFragment
import com.chibatching.kotpref.KotprefModel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch


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
                switch(UserPrefs.useDarkTheme, "Dark Base Theme")
                color(UserPrefs.primaryColor, "Primary Color")
                color(UserPrefs.accentColor, "Accent Color")

                multiSelectList(UserPrefs.libraryTabs, "Library Tabs") {
                    entries = arrayOf("Songs", "Albums", "Artists", "Playlists", "Friends", "Recommendations")
                    entryValues = entries
                }
            }

            category("Headphones") {
                switch(UserPrefs.pauseOnUnplug, "Pause on unplug")
                switch(UserPrefs.resumeOnPlug, "Resume on replug")
            }

            category("Album/Artist Artwork") {
                switch(UserPrefs.downloadArtworkWifiOnly, "Download on WiFi only")
                switch(UserPrefs.artworkOnLockscreen, "Show on lockscreen")
                switch(UserPrefs.reduceVolumeOnFocusLoss, "Reduce volume on focus loss")
            }

            list(UserPrefs.hqStreamingMode, "High Quality Streaming") {
                entries = UserPrefs.HQStreamingMode.values().map { it.name }.toTypedArray()
                entryValues = entries
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GlobalScope.launch {
            KotprefModel.saveFiles()
        }
    }
}