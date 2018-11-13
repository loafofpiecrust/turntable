package com.loafofpiecrust.turntable.model

import android.support.test.runner.AndroidJUnit4
import ch.tutteli.atrium.api.cc.en_GB.isNotEmpty
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.repository.remote.Spotify
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import kotlin.test.Test
import kotlin.test.expect

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    @Test fun searchSongs() = runBlocking<Unit> {
        // Killer Queen by Queen
        val results = Repositories.searchSongs("killer queen")
        println(results)
        expect(results).isNotEmpty()
    }

    @Test fun searchAlbums() {
        val results = runBlocking {
            // Sheer Heart Attack by Queen
            Repositories.searchAlbums("sheer heart attack")
        }
        println(results)
        expect(results).isNotEmpty()
    }

    @Test fun searchArtists() {
        val results = runBlocking {
            Repositories.searchArtists("khai dreams")
        }
        println(results)
        expect(results).isNotEmpty()
    }

    @Test fun combinedSearch() {
        val query = "yeek"
        val results: List<Music> = runBlocking {
            val songs = async { Repositories.searchSongs(query) }
            val albums = async { Repositories.searchAlbums(query) }
            val artists = async { Repositories.searchArtists(query) }
            songs.await() + albums.await() + artists.await()
        }
        println(results)
        expect(results).isNotEmpty()
    }
}