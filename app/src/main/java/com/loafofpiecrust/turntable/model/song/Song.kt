package com.loafofpiecrust.turntable.model.song

import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.v7.graphics.Palette
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.repository.StreamProviders
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.channels.produce


fun CharSequence.withoutArticle(): CharSequence = when {
    startsWith("the ", true) -> subSequenceView(4)
    startsWith("a ", true) -> subSequenceView(2)
    else -> this
}


data class HistoryEntry(
    val song: Song,
    val timestamp: Long = System.currentTimeMillis()
)

interface HasTracks: Music {
    val tracks: List<Song>
}


// All possible music status':
// - Local: has an id, can be played. May be partial album (if album)
// - Not local, not downloading yet (queued in sync / DL'd from search)
// - Not local, downloading from torrent (queued in sync / DL'd from search)
// - Not local, downloading from youtube (queued in sync / DL'd from search)
// - Not local, downloading from p2p/udp (queued in sync)
// - Not local, downloading from larger collection torrent (song from album, song/album from artist)

// All possible statuses of a song:
// Remote:
// - Unknown: no remoteInfo confirmed
// - Partial: metadata confirmed, no stream urls
// - Resolved: confirmed metadata and stream urls
@Parcelize
data class Song(
    override val id: SongId,
    /// This song's track number in the [Album] it comes from.
    val track: Int,
    /// The disc number of this song on the [Album] it comes from.
    val disc: Int,
    /// Air-time in milliseconds
    val duration: Int,
    /// Publish year of this song, generally the same as the album it comes from.
    val year: Int,
    /// Platform-specific identifier
    @Transient
    val platformId: PlatformId? = null
): Music, Parcelable, HasTracks, Recommendable {
    override val tracks: List<Song> get() = listOf(this)

    /**
     * Comparable/sortable value for disc and track
     * Assumes that a disc will never have over 999 tracks.
     *
     * Example: Disc 2, Track 27 => 2027
     */
    val discTrack: Int get() = disc * 1000 + track


    suspend fun loadMedia(): Media? {
        val existing = Library.instance.sourceForSong(this.id)
        if (existing != null) {
            return Media(existing)
        }

        val internetStatus = App.instance.internetStatus.first()
        if (internetStatus == App.InternetStatus.OFFLINE) {
            return null
        }

        val mode = UserPrefs.HQStreamingMode.valueOf(UserPrefs.hqStreamingMode.value)
        val hqStreaming = when (mode) {
            UserPrefs.HQStreamingMode.ONLY_UNMETERED -> internetStatus == App.InternetStatus.UNLIMITED
            UserPrefs.HQStreamingMode.ALWAYS -> true
            UserPrefs.HQStreamingMode.NEVER -> false
        }

        // This is a remote song, find the stream uuid
        // Well, we might have a song that's pretty much the same. Check first.
        // (Last line of defense against minor typos/discrepancies/album versions)
//        val res = OnlineSearchService.instance.getSongStreams(this)
//        return if (res.status is OnlineSearchService.StreamStatus.Available) {
//            val url = if (hqStreaming) {
//                res.status.hqStream ?: res.status.stream
//            } else res.status.stream
//            Media(url, res.start, res.end)
//        } else null
        return StreamProviders.sourceForSong(this)
    }

    fun loadCover(req: RequestManager, cb: (Palette?, Palette.Swatch?) -> Unit = { a, b -> }): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.instance.loadAlbumCover(req, id.album)
            val first = localArt.receive()
            send(if (first != null) {
                first
            } else {
                val remoteAlbum = Repositories.find(id.album)
                if (remoteAlbum != null) {
                    req.load(Repositories.fullArtwork(remoteAlbum, true))
                } else {
                    null
                }
            })

            sendFrom(localArt.filterNotNull())
        }

    data class Media(val url: String, val start: Int = 0, val end: Int = 0)

    /**
     * Source-specific ID providing info to locate related content from the same source.
     * For example, a [PlatformId] implementation may provide Android Media Query IDs.
     */
    interface PlatformId: Parcelable

    companion object {
        val MIN_DURATION = 5.seconds
        val MAX_DURATION = 1.hours
    }
}