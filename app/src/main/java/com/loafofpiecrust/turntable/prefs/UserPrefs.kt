package com.loafofpiecrust.turntable.prefs

import com.chibatching.kotpref.KotprefModel
import com.loafofpiecrust.turntable.model.Recommendation
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.song.HistoryEntry
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.sync.Friend
import com.loafofpiecrust.turntable.model.sync.User
import org.jetbrains.anko.colorAttr

object UserPrefs: KotprefModel() {
    // Theming
    val useDarkTheme by booleanPref(true)
    val primaryColor by intPref(context.colorAttr(android.R.attr.colorPrimary))
    val secondaryColor by intPref(context.colorAttr(android.R.attr.colorAccent))
    val accentColor by intPref(context.colorAttr(android.R.attr.colorAccent))

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

    val remoteAlbums by pref(emptyList<Album>())
    // TODO: Save metadata as Map<MusicId, Metadata> instead of a list.
    val albumMeta by pref(emptyList<Library.AlbumMetadata>())
    val artistMeta by pref(emptyList<Library.ArtistMetadata>())
    val history by pref(emptyList<HistoryEntry>())
    val playlists by pref(emptyList<Playlist>())
    val recommendations by pref(emptyList<Recommendation>())

    val friends by pref(emptyMap<User, Friend.Status>())

    val queue by pref(
        CombinedQueue(StaticQueue(emptyList(), 0), emptyList())
    )

//    val bufferState by pref(MusicPlayer.BufferState(0, 0, 0))
}