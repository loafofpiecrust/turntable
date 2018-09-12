package com.loafofpiecrust.turntable.album

import android.content.Context
import android.view.Menu
import com.loafofpiecrust.turntable.dedupMergeSorted
import com.loafofpiecrust.turntable.song.LocalSong
import com.loafofpiecrust.turntable.song.Song


class MergedAlbum(
    val a: Album,
    val b: Album
): Album {
    init {
//        assert(a.id == b.id) { "Can only merge similarly named albums" }
    }

    override val id get() = a.id
    override val type: Album.Type get() = minOf(a.type, b.type)
    override val year: Int? get() {
        val yearA = a.year
        val yearB = b.year
        return if (yearA != null && yearA > 0 && yearB != null && yearB > 0) {
            minOf(yearA, yearB)
        } else yearA ?: yearB
    }
    override val tracks: List<Song> by lazy {
        (a.tracks + b.tracks)
            .sortedBy { it.discTrack }
            .dedupMergeSorted(
                { a, b -> a.disc == b.disc && a.id == b.id },
                // TODO: Use MergedSong here!
                { a, b -> a }
            )
    }

    // TODO: Generalize to include both! Maybe abstract over popupMenu items
    override fun optionsMenu(ctx: Context, menu: Menu) = a.optionsMenu(ctx, menu)
}

//data class MergedAlbum(
//    override val id: AlbumId,
//    override val tracks: List<Song>,
//    override val type: Album.Type,
//    override val year: Int?
//): RemoteAlbum(id, remoteId, type, year) {
//    override fun mergeWith(other: Album): Album {
//        return MergedAlbum(
//            other.id.copy(name = other.id.name.commonPrefixWith(id.name, true)),
//            remoteId,
//            tracks = (tracks + other.tracks)
//                .sortedBy { it.disc * 1000 + it.track }
//                .dedupMergeSorted(
//                    { a, b -> a.disc == b.disc && a.id.displayName.equals(b.id.displayName, true) },
//                    { a, b -> if (a.local != null) a else b }
//                ),
//            type = when {
//                other.id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
//                other.tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
//                other.tracks.size <= 7 -> Album.Type.EP
//                other.id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
//                else -> Album.Type.LP
//            },
//            year = year ?: other.year
//        )
//    }
//
//}

