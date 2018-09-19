package com.loafofpiecrust.turntable.model

import ch.tutteli.atrium.api.cc.en_GB.and
import ch.tutteli.atrium.api.cc.en_GB.returnValueOf
import ch.tutteli.atrium.api.cc.en_GB.property
import ch.tutteli.atrium.api.cc.en_GB.toBe
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.selfTitledAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.SongId
import kotlin.test.Test
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect

class AlbumIdTests {
    @Test fun `simple self-titled`() {
        val id = AlbumId("Violent Femmes", ArtistId("Violent Femmes"))
        expect(id.selfTitledAlbum).toBe(true)
        expect(id.displayName).toBe(id.name)
    }

    @Test fun `disc of self-titled`() {
        val artist = ArtistId("Violent Femmes")
        val basic = AlbumId("Violent Femmes", artist)
        val disc1 = AlbumId(basic.name + " (Disc 1)", artist)
        val disc2 = AlbumId(basic.name + " (Disc 2)", artist)

        expect(disc1) {
            toBe(basic).and.toBe(disc2)
            returnValueOf(subject::hashCode).toBe(disc2.hashCode())
            property(subject::selfTitledAlbum).toBe(true)
            property(subject::discNumber).toBe(1)
            property(subject::displayName).toBe(basic.displayName).and.toBe(disc2.displayName)
        }
    }

    @Test fun `displayName with disc and edition`() {
        val id = AlbumId(
            "The 20/20 Experience [Deluxe Edition] (Disc 2)",
            ArtistId("Justin Timberlake")
        )
        expect(id) {
            property(subject::selfTitledAlbum).toBe(false)
            property(subject::displayName).toBe("The 20/20 Experience")
            property(subject::discNumber).toBe(2)
        }

        val deluxe = AlbumId(
            "The Spirit Moves (Deluxe Edition)",
            ArtistId("Longhorne Slim")
        )
        assert(deluxe.displayName).toBe("The Spirit Moves")
    }

    @Test fun `distinct albums with numbers`() {
        val vol2 = AlbumId(
            "Lost at Last Vol. 2",
            ArtistId("Longhorne Slim")
        )
        expect(vol2) {
            property(subject::displayName).toBe(subject.name)
            property(subject::discNumber).toBe(1)
        }
    }

    @Test fun `case insensitivity`() {
        val a = AlbumId("violenT fEmmes", ArtistId("VioLenT feMmEs"))
        val b = AlbumId("ViolenT FEmmes", ArtistId("VIOLENT FEMMES"))
        expect(a) {
            toBe(b)
            returnValueOf(subject::compareTo, b).toBe(0)
            property(subject::selfTitledAlbum).toBe(true)
            property(subject::dbKey).toBe(b.dbKey)
        }
    }
}

class SongIdTests {
    @Test fun `basic info`() {
        val song = SongId(
            "Infinite Stripes (feat. Ty Dolla \$ign)",
            AlbumId("9", ArtistId("Cashmere Cat"))
        )
        expect(song) {
            property(subject::displayName).toBe("Infinite Stripes")
            property(subject::features).toBe(listOf(ArtistId("Ty Dolla \$ign")))
        }
        expect(song.artist) {
            property(subject::displayName).toBe("Cashmere Cat")
        }
        expect(song.album.artist) {
            property(subject::displayName).toBe("Cashmere Cat")
        }
    }

    @Test fun `more featured artists`() {
        val song = SongId(
            "Wild Love (feat. The Weeknd & Francis and the Lights)",
            AlbumId("9", ArtistId("Cashmere Cat"))
        )
        expect(song.features).toBe(listOf(
            ArtistId("The Weeknd"),
            ArtistId("Francis and the Lights")
        ))
    }

    @Test fun `database key`() {
        val song = SongId(
            "Help",
            AlbumId("Help", ArtistId("The Beatles"))
        )
        expect(song) {
            property(subject::dbKey).toBe("help~help~beatles")
            returnValueOf(subject::toString).toBe("Help | Help | The Beatles")

            property(subject::album) {
                property(subject::dbKey).toBe("help~beatles")
                returnValueOf(subject::toString).toBe("Help | The Beatles")
            }
        }
    }
}
