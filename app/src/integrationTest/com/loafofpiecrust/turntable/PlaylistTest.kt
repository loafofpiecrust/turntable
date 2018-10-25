package com.loafofpiecrust.turntable

import ch.tutteli.atrium.api.cc.en_GB.isNotEmpty
import ch.tutteli.atrium.verbs.expect
import com.github.daemontus.Result
import com.loafofpiecrust.turntable.repository.remote.Spotify
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaylistTest {
    @Test
    fun fromSpotify() = runBlocking<Unit> {
        val playlist = CollaborativePlaylist.fromSpotifyPlaylist("1260492579", "4A6xyxDQciDkVCXZ9MNCwv?si=rUDsLvErS4OryHE2r3_Yrg")
        when (playlist) {
            is Result.Ok -> playlist.ok.let { playlist ->
                val tracks = playlist.tracks.first()
                println("${tracks.size} tracks on ${playlist.name} by ${playlist.owner.displayName}")
                println(tracks)
                expect(tracks).isNotEmpty()
            }
            is Result.Error -> {
                playlist.error.printStackTrace()
            }
        }
    }

    @Test
    fun fromSpotifyUser() = runBlocking<Unit> {
        val playlists = Spotify.playlistsByUser("1260492579")
        println(playlists)
        expect(playlists).isNotEmpty()
    }
}