package com.loafofpiecrust.turntable

import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.util.deserialize
import com.loafofpiecrust.turntable.util.serialize
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.runBlocking
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotSame

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SerializationTests {
    private val song = Song(
        SongId("Let It Be", AlbumId("Let It Be", ArtistId("The Beatles"))),
        track = 6,
        disc = 1,
        duration = 77500,
        year = 1970
    )

    @BeforeAll fun initKryo() {
        runBlocking { serialize(1) }
    }

    @Test fun songs() = runBlocking {
        val serialized = serialize(song)
        println(serialized.size)
        println(serialized.toString(Charsets.ISO_8859_1))
        val deserialized = deserialize<Song>(serialized)
        assertNotSame(song, deserialized)
        assertEquals(song, deserialized)
    }

    @Test fun `song to string via base64`() = runBlocking {
        val serialized = Base64.getEncoder().encodeToString(serialize(song))
        println(serialized.length)
        println(serialized)
        val deserialized = deserialize<Song>(Base64.getDecoder().decode(serialized))
        assertEquals(song, deserialized)
    }

    @Test fun `channel of songs`() = runBlocking {
        val chan = ConflatedBroadcastChannel<List<Song>>()
        chan.offer(listOf(song))
        val serialized = serialize(chan)
        println(serialized.size)
        println(serialized.toString(Charsets.ISO_8859_1))
        val deserialized = deserialize<ConflatedBroadcastChannel<List<Song>>>(serialized)
        assertEquals(chan.value, deserialized.value)
    }
}