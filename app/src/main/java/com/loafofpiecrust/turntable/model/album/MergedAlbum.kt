package com.loafofpiecrust.turntable.model.album

import android.graphics.drawable.Drawable
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.dedupMergeSorted
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.produceSingle
import com.loafofpiecrust.turntable.util.switchMap
import kotlinx.coroutines.channels.ReceiveChannel

class MergedAlbum(
    private val a: Album,
    private val b: Album
): Album {
    override val id get() = a.id
    /**
     * The "largest" type associated with their album being merged.
     * TODO: Re-calculate the type based on whether these are local or remote.
     */
    override val type: Album.Type get() = minOf(a.type, b.type)
    override val year: Int
        get() {
        val yearA = a.year
        val yearB = b.year
        return if (yearA > 0 && yearB > 0) {
            minOf(yearA, yearB)
        } else yearA.takeIf { it > 0 } ?: yearB
    }

    private var tracks: List<Song>? = null
    override suspend fun resolveTracks(): List<Song> {
        tracks = tracks ?: (a.resolveTracks() + b.resolveTracks())
            .sortedBy { it.discTrack }
            .dedupMergeSorted(
                { a, b -> a.disc == b.disc && a.id == b.id },
                // TODO: Use MergedSong here?
                { a, b -> a }
            )

        return tracks ?: listOf()
    }

    override fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> {
        return a.loadThumbnail(req).switchMap {
            if (it != null) {
                produceSingle(it)
            } else b.loadThumbnail(req)
        }
    }
}

//data class MergedAlbum(
//    override val uuid: AlbumId,
//    override val tracks: List<Song>,
//    override val type: Album.Type,
//    override val year: Int?
//): RemoteAlbum(uuid, remoteId, type, year) {
//    override fun mergeWith(other: Album): Album {
//        return MergedAlbum(
//            other.uuid.copy(name = other.uuid.name.commonPrefixWith(uuid.name, true)),
//            remoteId,
//            tracks = (tracks + other.tracks)
//                .sortedBy { it.disc * 1000 + it.track }
//                .dedupMergeSorted(
//                    { a, b -> a.disc == b.disc && a.uuid.displayName.equals(b.uuid.displayName, true) },
//                    { a, b -> if (a.local != null) a else b }
//                ),
//            type = when {
//                other.uuid.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
//                other.tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
//                other.tracks.size <= 7 -> Album.Type.EP
//                other.uuid.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
//                else -> Album.Type.LP
//            },
//            year = year ?: other.year
//        )
//    }
//
//}

