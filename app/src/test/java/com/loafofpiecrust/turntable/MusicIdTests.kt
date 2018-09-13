package com.loafofpiecrust.turntable

import ch.tutteli.atrium.api.cc.en_GB.toBe
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.selfTitledAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.SongId
import kotlin.test.Test
import ch.tutteli.atrium.verbs.assert
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class AlbumIdTests {
    @Test fun `simple self-titled`() {
        val id = AlbumId("Violent Femmes", ArtistId("Violent Femmes"))
        assert(id.selfTitledAlbum).toBe(true)
        assert(id.displayName).toBe(id.name)
    }

    @Test fun `disc of self-titled`() {
        val artist = ArtistId("Violent Femmes")
        val basic = AlbumId("Violent Femmes", artist)
        val disc1 = AlbumId(basic.name + " (Disc 1)", artist)
        val disc2 = AlbumId(basic.name + " (Disc 2)", artist)

        assert(disc1.selfTitledAlbum).toBe(true)
        assert(disc1.discNumber).toBe(1)
        assert(disc1.displayName).toBe(basic.displayName)
        assert(disc2.displayName).toBe(disc1.displayName)
        assert(disc1).toBe(basic)
        assert(disc1).toBe(disc2)
        assert(disc2.hashCode()).toBe(disc1.hashCode())
    }

    @Test fun `displayName with disc and edition`() {
        val id = AlbumId(
            "The 20/20 Experience [Deluxe Edition] (Disc 2)",
            ArtistId("Justin Timberlake")
        )
        assertFalse(id.selfTitledAlbum)
        assertEquals("The 20/20 Experience", id.displayName)
        assertEquals(2, id.discNumber)

        val deluxe = AlbumId(
            "The Spirit Moves (Deluxe Edition)",
            ArtistId("Longhorne Slim")
        )
        assertEquals("The Spirit Moves", deluxe.displayName)
    }

    @Test fun `distinct albums with numbers`() {
        val vol2 = AlbumId(
            "Lost at Last Vol. 2",
            ArtistId("Longhorne Slim")
        )
        assertEquals(vol2.name, vol2.displayName)
        assertEquals(1, vol2.discNumber)
    }

    @Test fun `case insensitivity`() {
        val a = AlbumId("violenT fEmmes", ArtistId("VioLenT feMmEs"))
        val b = AlbumId("ViolenT FEmmes", ArtistId("VIOLENT FEMMES"))
        assertTrue(a.selfTitledAlbum)
        assertTrue(b.selfTitledAlbum)
        assertEquals(a.dbKey, b.dbKey)
        assertEquals(a, b)
        assertEquals(0, a.compareTo(b))
    }
}

class SongIdTests {
    @Test fun `basic info`() {
        val song = SongId(
            "Infinite Stripes (feat. Ty Dolla \$ign)",
            AlbumId("9", ArtistId("Cashmere Cat"))
        )
        assertEquals("Infinite Stripes", song.displayName)
        assertEquals(listOf(ArtistId("Ty Dolla \$ign")), song.features)
        assertEquals("Cashmere Cat", song.artist.displayName)
        assertEquals("Cashmere Cat", song.album.artist.displayName)
    }

    @Test fun `more featured artists`() {
        val song = SongId(
            "Wild Love (feat. The Weeknd & Francis and the Lights)",
            AlbumId("9", ArtistId("Cashmere Cat"))
        )
        assertEquals(listOf(
            ArtistId("The Weeknd"),
            ArtistId("Francis and the Lights")
        ), song.features)
    }

    @Test fun `database key`() {
        val song = SongId(
            "Help",
            AlbumId("Help", ArtistId("The Beatles"))
        )
        assertEquals('B', song.album.artist.sortChar)
        assertEquals("help~help~beatles", song.dbKey)
        assertEquals("help~beatles", song.album.dbKey)
        assertEquals("Help | Help | The Beatles", song.toString())
        assertEquals("Help | The Beatles", song.album.toString())
    }
}
