package com.loafofpiecrust.turntable.model

import android.os.Debug
import android.support.test.runner.AndroidJUnit4
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.LocalSongId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.lazy
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify
import kotlinx.coroutines.experimental.channels.ConflatedBroadcastChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.runBlocking
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.util.*
import kotlin.test.Test

fun Random.nextChar() = nextInt(8).toChar()
fun Random.nextString(length: Int = 20): String {
    return (0..length).lazy
        .map { nextChar() }
        .joinToString("")
}

//@MediumTest
@RunWith(RobolectricTestRunner::class)
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

    private fun generateSongs(length: Int): List<Song> {
        return (0..length).map { randomSong(length) }
    }

    @org.junit.Test
    fun longRuntimes() = runBlocking<Unit> {
//        Robolectric.setupActivity(MainActivity::class.java)

        val library = Library()

        // Spotify's library limit is 10,000 saved songs.
        // My personal library (not that large) is ~12,000 songs
        // If I saved all I wanted to, my library would have >=20,000 songs
        library.localSongs.offer(generateSongs(15000))

        val songs = library.songs.openSubscription().first { it.isNotEmpty() }
        val albums = library.albums.openSubscription().first { it.isNotEmpty() }
        val artists = library.artists.openSubscription().first { it.isNotEmpty() }
        val songsMap = library.songsMap.openSubscription().first { it.isNotEmpty() }
        val artistsMap = library.artistsMap.openSubscription().first { it.isNotEmpty() }

        println("song count: ${songs.size}")
        println("album count: ${albums.size}")
        println("artist count: ${artists.size}")
    }
}