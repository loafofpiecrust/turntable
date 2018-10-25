package com.loafofpiecrust.turntable.repository.local

import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.service.Library
import kotlinx.coroutines.channels.first
import me.xdrop.fuzzywuzzy.FuzzySearch


object LocalApi: Repository {
    override val displayName: Int
        get() = R.string.search_local

    private val library: Library get() = Library.instance

    override suspend fun searchArtists(query: String) = library.artistsMap.value.values.filter {
        FuzzySearch.partialRatio(it.id.displayName, query) > 80
    }

    override suspend fun searchAlbums(query: String) = library.albumsMap.value.values.filter {
        FuzzySearch.partialRatio(it.id.displayName, query) > 80
    }

    override suspend fun searchSongs(query: String) = library.songsMap.value.values.filter {
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