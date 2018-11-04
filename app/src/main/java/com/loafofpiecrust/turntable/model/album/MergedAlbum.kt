package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.dedupMergeSorted
import com.loafofpiecrust.turntable.model.song.Song


class MergedAlbum(
    private val a: Album,
    private val b: Album
): Album {
    init {
//        assert(a.uuid == b.uuid) { "Can only merge similarly named albums" }
    }

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
    override val tracks: List<Song> by lazy {
        (a.tracks + b.tracks)
            .sortedBy { it.discTrack }
            .dedupMergeSorted(
                { a, b -> a.disc == b.disc && a.id == b.id },
                // TODO: Use MergedSong here?
                { a, b -> a }
            )
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

