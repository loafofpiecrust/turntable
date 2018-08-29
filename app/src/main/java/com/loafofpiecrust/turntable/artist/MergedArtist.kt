package com.loafofpiecrust.turntable.artist

import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.MergedAlbum
import com.loafofpiecrust.turntable.dedupMerge
import com.loafofpiecrust.turntable.mergeNullables
import kotlin.math.max
import kotlin.math.min


class MergedArtist(val a: Artist, val b: Artist): Artist {
    override val id get() = a.id
    override val startYear: Int?
        get() = mergeNullables(a.startYear, b.startYear, ::min)

    override val endYear: Int?
        get() = mergeNullables(a.endYear, b.endYear, ::max)

    override val albums: List<Album> by lazy {
        (a.albums + b.albums).dedupMerge(
            { a, b -> a.id == b.id && a.type == b.type },
            { a, b -> MergedAlbum(a, b) }
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