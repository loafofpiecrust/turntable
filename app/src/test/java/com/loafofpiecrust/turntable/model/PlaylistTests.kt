package com.loafofpiecrust.turntable.model

import android.graphics.Color
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.sync.User
import com.loafofpiecrust.turntable.util.deserialize
import com.loafofpiecrust.turntable.util.serialize
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.util.*
import kotlin.test.Test

class PlaylistTests {
    val user = User(
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
        ),
        Song(
            SongId("Red House", AlbumId("Greatest Hits", ArtistId("Jimi Hendrix"))),
            track = 5, disc = 1,
            duration = 55860,
            year = 1973
        )
    )

    @Test fun `serialization`() {
        val mixtape = MixTape(
            PlaylistId("For My Love"),
            user,
            MixTape.Type.C60,
            Color.BLUE
        )
        val serd = runBlocking { serialize(mixtape) }
        val deserd = runBlocking { deserialize(serd) }
        assert(deserd).toBe(mixtape)
    }

    @Test fun `basic mixtape`() {
        val mixtape = MutableMixtape(
            PlaylistId("For My Love"),
            user,
            MixTape.Type.C60,
            Color.BLUE
        )
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

    @Test fun operations() = runBlocking<Unit> {
        val playlist = CollaborativePlaylist(PlaylistId("All Good Things..."), user, Color.GREEN)
        expect(playlist.add(songs[1])).toBe(true)
        expect(playlist.add(songs[0])).toBe(true)
        delay(10)
        expect(playlist.tracks).toBe(listOf(songs[1], songs[0]))
        playlist.move(1, 0)
        playlist.remove(0)
        expect(playlist.tracks).toBe(listOf(songs[1]))
        playlist.remove(0)
        expect(playlist.tracks).isEmpty()
    }

    @Test fun general() = runBlocking<Unit> {
        // Original user creates playlist.
        val id = PlaylistId("for you!")
        val original = GeneralPlaylist(id)
        original.add(songs[0])
        // upload it to the database
        original.lastSyncTime = System.currentTimeMillis()
        expect(original.tracks).toBe(listOf(songs[0]))

        // Another user starts editing the playlist.
        val remote = App.kryo.copy(original)
        remote.add(songs[1])
        remote.remove(songs[0].id)
        // they sync with the database
        remote.lastSyncTime = System.currentTimeMillis()
        expect(remote.tracks).toBe(listOf(songs[1]))

        original.add(songs[2])
        expect(original.tracks).toBe(listOf(songs[0], songs[2]))

        // Original user merges with remote version
        original.mergeWith(remote)
        print(original.tracks) // 0, 2, 1
        expect(original.tracks).toBe(listOf(songs[1], songs[2]))
    }

    @Test fun `general with moves`() = runBlocking<Unit> {
        val id = PlaylistId("for you!")
        val original = GeneralPlaylist(id)

        original.add(songs[0])
        original.add(songs[1])
        // sync
        original.lastSyncTime = System.currentTimeMillis()
        expect(original.tracks).toBe(listOf(songs[0], songs[1]))

        delay(5)

        // branch
        val branch = App.kryo.copy(original)
        branch.move(songs[1].id, songs[0].id)
        branch.lastSyncTime = System.currentTimeMillis()
        expect(branch.tracks).toBe(listOf(songs[1], songs[0]))

        delay(5)

        original.add(songs[2])
        original.lastSyncTime = System.currentTimeMillis()
        expect(original.tracks).toBe(listOf(songs[0], songs[1], songs[2]))

        delay(5)

        branch.mergeWith(original)
        expect(branch.tracks).contains.inAnyOrder.only.values(songs[1], songs[0], songs[2])
    }
}