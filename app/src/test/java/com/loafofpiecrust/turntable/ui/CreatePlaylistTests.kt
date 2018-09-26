package com.loafofpiecrust.turntable.ui

import android.widget.Button
import android.widget.EditText
import ch.tutteli.atrium.api.cc.en_GB.*
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.playlist.Playlist
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import com.loafofpiecrust.turntable.playlist.AddPlaylistDialog
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.sync.SyncService
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.firstOrNull
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CreatePlaylistTests {
    val disc1Id = AlbumId(
        "Jackson C. Frank (Disc 1)",
        ArtistId("Jackson C. Frank")
    )
    val track1 = Song(
        SongId("Blues Run The Game", disc1Id),
        track = 1,
        disc = 1,
        duration = 0,
        year = 1965
    )
    val track2 = Song(
        SongId("The Same as a Flower", AlbumId("The Same as a Flower", ArtistId("Nagisa Ni Te"))),
        track = 1,
        disc = 1,
        duration = 60000,
        year = 2004
    )

//    @Test fun `create playlist`() {
//        val activity = Robolectric.buildActivity(
//            AddPlaylistDialog::class.java,
//            AddPlaylistActivityStarter.getIntent(
//                App.instance,
//                AddPlaylistDialog.TrackList(listOf(track1, track2))
//            )
//        ).setup().get()
//
//        val titleEditor = activity.find<EditText>(R.id.title)
//        titleEditor.text.append("My First Playlist")
//        val accept = activity.find<Button>(R.id.positive_button)
//        accept.performClick()
//
//        Thread.sleep(100)
//
//        val playlists = runBlocking { UserPrefs.playlists.openSubscription().firstOrNull() }
//        expect(playlists?.firstOrNull()).notToBeNull {
//            isA<Playlist> {
//                property(subject::owner).toBe(SyncService.selfUser)
//                property(subject::name).toBe("My First Playlist")
//                expect(runBlocking { subject.tracks.first() }).toBe(listOf(track1, track2))
//            }
//        }
//    }
}