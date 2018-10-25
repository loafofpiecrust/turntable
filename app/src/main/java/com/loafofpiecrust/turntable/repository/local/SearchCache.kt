package com.loafofpiecrust.turntable.repository.local

import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.repository.Repository

object SearchCache: Repository {
    private const val CACHE_COUNT = 25

    override suspend fun searchArtists(query: String) = emptyList<Artist>()

    override suspend fun searchAlbums(query: String) = emptyList<Album>()

    override suspend fun searchSongs(query: String) = emptyList<Song>()


    private fun <K, T> MutableMap<K, T>.cache(key: K, value: T) {
        if (size >= CACHE_COUNT) {
            entries.remove(entries.first())
        }
        put(key, value)
    }

    private val music = LinkedHashMap<MusicId, Music>()
    fun cache(item: Album) = music.cache(item.id, item)
    fun cache(item: Artist) = music.cache(item.id, item)

    override suspend fun find(album: AlbumId): Album? {
        return music[album] as? Album
    }

    override suspend fun find(artist: ArtistId): Artist? {
        return music[artist] as? Artist
    }


    private val artwork = LinkedHashMap<MusicId, String>()
    fun cacheArtwork(item: Album, url: String) = artwork.cache(item.id, url)
    fun cacheArtwork(item: Artist, url: String) = artwork.cache(item.id, url)
    override suspend fun fullArtwork(album: Album, search: Boolean): String? = artwork[album.id]
    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? = artwork[artist.id]
}