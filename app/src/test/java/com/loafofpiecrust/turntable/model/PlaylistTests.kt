package com.loafofpiecrust.turntable.model

import android.graphics.Color
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.util.deserialize
import com.loafofpiecrust.turntable.util.serialize
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.firstOrNull
import kotlinx.coroutines.experimental.delay
import kotlinx.coroutines.experimental.runBlocking
import java.util.*
import kotlin.test.Test

class PlaylistTests {
    val user = SyncService.User(
        "name@website.com",
        "<deviceId>",
        "Lemony Snicket"
    )
    val songs = listOf(
        Song(
            SongId(
                "The Clover Saloon",
                AlbumId(
                    "How Sad, How Lovely",
                    ArtistId("Connie Converse")
                )
            ),
            track = 5,
            disc = 1,
            duration = 65000,
            year = 2009
        ),
        Song(
            SongId(
                "Me, On The Beach",
                AlbumId(
                    "Dream Sounds",
                    ArtistId("Nagisa Ni Te")
                )
            ),
            track = 3, disc = 1,
            duration = 76500,
            year = 2005
        )
    )

    @Test fun `serialization`() {
        val mixtape = MixTape(user, MixTape.Type.C60, "For My Love", Color.BLUE, UUID.randomUUID())
        val serd = runBlocking { serialize(mixtape) }
        val deserd = runBlocking { deserialize<MixTape>(serd) }
        assert(deserd).toBe(mixtape)
    }

    @Test fun `basic mixtape`() {
        val mixtape = MixTape(user, MixTape.Type.C60, "For My Love", Color.BLUE, UUID.randomUUID())
        mixtape.addAll(0, listOf(songs[0]))

        // TODO: Make this international? Google what Japanse tape sides were called.
        val sides = mixtape.type.sideNames
        expect(sides).contains.inOrder.only.entries(
            { contains("A") },
            { contains("B") }
        )
        expect(mixtape.type.totalLength).toBe(60)

        val side1 = runBlocking { mixtape.tracksOnSide(0).firstOrNull() }
        expect(side1!!).toBe(listOf(songs[0]))
    }

    @Test fun operations() = runBlocking {
        val playlist = CollaborativePlaylist(user, "All Good Things...", Color.GREEN, UUID.randomUUID())
        expect(playlist.add(songs[1])).toBe(true)
        expect(playlist.add(songs[0])).toBe(true)
        delay(10)
        expect(playlist.tracks.first()).toBe(listOf(songs[1], songs[0]))
        playlist.move(1, 0)
        playlist.remove(0)
        expect(playlist.tracks.first()).toBe(listOf(songs[1]))
        playlist.remove(0)
        expect(playlist.tracks.first()).isEmpty()
    }
}