package com.loafofpiecrust.turntable.browse

import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import kotlinx.coroutines.experimental.channels.first
import me.xdrop.fuzzywuzzy.FuzzySearch


object LocalApi: SearchApi {
    private val library: Library get() = Library.instance

    override suspend fun searchArtists(query: String) = library.artists.value.filter {
        FuzzySearch.partialRatio(it.id.displayName, query) > 80
    }

    override suspend fun searchAlbums(query: String) = library.albums.value.filter {
        FuzzySearch.partialRatio(it.id.displayName, query) > 80
    }

    override suspend fun searchSongs(query: String) = library.songs.value.filter {
        FuzzySearch.partialRatio(it.id.displayName, query) > 80
    }

    override suspend fun find(album: AlbumId): Album? {
        return library.findAlbum(album).first()
    }

    override suspend fun find(artist: ArtistId): Artist? {
        return library.findArtist(artist).first()
    }

    override suspend fun find(song: SongId): Song? {
        return library.findSong(song).first()
    }

    override suspend fun fullArtwork(album: Album, search: Boolean): String? {
        return library.findAlbumExtras(album.id).first()?.artworkUri
    }

    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? {
        return null
    }

}