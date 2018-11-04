package com.loafofpiecrust.turntable.model.artist

import com.loafofpiecrust.turntable.dedupMerge
import com.loafofpiecrust.turntable.mergeNullables
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.MergedAlbum
import kotlin.math.max
import kotlin.math.min


/*
Merged Usage:
   val local = Library.findArtist(id)
We have the local albums for this artist,
Now we want the remote albums too, for changing remoteInfo display mode
   val remote = Repositories.find(local.id)
   val merged = MergedArtist(local, remote)
 */
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
