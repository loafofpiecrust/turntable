package com.loafofpiecrust.turntable.artist

import android.os.Parcelable
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.dedupMerge
import com.loafofpiecrust.turntable.given
import kotlinx.coroutines.experimental.runBlocking


interface AlbumArtist {
    val albums: List<Album>
    val members: List<Artist.Member>? get() = null
}

// class RemoteAlbumArtist: AlbumArtist {
// }

// loadArtwork(req):
//if (artworkUrl != null) {
//    produce(BG_POOL) { send(req.load(artworkUrl).apply(Library.ARTWORK_OPTIONS)) }
//} else {
//    Library.instance.loadArtistImage(req, id)
//}

class LocalArtist(
    override val id: ArtistId,
    override val albums: List<Album>
): Artist {
    override val startYear get() = albums.minBy { it.year ?: Int.MAX_VALUE }?.year
    override val endYear get() = albums.maxBy { it.year ?: 0 }?.year

    private val remote get() = runBlocking { SearchApi.find(id) as RemoteArtist? }
    override val biography: String? get() = remote?.biography
}

class RemoteArtist(
    override val id: ArtistId,
    val details: Artist.RemoteDetails,
    override val startYear: Int? = null,
    override val endYear: Int? = null
): Artist {
    override val albums get() = details.albums
    override val biography get() = details.biography

    // Properties only obtained with details:
    // - albums
    // - members
    // - biography
}

class MergedArtist(val a: Artist, val b: Artist): Artist {
    override val id get() = a.id
    override val startYear get() = when {
        a.startYear != null && b.startYear != null -> minOf(a.startYear!!, b.startYear!!)
        else -> a.startYear ?: b.startYear
    }
    override val endYear get() = given(a.endYear) { a ->
        given (b.endYear) { b -> maxOf(a, b) } ?: a
    } ?: b.endYear
    override val albums: List<Album> by lazy {
        (a.albums + b.albums).dedupMerge(
            { a, b -> a.id == b.id && a.type == b.type },
            { a, b -> a.mergeWith(b) }
        )
    }
    override val biography get() = a.biography ?: b.biography
}

/*
Merged Usage:
   val local = Library.findArtist(id)
We have the local albums for this artist,
Now we want the remote albums too, for changing details display mode
   val remote = SearchApi.find(local.id)
   val merged = MergedArtist(local, remote)

TODO: SearchApi.find(...) should cache the results, so that it has RemoteArtist objects with potentially resolved children.
 */
