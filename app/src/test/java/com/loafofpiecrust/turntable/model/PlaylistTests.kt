package com.loafofpiecrust.turntable.model

import android.graphics.Color
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.assert
import ch.tutteli.atrium.verbs.expect
import com.github.salomonbrys.kotson.fromJson
import com.github.salomonbrys.kotson.typedToJson
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.*
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.model.sync.User
import kotlinx.coroutines.channels.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

class PlaylistTests {
    val id = PlaylistId("for you!")
    val user = User(
        "name@website.com",
        "<deviceId>",
        "Lemony Snicket"
    )
    val cloverSaloon = Song(
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
    val meOnTheBeach = Song(
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
    val redHouse = Song(
        SongId("Red House", AlbumId("Greatest Hits", ArtistId("Jimi Hendrix"))),
        track = 5, disc = 1,
        duration = 55860,
        year = 1973
    )
    val songs = listOf(
        cloverSaloon,
        meOnTheBeach,
        redHouse
    )

    inline fun <reified T: Any> copyObj(obj: T): T {
        return App.gson.fromJson(App.gson.typedToJson(obj))
    }

    suspend fun SongPlaylist.updateSync() {
        delay(2)
        lastSyncTime = System.currentTimeMillis()
        delay(2)
    }


    @Test fun `serialization`() = runBlocking<Unit> {
        val mixtape = SongPlaylist(
            PlaylistId("For My Love", user)
        )
        val serd = App.gson.toJson(mixtape)
        val deserd: MixTape = App.gson.fromJson(serd)
        assert(deserd.tracks).toBe(mixtape.resolveTracks())
    }

//    @Test fun `basic mixtape`() {
//        val mixtape = MutableMixtape(
//            PlaylistId("For My Love", user),
//            MixTape.Type.C60,
//            Color.BLUE
//        )
//        mixtape.addAll(0, listOf(songs[0]))
//
//        // TODO: Make this international? Google what Japanse tape sides were called.
//        val sides = mixtape.type.sideNames
//        expect(sides).contains.inOrder.only.entries(
//            { contains("A") },
//            { contains("B") }
//        )
//        expect(mixtape.type.totalLength).toBe(60)
//
//        val side1 = runBlocking { mixtape.tracksOnSide(0).firstOrNull() }
//        expect(side1!!).toBe(listOf(songs[0]))
//    }

//    @Test fun operations() = runBlocking<Unit> {
//        val playlist = CollaborativePlaylist(PlaylistId("All Good Things...", user), Color.GREEN)
//        expect(playlist.add(songs[1])).toBe(true)
//        expect(playlist.add(songs[0])).toBe(true)
//        delay(10)
//        expect(playlist.tracks).toBe(listOf(songs[1], songs[0]))
//        playlist.move(1, 0)
//        playlist.remove(0)
//        expect(playlist.tracks).toBe(listOf(songs[1]))
//        playlist.remove(0)
//        expect(playlist.tracks).isEmpty()
//    }

    @Test fun `general parallel add`() = runBlocking<Unit> {
        val original = SongPlaylist(id)
        original.add(redHouse)
        original.updateSync()

        val branch = copyObj(original)
        branch.add(meOnTheBeach)

        val origMerged = copyObj(original)
        origMerged.mergeWith(branch)
        delay(2)
        expect(origMerged.resolveTracks()).toBe(listOf(redHouse, meOnTheBeach))

        branch.mergeWith(original)
        delay(2)
        expect(branch.resolveTracks()).toBe(origMerged.resolveTracks())
    }

    @Test fun `add, remote remove`() = runBlocking<Unit> {
        val original = SongPlaylist(id)
        original.add(redHouse)
        original.updateSync()

        val branch = copyObj(original)
        branch.remove(redHouse.id)
        branch.updateSync()

        val needed = original.mergeWith(branch)
        delay(2)
        expect(needed).toBe(true)
        expect(original.resolveTracks()).isEmpty()
    }

    @Test fun general() = runBlocking<Unit> {
        // Original user creates playlist.
        val original = SongPlaylist(id)
        original.add(cloverSaloon)
        // upload it to the database
        original.updateSync()
        expect(original.resolveTracks()).toBe(listOf(cloverSaloon))

        // Another user starts editing the playlist.
        val remote = copyObj(original)
        remote.add(meOnTheBeach)
        remote.remove(cloverSaloon.id)
        // they sync with the database
        remote.updateSync()
        expect(remote.resolveTracks()).toBe(listOf(meOnTheBeach))

        original.add(redHouse)
        expect(original.resolveTracks()).toBe(listOf(cloverSaloon, redHouse))

        // Original user merges with remote version
        original.mergeWith(remote)
        delay(2)
        print(original.resolveTracks())
        expect(original.resolveTracks()).toBe(listOf(meOnTheBeach, redHouse))
    }

    @Test fun `general with moves`() = runBlocking<Unit> {
        val id = PlaylistId("for you!")
        val original = SongPlaylist(id)

        original.add(cloverSaloon)
        original.add(meOnTheBeach)
        // sync
        original.updateSync()
        expect(original.resolveTracks()).toBe(listOf(cloverSaloon, meOnTheBeach))

        // branch
        val branch = copyObj(original)
        branch.move(meOnTheBeach.id, cloverSaloon.id)
        expect(branch.resolveTracks()).toBe(listOf(meOnTheBeach, cloverSaloon))

        delay(2)

        original.add(redHouse)
        expect(original.resolveTracks()).toBe(listOf(cloverSaloon, meOnTheBeach, redHouse))

        delay(2)

        val branchMerged = copyObj(branch)
        branchMerged.mergeWith(original)
        delay(2)
        expect(branchMerged.resolveTracks()).toBe(listOf(meOnTheBeach, cloverSaloon, redHouse))

        original.mergeWith(branch)
        delay(2)
        expect(original.resolveTracks()).toBe(branchMerged.resolveTracks())
    }
}