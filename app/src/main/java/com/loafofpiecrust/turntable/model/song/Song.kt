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
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.produce

interface HasTracks : Music {
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
//@Serializable
data class Song(
    override val id: SongId,
    /** This song's track number in the [Album] it comes from. */
    val track: Int,
    /** The disc number of this song on the [Album] it comes from. */
    val disc: Int,
    /** Air-time in milliseconds */
    val duration: Int,
    /** Publish year of this song, generally the same as the album it comes from. */
    val year: Int,
    /** Platform-specific identifier */
    @Transient
    val platformId: PlatformId? = null
): Music, Parcelable, HasTracks, Recommendable {
    @kotlinx.serialization.Transient
    override val tracks: List<Song> get() = listOf(this)

    /**
     * Comparable/sortable value for disc and track
     * Assumes that a disc will never have over 999 tracks.
     *
     * Example: Disc 2, Track 27 => 2027
     */
    @kotlinx.serialization.Transient
    val discTrack: Int get() = disc * 1000 + track

    fun loadCover(
        req: RequestManager,
        cb: (Palette?, Palette.Swatch?) -> Unit = { a, b -> }
    ): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.loadAlbumCover(req, id.album)
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

    data class Media(
        val sources: List<Source>,
        val start: Int = 0,
        val end: Int = 0
    ) {
        constructor(url: String): this(listOf(Source(url)))

        fun bestSource() = sources.maxBy { it.quality }
        fun mediocreSource() =
            sources.lazy.filter { it.quality < Quality.MEDIUM }.maxBy { it.quality }
                ?: sources.minBy { it.quality }

        fun bestSourceFor(
            internetStatus: App.InternetStatus,
            streamingMode: UserPrefs.HQStreamingMode
        ): Source? {
            return if (sources.size == 1) {
                sources[0]
            } else when (streamingMode) {
                UserPrefs.HQStreamingMode.ALWAYS -> bestSource()
                UserPrefs.HQStreamingMode.NEVER -> mediocreSource()
                UserPrefs.HQStreamingMode.ONLY_UNMETERED -> {
                    if (internetStatus == App.InternetStatus.UNLIMITED) {
                        bestSource()
                    } else {
                        mediocreSource()
                    }
                }
            }
        }

        enum class Quality(val bitrate: Int = -1) {
            AWFUL(128),  // ~128kbps
            UNKNOWN, // Unlisted quality is probably comparable to 192kbps
            LOW(192),    // ~192kpbs
            MEDIUM(256), // ~256kbps
            LOSSLESS, // Prioritize 320kbps mp3 over flac for download times & practicality
            HIGH(320);   // ~320kbps
        }

        data class Source(
            val url: String,
            val quality: Quality = Quality.UNKNOWN
        )

        companion object {
            fun fromYouTube(lqUrl: String, hqUrl: String?): Media {
                val sources = mutableListOf(
                    Source(lqUrl, Quality.LOW)
                )
                if (hqUrl != null) {
                    sources.add(Source(hqUrl, Quality.MEDIUM))
                }
                return Media(sources)
            }
        }
    }

    /**
     * Source-specific ID providing info to locate related content from the same source.
     * For example, a [PlatformId] implementation may provide Android Media Query IDs.
     */
    interface PlatformId : Parcelable

    companion object {
        val MIN_DURATION: Duration = 5.seconds
        val MAX_DURATION: Duration = 1.hours
        val DEFAULT_DURATION: Duration = 4.minutes
    }
}

fun CharSequence.withoutArticle(): CharSequence = when {
    startsWith("the ", true) -> subSequenceView(4)
    startsWith("a ", true) -> subSequenceView(2)
    else -> this
}

data class HistoryEntry(
    val song: Song,
    val timestamp: Long = System.currentTimeMillis()
)
