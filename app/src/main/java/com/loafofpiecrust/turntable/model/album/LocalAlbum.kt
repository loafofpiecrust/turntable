package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.model.song.Song


data class LocalAlbum(
    override val id: AlbumId,
    override val tracks: List<Song>
): Album {
    private constructor(): this(AlbumId(), emptyList())

    override val year: Int
        get() = tracks.first { it.year > 0 }.year

    override val type by lazy {
        when {
            id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
            tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
            tracks.size <= 7 -> Album.Type.EP
            id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
            else -> Album.Type.LP
        }
    }
}