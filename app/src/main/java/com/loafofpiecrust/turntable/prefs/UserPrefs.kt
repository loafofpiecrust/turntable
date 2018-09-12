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

    val remoteAlbums by pref(emptyList<Album>())
    val albumMeta by pref(emptyList<Library.AlbumMetadata>())
    val artistMeta by pref(emptyList<Library.ArtistMetadata>())
    val history by pref(emptyList<HistoryEntry>())
    val playlists by pref(emptyList<Playlist>())
    val recommendations by pref(emptyList<Music>())

    val friends by pref(emptyList<SyncService.Friend>())

    val queue by pref<MusicPlayer.Queue>(StaticQueue(emptyList(), 0))
}