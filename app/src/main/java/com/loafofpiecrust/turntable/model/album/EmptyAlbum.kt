package com.loafofpiecrust.turntable.model.album

import com.loafofpiecrust.turntable.model.song.Song

class EmptyAlbum(
    override val id: AlbumId
): Album {
    override val year: Int get() = 0
    override val type: Album.Type
        get() = Album.Type.OTHER

    override suspend fun resolveTracks(): List<Song> = emptyList()
}