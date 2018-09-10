package com.loafofpiecrust.turntable.prefs

import com.chibatching.kotpref.KotprefModel
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.util.getColorCompat
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.StaticQueue
import com.loafofpiecrust.turntable.playlist.Playlist
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.HistoryEntry
import com.loafofpiecrust.turntable.song.Music

object UserPrefs: KotprefModel() {
    // Theming
    val useDarkTheme by booleanPref(true)
    val primaryColor by intPref(context.getColorCompat(R.color.primary))
    val secondaryColor by intPref(context.getColorCompat(R.color.accent))
    val accentColor by intPref(context.getColorCompat(R.color.accent))

    // Structure
    val libraryTabs by pref(setOf("Albums", "Artists"))
    val albumGridColumns by intPref(3)
    val artistGridColumns by intPref(3)
    val playlistGridColumns by intPref(1)


    enum class HQStreamingMode {
        ALWAYS, ONLY_UNMETERED, NEVER
    }
    val hqStreamingMode by stringPref(HQStreamingMode.ONLY_UNMETERED.name)

    // Artwork
    val downloadArtworkWifiOnly by booleanPref(true)
    val artworkOnLockscreen by booleanPref(true)
    val reduceVolumeOnFocusLoss by booleanPref(true)

    // Headphones
    val pauseOnUnplug by booleanPref(true)
    val resumeOnPlug by booleanPref(true)


    // Sync
    val onlySyncOnWifi by booleanPref(false)

    // Last.FM
    val doScrobble by booleanPref(false)

    val sdCardUri by pref("")

    // Metadata (saves to files rather than SharedPreferences to reduce memory usage)
//    val playlists by jsonPref(arrayOf<Playlist>())

    val remoteAlbums by pref(listOf<Album>())
    val albumMeta by pref(listOf<Library.AlbumMetadata>())
    val artistMeta by pref(listOf<Library.ArtistMetadata>())
    val history by pref(listOf<HistoryEntry>())
    val playlists by pref(listOf<Playlist>())
    val recommendations by pref(listOf<Music>())

    val friends by pref(listOf<SyncService.Friend>())

    val queue by pref<MusicPlayer.Queue>(StaticQueue(listOf(), 0))
}