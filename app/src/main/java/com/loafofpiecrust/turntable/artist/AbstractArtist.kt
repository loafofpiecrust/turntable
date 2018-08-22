
import android.os.Parcelable
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.dedupMerge


interface Artist {
    val id: ArtistId
    val albums: List<Album>
    val startYear: Int?
    val endYear: Int?
   
    
    data class Member(
        val name: String,
        val id: String,
        val active: Boolean
    )
}

interface AlbumArtist {
    val albums: List<Album>
    val members: List<Artist.Member>? get() = null
}

// class RemoteAlbumArtist: AlbumArtist {
// }


class LocalArtist(
    override val id: ArtistId,
    override val albums: List<Album>
): Artist {
    override val startYear get() = albums.minBy { it.year ?: Int.MAX_VALUE }?.year
    override val endYear get() = albums.maxBy { it.year ?: 0 }?.year
}

class RemoteArtist(
    override val id: ArtistId,
    val details: RemoteDetails,
    override val startYear: Int? = null,
    override val endYear: Int? = null
): Artist {
    // Each API implements whether they have any of this info already 
    // or if it's all lazy or exists at all or what
    interface RemoteDetails: Parcelable {
        val albums: List<Album>
        val biography: String
        val members: List<Artist.Member>
    }

    override val albums: List<Album> by lazy {
        details.albums
    }
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
