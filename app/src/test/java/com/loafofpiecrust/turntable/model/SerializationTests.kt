package com.loafofpiecrust.turntable.model

import android.os.Bundle
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.parMap
import com.loafofpiecrust.turntable.util.ParcelSerializer
import com.loafofpiecrust.turntable.util.deserialize
import com.loafofpiecrust.turntable.util.serialize
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.util.*
import kotlin.test.*


class SerializationTests {
    init {
        // initialize kryo
        runBlocking { serialize(1) }
    }

    private val song = Song(
        SongId("Let It Be", AlbumId("Let It Be", ArtistId("The Beatles"))),
        track = 6,
        disc = 1,
        duration = 77500,
        year = 1970
    )

    @Test fun `single song`() = runBlocking<Unit> {
        val serialized = serialize(song)
        println(serialized.size)
        println(serialized.toString(Charsets.ISO_8859_1))
        val deserialized = deserialize(serialized)
        assert(deserialized).toBe(song)
    }

    @Test fun `song to string via base64`() = runBlocking<Unit> {
        val serialized = Base64.getEncoder().encodeToString(serialize(song))
        println(serialized.length)
        println(serialized)
        val deserialized = deserialize(Base64.getDecoder().decode(serialized))
        assert(deserialized).toBe(song)
    }

    @Test fun `channel of songs`() = runBlocking {
        val chan = ConflatedBroadcastChannel<List<Song>>()
        chan.offer(listOf(song))
        val serialized = serialize(chan)
        println(serialized.size)
        println(serialized.toString(Charsets.ISO_8859_1))
        val deserialized = (deserialize(serialized) as ConflatedBroadcastChannel<*>)
            .openSubscription().firstOrNull()
        val orig = chan.openSubscription().firstOrNull()

        assert(orig).notToBeNull {
            assert(deserialized).notToBeNullBut(subject)
        }
    }

    @Test fun `empty songs channel`() = runBlocking {
        val chan = ConflatedBroadcastChannel<List<Song>>()
        val serialized = serialize(chan)
        println(serialized.size)
        println(serialized.toString(Charsets.ISO_8859_1))
        val deserialized = deserialize(serialized) as ConflatedBroadcastChannel<*>
        assert(deserialized.valueOrNull).toBe(null)
    }

    @Test fun `refs and copies`() = runBlocking<Unit> {
        val orig = listOf(song, song, song.copy())
        val serd = serialize(orig)
        val deser = deserialize(serd) as List<Song>
        assert(deser).toBe(orig).and {
            assert(subject[1]).isSameAs(subject[0])
            assert(subject[2]).isNotSameAs(subject[1])
        }
    }

    @Test fun uuid() {
        runBlocking {
            data class Simple(val id: UUID = UUID.randomUUID())
            val orig = Simple()
            val serd = serialize(orig)
            val deser = deserialize(serd)
            assert(deser).toBe(orig)
        }
    }

    @Test fun `many songs in parallel`() {
        val songs = mutableListOf(song)
        for (i in 0..500) {
            songs.add(song)
        }

        val reser = runBlocking {
            songs.parMap {
                serialize(it)
            }.parMap {
                deserialize(it.await()) as Song
            }.awaitAll()
        }
        expect(reser).toBe(songs)
    }


    object Something
    @Test fun `singletons`() = runBlocking<Unit> {
        val orig = Something
        val ser = serialize(Something)
        print(ser)
        val deser = deserialize(ser)
        expect(deser).isSameAs(orig)
    }
}