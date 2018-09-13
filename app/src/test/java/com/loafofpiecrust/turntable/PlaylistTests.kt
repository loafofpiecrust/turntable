package com.loafofpiecrust.turntable

import android.graphics.Color
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.MixTape
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.util.deserialize
import com.loafofpiecrust.turntable.util.serialize
import kotlinx.coroutines.experimental.channels.firstOrNull
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
        assert(sides).contains.inOrder.only.entries(
            { contains("A") },
            { contains("B") }
        )
        assert(mixtape.type.totalLength).toBe(60)

        val side1 = runBlocking { mixtape.tracksOnSide(0).firstOrNull() }
        assert(side1!!).toBe(listOf(songs[0]))
    }
}