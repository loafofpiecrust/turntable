package com.loafofpiecrust.turntable.browse

import com.google.gson.internal.LinkedTreeMap
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.song.Song

object SearchCache: SearchApi {
    override val displayName: Int
        get() = 0

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

    private val music = LinkedTreeMap<MusicId, Music>()
    fun cache(item: Album) = music.cache(item.id, item)
    fun cache(item: Artist) = music.cache(item.id, item)

    override suspend fun find(album: AlbumId): Album? {
        return music[album] as? Album
    }

    override suspend fun find(artist: ArtistId): Artist? {
        return music[artist] as? Artist
    }

    override suspend fun fullArtwork(album: Album, search: Boolean): String? = null
    override suspend fun fullArtwork(artist: Artist, search: Boolean): String? = null
}