package com.loafofpiecrust.turntable.model

import android.app.Application
import android.os.Debug
import androidx.test.core.app.ApplicationProvider
import androidx.test.runner.AndroidJUnit4
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.LocalAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.LocalSongId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.toListSortedBy
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.lazy
import com.loafofpiecrust.turntable.util.measureTime
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.channels.first
import kotlinx.coroutines.runBlocking
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.junit.runner.RunWith
import java.util.*
import org.junit.Test
import kotlin.collections.HashMap
import kotlin.system.measureNanoTime
import kotlin.system.measureTimeMillis

fun Random.nextChar() = nextInt(8).toChar()
fun Random.nextString(length: Int = 20): String {
    return (0..length).lazy
        .map { nextChar() }
        .joinToString("")
}

//@MediumTest
@RunWith(AndroidJUnit4::class)
class LibraryTest {
    private val random = Random()
//    private val library by lazy { Library() }

    private fun randomSong(count: Int): Song {
        return Song(
            SongId(random.nextString(20), AlbumId(random.nextString(4), ArtistId(random.nextString(2)))),
            random.nextInt(),
            random.nextInt(),
            random.nextInt(),
            random.nextInt(),
            platformId = LocalSongId(random.nextInt(count).toLong(), random.nextInt(count / 8).toLong(), random.nextInt(count / 24).toLong())
        )
    }

    private fun randomAlbum(length: Int) = LocalAlbum(
        AlbumId(random.nextString(4), ArtistId(random.nextString(2))),
        (0..10).map { randomSong(length * 10) }
    )

    private fun generateSongs(length: Int): List<Song> {
        return (0..length).map { randomSong(length) }
    }

    private fun generateAlbums(length: Int) =
        (0..length).map { randomAlbum(length) }

    @Test
    fun longRuntimes() = runBlocking<Unit> {
//        Robolectric.setupActivity(MainActivity::class.java)

        // Spotify's library limit is 10,000 saved songs.
        // My personal library (not that large) is ~12,000 songs
        // If I saved all I wanted to, my library would have >=20,000 songs
        Library.localSongs.offer(generateSongs(15000))

        val songs = Library.songsMap.openSubscription().first { it.isNotEmpty() }
        val albums = Library.albumsMap.openSubscription().first { it.isNotEmpty() }
        val artists = Library.artistsMap.openSubscription().first { it.isNotEmpty() }

        println("song count: ${songs.size}")
        println("album count: ${albums.size}")
        println("artist count: ${artists.size}")
    }


    @Test fun `tree vs hash maps`() = runBlocking<Unit> {
//        val songs = generateSongs(20000)
        val albums = generateAlbums(500)
        val example = albums[400].resolveTracks()[3]

        // first insert into treemap then display
        val tree = measureTime("treemap") {
            albums.lazy.flatMap {
                runBlocking { it.resolveTracks().lazy }
            }.map { it.id to it }.toMap(TreeMap())
        }
        measureTime("treemap sorted") {
            val display = tree.values.toList()
        }

        val hash = measureTime("hashmap") {
            albums.lazy.flatMap {
                runBlocking { it.resolveTracks().lazy }
            }.map { it.id to it }.toMap(HashMap())
        }
        measureTime("hashmap sorted") {
            val display = hash.values.sortedBy { it.id }
        }
        measureTime("hashmap fuzzy search") {
            hash.entries.filter { FuzzySearch.ratio(it.key.displayName, example.id.displayName) >= 70 }
        }

        val list = measureTime("sorted list") {
            albums.lazy.flatMap {
                runBlocking { it.resolveTracks().lazy }
            }.toListSortedBy { it.id }
        }
        measureTime("sorted list for display") {
            val display = list
        }
        measureTime("sorted list fuzzy search") {
            list.filter { FuzzySearch.ratio(it.id.displayName, example.id.displayName) >= 70 }
        }
    }
}