package com.loafofpiecrust.turntable.ui

import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.test
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith


class MusicPlayerTest {
    val songs = listOf(
        Song(
            // song that should never exist
            SongId(
                "GibberishToddle",
                AlbumId(
                    "!*^&$(#)@",
                    ArtistId("-")
                )
            ),
            track = 999,
            disc = 22,
            duration = 999999,
            year = 5
        ),
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

//    @Test fun `play fake song, then advance`() = test {
//        val service = Robolectric.setupService(MusicService::class.java)
//
//        MusicService.enactNow(PlayerAction.PlaySongs(songs), false)
//        delay(50)
//
//        val player = service.player
//        var q = runBlocking { player.queue.firstOrNull() }
//        expect(q).notToBeNull {
//            property(subject::list).toBe(songs)
//            property(subject::position).toBe(0)
//            property(subject::current).notToBeNullBut(songs[0])
//        }
//
//        // go to next song
//        MusicService.enactNow(PlayerAction.RelativePosition(1), false)
//        delay(100)
//        q = runBlocking { player.queue.firstOrNull() }
//        expect(q).notToBeNull {
//            property(subject::list).toBe(songs)
//            property(subject::position).toBe(1)
//            property(subject::current).notToBeNullBut(songs[1])
//        }
//    }
}