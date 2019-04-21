package com.loafofpiecrust.turntable.serialize

import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import com.github.salomonbrys.kotson.fromJson
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.isA
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.RemoteAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.playlist.SongPlaylist
import com.loafofpiecrust.turntable.repository.remote.Spotify
import java.io.File
import kotlin.test.Test

class SerializationCompatTest {
    val folder = File("src/test/resources/data")

    @Test fun `load saved album`() {
        val albumFile = folder.resolve("album.json")
        val album = RemoteAlbum(
            AlbumId("The Cry of Love", ArtistId("Jimi Hendrix")),
            Spotify.AlbumDetails("7ykAHaoptbCYaO0HAjpgcL"),
            year = 1971
        )

        val original = App.gson.fromJson<Album>(albumFile.reader())
        expect(album).toBe(original)
    }

    @Test fun `load saved playlist`() {
        val file = folder.resolve("song-playlist.json")
        val playlist = App.gson.fromJson<Playlist>(file.reader())
        expect(playlist).isA<SongPlaylist>()
    }
}