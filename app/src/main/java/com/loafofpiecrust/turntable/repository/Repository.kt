package com.loafofpiecrust.turntable.repository

import android.support.annotation.StringRes
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import org.jetbrains.anko.AnkoLogger

/**
 * Repository of music metadata that can be searched for an:
 * [Artist], [Album], [Song], artwork pertaining to one of these, etc.
 * Does *NOT* provide any streaming/downloading sources.
 */
interface Repository: AnkoLogger {
    @get:StringRes
    val displayName: Int
        get() = -1

    suspend fun searchArtists(query: String): List<Artist>
    suspend fun searchAlbums(query: String): List<Album>
    suspend fun searchSongs(query: String): List<Song>

    suspend fun find(album: AlbumId): Album?
    suspend fun find(artist: ArtistId): Artist?
    suspend fun find(song: SongId): Song? =
        find(song.album)?.tracks?.find { it.id.fuzzyEquals(song) }

    suspend fun fullArtwork(album: Album, search: Boolean = false): String?
    suspend fun fullArtwork(artist: Artist, search: Boolean = false): String?
}