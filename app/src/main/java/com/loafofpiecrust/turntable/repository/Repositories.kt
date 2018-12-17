package com.loafofpiecrust.turntable.repository

import com.github.ajalt.timberkt.Timber
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.RemoteArtist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.repository.local.SearchCache
import com.loafofpiecrust.turntable.repository.remote.Discogs
import com.loafofpiecrust.turntable.repository.remote.MusicBrainz
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.tryOr
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Search using all the default sources.
 * Each method returns a result from the first [Repository] that finds search results.
 */
object Repositories: Repository {
    override val displayName: Int
        get() = R.string.search_all

    /// All Music APIs in descending order of priority
    val ALL: Array<Repository> = arrayOf(
        SearchCache,
        Discogs,
        MusicBrainz,
        Spotify
    )

    private suspend fun <R: Any> overSources(block: suspend Repository.() -> R?): R? {
        for (a in ALL) {
            if (!coroutineContext.isActive) return null

            val res = tryOr(null) { block(a) }
            if (res != null) {
                Timber.d { "Search succeeded on ${a.javaClass.simpleName}" }
                return res
            }
        }
        return null
    }

    override suspend fun find(album: AlbumId): Album? =
        LocalApi.find(album)
            ?: SearchCache.find(album.artist)?.albums?.find { it.id == album }
            ?: findOnline(album)

    suspend fun findOnline(album: AlbumId): Album? = overSources {
        find(album)
    }?.also { SearchCache.cache(it) }


    override suspend fun find(artist: ArtistId): Artist? =
        LocalApi.find(artist) ?: findOnline(artist)

    suspend fun findOnline(artist: ArtistId): Artist?
        = overSources { find(artist) }?.also { SearchCache.cache(it) }

    override suspend fun searchArtists(query: String): List<Artist> =
        overSources {
            searchArtists(query).takeIf { it.isNotEmpty() }
        } ?: emptyList()

    override suspend fun searchAlbums(query: String): List<Album> =
        overSources {
            searchAlbums(query).takeIf { it.isNotEmpty() }
        } ?: emptyList()

    override suspend fun searchSongs(query: String): List<Song> =
        overSources {
            searchSongs(query).takeIf { it.isNotEmpty() }
        } ?: emptyList()

    suspend fun searchAll(query: String): List<Music> = coroutineScope {
        val songs = async { searchSongs(query) }
        val albums = async { searchAlbums(query) }
        val artists = async { searchArtists(query) }
        artists.await() + albums.await() + songs.await()
    }

    override suspend fun fullArtwork(album: Album, search: Boolean): String? =
        SearchCache.fullArtwork(album) ?: tryOr(null) {
            if (album is RemoteAlbum) {
                album.remoteId.artworkUrl ?: when (album.remoteId) {
                    is Discogs.AlbumDetails -> Discogs.fullArtwork(album, search)
                    is Spotify.AlbumDetails -> Spotify.fullArtwork(album, search)
                    is MusicBrainz.AlbumDetails -> MusicBrainz.fullArtwork(album, search)
                    else -> null
                } ?: overSources {
                    fullArtwork(album, search)
                }
            } else overSources {
                fullArtwork(album, search)
            }
        }?.also { SearchCache.cacheArtwork(album, it) }

    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? =
        SearchCache.fullArtwork(artist) ?: tryOr(null) {
            if (artist is RemoteArtist) {
                when (artist.details) {
                    is Discogs.ArtistDetails -> Discogs.fullArtwork(artist, search)
                    is Spotify.ArtistDetails -> Spotify.fullArtwork(artist, search)
                    is MusicBrainz.ArtistDetails -> MusicBrainz.fullArtwork(artist, search)
                    else -> null
                } ?: overSources {
                    fullArtwork(artist, search)
                }
            } else overSources {
                fullArtwork(artist, search)
            }
        }?.also { SearchCache.cacheArtwork(artist, it) }
}