package com.loafofpiecrust.turntable.song

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.v7.graphics.Palette
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.album.LocalAlbum
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.util.produceTask
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import java.util.concurrent.TimeUnit


interface Song: Music {
    val id: SongId
    val track: Int
    val disc: Int
    val duration: Int // milliseconds
    val year: Int?
    val artworkUrl: String?
    /**
     * Comparable/sortable value for disc and track
     * Assumes that a disc will never have over 999 tracks.
     * Example: Disc 2, Track 27 => 2027
     */
    val discTrack: Int get() = disc * 1000 + track

    override val simpleName get() = id.displayName

    data class Media(val url: String, val start: Int = 0, val end: Int = 0)
    suspend fun loadMedia(): Media?

    override fun optionsMenu(ctx: Context, menu: Menu) {}

    fun loadCover(req: RequestManager, cb: (Palette?, Palette.Swatch?) -> Unit = { a, b -> }): ReceiveChannel<RequestBuilder<Drawable>?> = run {
        Library.instance.findAlbumExtras(id.album).switchMap {
            if (it != null) {
                // This album cover is either local or cached.
                produceTask {
                    req.load(it.artworkUri)
                        .apply(Library.ARTWORK_OPTIONS)
                        .listener(loadPalette(id, cb))
                }
            } else when {
                // The song happens to know its artwork url.
                artworkUrl != null -> produceTask {
                    req.load(artworkUrl)
                        .apply(Library.ARTWORK_OPTIONS)
                        .listener(loadPalette(id, cb))
                }
                else -> produceTask { null }
            }
        }
    }


    companion object {
        val MIN_DURATION = TimeUnit.SECONDS.toMillis(5)
        val MAX_DURATION = TimeUnit.MINUTES.toMillis(60)
    }
}

@Parcelize
data class SyncSong(
    override val id: SongId
): Song, Parcelable {
    override val track get() = 1
    override val disc get() = 1
    override val duration get() = 0
    override val year: Int? get() = null
    override val artworkUrl: String? get() = null


    override suspend fun loadMedia(): Song.Media? {
        val existing = Library.instance.findSong(id).first()
//        val existing: Song? = null

        val internetStatus = App.instance.internetStatus.first()
        if (internetStatus == App.InternetStatus.OFFLINE && existing == null) {
            return null
        }

        val mode = UserPrefs.HQStreamingMode.valueOf(UserPrefs.hqStreamingMode.value)
        val hqStreaming = when (mode) {
            UserPrefs.HQStreamingMode.ONLY_UNMETERED -> internetStatus == App.InternetStatus.UNLIMITED
            UserPrefs.HQStreamingMode.ALWAYS -> true
            UserPrefs.HQStreamingMode.NEVER -> false
        }

        // This is a remote song, find the stream id
        // Well, we might have a song that's pretty much the same. Check first.
        // (Last line of defense against minor typos/discrepancies/album versions)
        return if (existing != null) {
            existing.loadMedia()
        } else {
            val res = OnlineSearchService.instance.getSongStreams(this)
            if (res.status is OnlineSearchService.StreamStatus.Available) {
                val url = if (hqStreaming) {
                    res.status.hqStream ?: res.status.stream
                } else res.status.stream
                Song.Media(url, res.start, res.end)
            } else null
        }
    }
}


data class LocalSong(
    val path: String,
    val localAlbumId: Long,
    override val id: SongId,
    override val track: Int,
    override val disc: Int,
    override val duration: Int,
    override val year: Int?,
    override val artworkUrl: String? = null
): Song {
    override suspend fun loadMedia() = Song.Media(path)
}

data class RemoteSong(
    override val id: SongId,
    val details: Details? = null,
    override val track: Int = 1,
    override val disc: Int = 1,
    override val duration: Int = 0,
    override val year: Int? = null,
    override val artworkUrl: String? = null
): Song {
    override suspend fun loadMedia(): Song.Media? {
        val existing = Library.instance.findSongFuzzy(id).first()
//        val existing: Song? = null

        val internetStatus = App.instance.internetStatus.first()
        if (internetStatus == App.InternetStatus.OFFLINE && existing == null) {
            return null
        }

        val mode = UserPrefs.HQStreamingMode.valueOf(UserPrefs.hqStreamingMode.value)
        val hqStreaming = when (mode) {
            UserPrefs.HQStreamingMode.ONLY_UNMETERED -> internetStatus == App.InternetStatus.UNLIMITED
            UserPrefs.HQStreamingMode.ALWAYS -> true
            UserPrefs.HQStreamingMode.NEVER -> false
        }

        // This is a remote song, find the stream id
        // Well, we might have a song that's pretty much the same. Check first.
        // (Last line of defense against minor typos/discrepancies/album versions)
        return if (existing != null) {
            existing.loadMedia()
        } else {
            val res = OnlineSearchService.instance.getSongStreams(this)
            if (res.status is OnlineSearchService.StreamStatus.Available) {
                val url = if (hqStreaming) {
                    res.status.hqStream ?: res.status.stream
                } else res.status.stream
                Song.Media(url, res.start, res.end)
            } else null
        }
    }

    data class Details(
        val id: String?,
        val albumId: String?,
        val artistId: String?,
        val normalStream: String? = null, // Usually 128 kb/s
        val hqStream: String? = null, // Usually 160 kb/s
        val start: Int = -1 // Position to start the stream from
    )
}