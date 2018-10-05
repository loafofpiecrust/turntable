package com.loafofpiecrust.turntable.browse

import android.support.annotation.StringRes
import com.loafofpiecrust.turntable.R
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
import kotlinx.coroutines.experimental.isActive
import kotlin.coroutines.experimental.coroutineContext

interface SearchApi {
    @get:StringRes
    val displayName: Int

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
        override val displayName: Int
            get() = R.string.search

        /// All Music APIs in descending order of priority
        val DEFAULT_APIS = arrayOf<SearchApi>(
            SearchCache,
            Discogs,
            MusicBrainz,
            Spotify
        )

        private suspend fun <R: Any> overApis(block: suspend SearchApi.() -> R?): R? {
            for (a in DEFAULT_APIS) {
                if (!coroutineContext.isActive) return null

                val res = tryOr(null) { block(a) }
                if (res != null) {
                    return res
                }
            }
            return null
        }

        override suspend fun find(album: AlbumId): Album?
            = LocalApi.find(album) ?: findOnline(album)

        suspend fun findOnline(album: AlbumId): Album?
            = overApis { find(album) }?.also { SearchCache.cache(it) }


        override suspend fun find(artist: ArtistId): Artist?
            = LocalApi.find(artist) ?: findOnline(artist)

        suspend fun findOnline(artist: ArtistId): Artist?
            = overApis { find(artist) }?.also { SearchCache.cache(it) }

//        override suspend fun find(song: Song): Song.RemoteDetails?
//            = overApis { find(song) }

        override suspend fun searchArtists(query: String): List<Artist> =
            overApis {
                searchArtists(query).takeIf { it.isNotEmpty() }
            } ?: listOf()

        override suspend fun searchAlbums(query: String): List<Album> =
            overApis {
                searchAlbums(query).takeIf { it.isNotEmpty() }
            } ?: listOf()

        override suspend fun searchSongs(query: String): List<Song> =
            overApis {
                searchSongs(query).takeIf { it.isNotEmpty() }
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