package com.loafofpiecrust.turntable.model

import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.typedToJson
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.sync.Message
import com.loafofpiecrust.turntable.model.sync.PlayerAction
import com.loafofpiecrust.turntable.parMap
import de.javakaffee.kryoserializers.dexx.ListSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.internal.ArrayListSerializer
import kotlinx.serialization.json.JSON
import kotlinx.serialization.parse
import kotlinx.serialization.stringify
import java.util.*
import kotlin.test.*


class SerializationTests {
    init {
        // initialize kryo
        runBlocking { serialize(1) }
    }

    inline fun <reified T : Any> serialize(obj: T): String {
        return App.gson.typedToJson(obj)
    }

    inline fun <reified T : Any> deserialize(bytes: String): T {
        return App.gson.fromJson(bytes)
    }

    private val song = Song(
        SongId("Let It Be", AlbumId("Let It Be", ArtistId("The Beatles"))),
        track = 6,
        disc = 1,
        duration = 77500,
        year = 1970
    )

    @Test
    fun `single song`() = runBlocking<Unit> {
        val serialized = App.gson.typedToJson(song)
        println(serialized)
        val deserialized: Song = App.gson.fromJson(serialized)
        assert(deserialized).toBe(song)
    }

    @Test
    fun `list of songs`() {
        val list = listOf(song)
        val serialized = App.gson.toJson(list)
        val deserialized: List<Song> = App.gson.fromJson(serialized)
        expect(deserialized).toBe(list)
    }

    @Test
    fun `channel of songs`() = runBlocking<Unit> {
        val chan = ConflatedBroadcastChannel<List<Song>>(listOf(song))
        val serialized = App.gson.typedToJson(chan)
//        println(serialized.size)
        println(serialized)
        val deserialized = (App.gson.fromJson<ConflatedBroadcastChannel<List<Song>>>(serialized))
            .openSubscription().firstOrNull()
        val orig = chan.openSubscription().firstOrNull()

        assertNotNull(orig)
        assertNotNull(deserialized)
        assert(deserialized).toBe(orig)
    }

    data class ChanContainer(val chan: ConflatedBroadcastChannel<List<Song>>)

    @Test
    fun `empty songs channel`() {
        val chan = ChanContainer(ConflatedBroadcastChannel())
        val serialized = App.gson.typedToJson(chan)
        println(serialized)
        val deserialized: ChanContainer = App.gson.fromJson(serialized)
        assert(deserialized.chan.valueOrNull).toBe(null)
    }

    data class UUIDContainer(val id: UUID = UUID.randomUUID())

    @Test
    fun uuid() {
//        runBlocking {
        val orig = UUIDContainer()
        val serd = App.gson.typedToJson(orig)
        val deser: UUIDContainer = App.gson.fromJson(serd)
        expect(deser).toBe(orig)
//        }
    }

    @Test
    fun `many songs in parallel`() {
        val songs = mutableListOf(song)
        for (i in 0..10000) {
            songs.add(song)
        }

        val reser = runBlocking(Dispatchers.IO) {
            songs.parMap {
                deserialize<Song>(serialize(it))
            }.awaitAll()
        }
        expect(reser).toBe(songs)
    }

    @Test
    fun `many songs in sequence`() {
        val songs = mutableListOf(song)
        for (i in 0..10000) {
            songs.add(song)
        }

        val reser = songs.map {
            deserialize<Song>(serialize(it))
        }
        expect(reser).toBe(songs)
    }


    object Something

    @Test
    fun `singletons`() = runBlocking<Unit> {
        val orig = Something
        val ser = serialize(Something)
        print(ser)
        val deser: Something = deserialize(ser)
        expect(deser).toBe(orig)
    }

    @Test
    fun `polymorphic singleton`() {
        val msg: Message = PlayerAction.TogglePause
        val ser = App.gson.typedToJson(msg)
        println(ser)
        val deser = App.gson.fromJson<Message>(ser)
        expect(deser).toBe(msg)
    }

    @Test
    fun `polymorphic message`() {
        val msg: Message = PlayerAction.PlaySongs(listOf(song))
        val ser = App.gson.typedToJson(msg)
        println(ser)
        val deser: Message = App.gson.fromJson(ser)
        expect(deser).toBe(msg)
    }

    @Test
    fun `polymorphic list`() {
        val items = listOf<MusicId>(
            song.id,
            AlbumId("Hello", ArtistId("Somebody"))
        )
        val deser: List<MusicId> = deserialize(serialize(items))
        expect(deser).toBe(items)
    }
}