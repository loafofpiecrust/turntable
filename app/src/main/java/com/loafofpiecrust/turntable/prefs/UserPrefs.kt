package com.loafofpiecrust.turntable.prefs

import com.chibatching.kotpref.KotprefModel
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.model.song.HistoryEntry
import com.loafofpiecrust.turntable.serialize.page
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.util.getColorCompat
import io.paperdb.Paper
import kotlinx.collections.immutable.immutableListOf

/**
 * Never change any of the string keys used here.
 */
object UserPrefs: KotprefModel() {
    // Theming
    val useDarkTheme by booleanPref(false, "useDarkTheme")
    val primaryColor by intPref(context.getColorCompat(R.color.md_purple_300), "primaryColor")
//    val secondaryColor by intPref(context.getColorCompat(R.color.md_teal_200))
    val accentColor by intPref(context.getColorCompat(R.color.md_teal_200), "accentColor")

    // Structure
//    val libraryTabs by Paper.page<Set<String>>("libraryTabs") {
//        setOf("Albums", "Artists", "Playlists", "Friends", "Recommendations")
//    }
    val albumGridColumns by intPref(3, "albumGridColumns")
    val artistGridColumns by intPref(3, "artistGridColumns")
    val playlistGridColumns by intPref(1, "playlistGridColumns")

    enum class HQStreamingMode {
        ALWAYS, ONLY_UNMETERED, NEVER
    }
    val hqStreamingMode by Paper.page("hqStreamingMode") {
        HQStreamingMode.ONLY_UNMETERED
    }

    // Artwork
    val downloadArtworkWifiOnly by booleanPref(true, "downloadArtworkWifiOnly")
    val downloadArtworkAuto by booleanPref(true, "downloadArtworkAuto")
    val artworkOnLockscreen by booleanPref(true, "artworkOnLockscreen")
    val reduceVolumeOnFocusLoss by booleanPref(true, "reduceVolumeOnFocusLoss")

    // Headphones
    val pauseOnUnplug by booleanPref(true, "pauseOnUnplug")
    val resumeOnPlug by booleanPref(true, "resumeOnPlug")

    // Sync
    val onlySyncOnWifi by booleanPref(false, "onlySyncOnWifi")

    // Last.FM
    val doScrobble by booleanPref(false, "scrobble")

    val sdCardUri by Paper.page("sdCardUri") { "" }

    // Metadata (saves to files rather than SharedPreferences to reduce memory usage)
    val history by Paper.page("history") {
        immutableListOf<HistoryEntry>()
    }
    val playlists by Paper.page("playlists") {
        immutableListOf<Playlist>()
    }
    val recommendations by Paper.page("recommendations") {
        immutableListOf<Recommendable>()
    }

    val lastOpenTime by longPref(System.currentTimeMillis(), "lastOpenTime")
    val currentOpenTime by longPref(System.currentTimeMillis(), "currentOpenTime")

//    val bufferState by pref(MusicPlayer.BufferState(0, 0, 0))
}