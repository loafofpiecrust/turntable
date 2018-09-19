package com.loafofpiecrust.turntable.browse

import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.RemoteArtist
import com.loafofpiecrust.turntable.provided
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.tryOr

interface SearchApi {
    suspend fun searchArtists(query: String): List<Artist>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchSongs(query: String): List<Song>

    suspend fun find(album: AlbumId): Album?
    suspend fun find(artist: ArtistId): Artist?
    suspend fun find(song: SongId): Song? =
        find(song.album)?.tracks?.find { it.id.fuzzyEquals(song) }
//    suspend fun find(song: Song): Song.RemoteDetails?

    suspend fun fullArtwork(album: Album, search: Boolean = false): String?
    suspend fun fullArtwork(artist: Artist, search: Boolean = false): String?

    companion object: SearchApi {

        /// All Music APIs in descending order of priority
        private val APIS = arrayOf(
            LocalApi,
            Discogs,
            MusicBrainz,
            Spotify
        )

        private suspend fun <R: Any> overApis(block: suspend SearchApi.() -> R?): R? {
            for (a in APIS) {
                val res = tryOr(null) { block(a) }
                if (res != null) {
                    return res
                }
            }
            return null
        }

        override suspend fun find(album: AlbumId): Album?
            = overApis { find(album) }

        override suspend fun find(artist: ArtistId): Artist?
            = overApis { find(artist) }

//        override suspend fun find(song: Song): Song.RemoteDetails?
//            = overApis { find(song) }

        override suspend fun searchArtists(query: String): List<Artist> =
            overApis {
                searchArtists(query).provided { it.isNotEmpty() }
            } ?: listOf()

        override suspend fun searchAlbums(query: String): List<Album> =
            overApis {
                searchAlbums(query).provided { it.isNotEmpty() }
            } ?: listOf()

        override suspend fun searchSongs(query: String): List<Song> =
            overApis {
                searchSongs(query).provided { it.isNotEmpty() }
            } ?: listOf()

        override suspend fun fullArtwork(album: Album, search: Boolean): String? = tryOr(null) {
            if (album is RemoteAlbum) {
                album.remoteId.artworkUrl ?: when (album.remoteId) {
                    is Discogs.AlbumDetails -> Discogs.fullArtwork(album, search)
                    is Spotify.AlbumDetails -> Spotify.fullArtwork(album, search)
                    is MusicBrainz.AlbumDetails -> MusicBrainz.fullArtwork(album, search)
                    else -> null
                } ?: overApis {
                    fullArtwork(album, search)
                }
            } else overApis {
                fullArtwork(album, search)
            }
        }

        override suspend fun fullArtwork(artist: Artist, search: Boolean): String? = tryOr(null) {
            if (artist is RemoteArtist) {
                when (artist.details) {
                    is Discogs.ArtistDetails -> Discogs.fullArtwork(artist, search)
                    is Spotify.ArtistDetails -> Spotify.fullArtwork(artist, search)
                    is MusicBrainz.ArtistDetails -> MusicBrainz.fullArtwork(artist, search)
                    else -> null
                } ?: overApis {
                    fullArtwork(artist, search)
                }
            } else overApis {
                fullArtwork(artist, search)
            }
        }
    }
}