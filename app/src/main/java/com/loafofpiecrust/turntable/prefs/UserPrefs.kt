package com.loafofpiecrust.turntable.prefs

import com.chibatching.kotpref.KotprefModel
import com.chibatching.kotpref.preference
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.queue.CombinedQueue
import com.loafofpiecrust.turntable.model.song.HistoryEntry
import com.loafofpiecrust.turntable.model.queue.StaticQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.util.getColorCompat
import java.util.*

object UserPrefs: KotprefModel() {
    // Theming
    val useDarkTheme by booleanPref(true)
    val primaryColor by intPref(context.getColorCompat(R.color.md_purple_300))
//    val secondaryColor by intPref(context.getColorCompat(R.color.md_teal_200))
    val accentColor by intPref(context.getColorCompat(R.color.md_teal_200))

    // Structure
    val libraryTabs by preference(
        setOf("Albums", "Artists", "Playlists", "Friends", "Recommendations")
    )
    val albumGridColumns by intPref(3)
    val artistGridColumns by intPref(3)
    val playlistGridColumns by intPref(1)


    enum class HQStreamingMode {
        ALWAYS, ONLY_UNMETERED, NEVER
    }
    val hqStreamingMode by preference(HQStreamingMode.ONLY_UNMETERED)

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

    val sdCardUri by preference("")

    // Metadata (saves to files rather than SharedPreferences to reduce memory usage)

    val remoteAlbums by preference(emptyList<Album>())
    // TODO: Save metadata as Map<MusicId, Metadata> instead of a list.
    val albumMeta by preference(emptyList<Library.AlbumMetadata>())
    val artistMeta by preference(emptyList<Library.ArtistMetadata>())
    val history by preference(emptyList<HistoryEntry>())
    val playlists by preference(emptyList<Playlist>())
    val recommendations by preference(emptyList<Recommendable>())

    val queue by preference(
        CombinedQueue(StaticQueue(emptyList(), 0), emptyList())
    )

    val lastOpenTime by longPref(System.currentTimeMillis())
    val currentOpenTime by longPref(System.currentTimeMillis())

//    val bufferState by pref(MusicPlayer.BufferState(0, 0, 0))
}