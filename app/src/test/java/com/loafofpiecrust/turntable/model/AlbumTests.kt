package com.loafofpiecrust.turntable.model

import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.album.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.repository.Repositories
import io.paperdb.Paper
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test


class LocalAlbumTests {
    val artistId = ArtistId("Cashmere Cat")

    @Test fun `basic info`() {
        val albumId = AlbumId("9", artistId)
        val album = LocalAlbum(
            albumId,
            listOf(
                Song(
                    SongId("Night Night (feat. Kehlani)", albumId.copy()),
                    track = 1,
                    disc = 1,
                    duration = 60000 + 45000,
                    year = 2017
                )
            )
        )

        val tracks = runBlocking {
            album.resolveTracks()
        }

        assert(album.id).toBe(album.id)
        assert(album.type).toBe(Album.Type.SINGLE)
        assert(album.year).toBe(2017)
        assert(tracks.first().id.displayName).toBe("Night Night")
    }

    @Test fun `types and EPs`() {
        val albumId = AlbumId("Wedding Bells EP", artistId)
        val track1 = Song(
            SongId("With Me", albumId),
            track = 1,
            disc = 1,
            duration = 300000,
            year = 2014
        )
        val album = LocalAlbum(
            albumId,
            listOf(
                track1,
                track1.copy(id = SongId("Pearls", albumId), track = 2),
                track1.copy(id = SongId("Wedding Bells", albumId), track = 3),
                track1.copy(id = SongId("Rice Rain", albumId), track = 4)
            )
        )
        assert(album.id.displayName).toBe("Wedding Bells")
        assert(album.type).toBe(Album.Type.EP)
        assert(album.hasTrackGaps).toBe(false)
    }

    /**
     * TODO: Make merging happen in a better spot?
     * We want to keep track and disc numbers as inherent information of a song
     */
    @Test fun `merge two local discs`() {
        val disc1Id = AlbumId(
            "Jackson C. Frank (Disc 1)",
            ArtistId("Jackson C. Frank")
        )
        val disc2Id = AlbumId(
            "Jackson C. Frank (Disc 2)",
            ArtistId("Jackson C. Frank")
        )
        val track1 = Song(
            SongId("Blues Run The Game", disc1Id),
            track = 1,
            disc = 1,
            duration = 0,
            year = 1965
        )
        val disc1 = LocalAlbum(
            disc1Id,
            listOf(track1)
        )
        val disc2 = LocalAlbum(
            disc2Id,
            listOf(
                track1.copy(id = SongId("Don't Look Back", disc2Id))
            )
        )

        val finalAlbum = MergedAlbum(disc1, disc2)
        val tracks = runBlocking {
            finalAlbum.resolveTracks()
        }

        assert(finalAlbum.id.displayName).toBe("Jackson C. Frank")
        assert(tracks.size).toBe(2)
        assert(finalAlbum.year).toBe(1965)
    }

//    @Test fun `find online`() {
//        Paper.init(File("./build/temp"))
//        val albumId = AlbumId("Wedding Bells", ArtistId("Cashmere Cat"))
//        val remote = runBlocking { Repositories.find(albumId) }
//        expect(remote).notToBeNull {
//            isA<RemoteAlbum> {
//                println(subject.remoteId)
//
//                property(subject::id).toBe(albumId)
//                // FIXME: Doesn't give type EP!
////                property(subject::type).toBe(Album.Type.EP)
//                // EP "Wedding Bells" by Cashmere Cat has 4 tracks
//                expect(runBlocking { subject.resolveTracks() }).hasSize(4)
//            }
//        }
//    }
}