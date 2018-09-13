package com.loafofpiecrust.turntable.browse

import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.song.Song
import kotlinx.coroutines.experimental.channels.first


object LocalApi: SearchApi {
    override suspend fun searchArtists(query: String) = emptyList<Artist>()

    override suspend fun searchAlbums(query: String) = emptyList<Album>()

    override suspend fun searchSongs(query: String) = emptyList<Song>()

    override suspend fun find(album: AlbumId): Album? {
        return Library.instance.findAlbum(album).first()
    }

    override suspend fun find(artist: ArtistId): Artist? {
        return Library.instance.findArtist(artist).first()
    }

    override suspend fun fullArtwork(album: Album, search: Boolean): String? {
        return Library.instance.findAlbumExtras(album.id).first()?.artworkUri
    }

    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? {
        return null
    }

}