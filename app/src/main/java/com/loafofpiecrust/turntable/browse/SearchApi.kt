package com.loafofpiecrust.turntable.browse

import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.provided
import com.loafofpiecrust.turntable.song.Song

interface SearchApi {
    suspend fun searchArtists(query: String): List<Artist>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchSongs(query: String): List<Song>

    suspend fun find(album: Album): Album.RemoteDetails?
    suspend fun find(artist: Artist): Artist.RemoteDetails?
//    suspend fun find(song: Song): Song.RemoteDetails?

    suspend fun fullArtwork(album: Album, search: Boolean = false): String?

    companion object: SearchApi {

        /// All Music APIs in descending order of priority
        private val APIS = listOf(
            Discogs,
            MusicBrainz,
            Spotify
        )

        private suspend fun <R: Any> overApis(block: suspend SearchApi.() -> R?): R? {
            for (a in APIS) {
                val res = block(a)
                if (res != null) {
                    return res
                }
            }
            return null
        }

        override suspend fun find(album: Album): Album.RemoteDetails?
            = overApis { find(album) }

        override suspend fun find(artist: Artist): Artist.RemoteDetails?
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

        override suspend fun fullArtwork(album: Album, search: Boolean): String? {
            return if (album.remote != null) {
                return album.remote.artworkUrl ?: when (album.remote) {
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
    }
}