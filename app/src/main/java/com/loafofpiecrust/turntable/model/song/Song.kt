package com.loafofpiecrust.turntable.model.song

import android.graphics.drawable.Drawable
import android.os.Parcelable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.Recommendable
import com.loafofpiecrust.turntable.model.album.Album
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
    suspend fun resolveTracks(): List<Song>
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
    override suspend fun resolveTracks() = listOf(this)

    /**
     * Comparable/sortable value for disc and track
     * Assumes that a disc will never have over 999 tracks.
     *
     * Example: Disc 2, Track 27 => 2027
     */
    @kotlinx.serialization.Transient
    val discTrack: Int get() = disc * 1000 + track

    fun loadCover(
        req: RequestManager
    ): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.loadAlbumCover(req, id.album)
            val initial = localArt.receive()
                ?: Repositories.find(id.album)?.let { remoteAlbum ->
                    req.load(Repositories.fullArtwork(remoteAlbum, true))
                }

            send(initial)
            sendFrom(localArt.filterNotNull())
        }

    data class Media(
        val sources: List<Source>,
        // Never expire by default
        val expiryDate: Long = Long.MAX_VALUE,
        // Play the whole source by default
        val start: Int = 0,
        val end: Int = 0
    ) {
        constructor(url: String): this(
            listOf(Source(url, Quality.UNKNOWN))
        )

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
            val quality: Quality,
            val format: String = "aac"
        )

        companion object {
            fun fromYouTube(
                lqUrl: String,
                hqUrl: String?,
                expiryDate: Long = System.currentTimeMillis() + 6.hours.toMillis().toLong()
            ): Media {
                val lq = Source(lqUrl, Quality.LOW)
                val sources = if (hqUrl != null) {
                    listOf(lq, Source(hqUrl, Quality.MEDIUM))
                } else {
                    listOf(lq)
                }
                return Media(sources, expiryDate)
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
