package com.loafofpiecrust.turntable.ui

import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import org.junit.runner.RunWith

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
//        val titleEditor = activity.find<EditText>(R.uuid.title)
//        titleEditor.text.append("My First Playlist")
//        val accept = activity.find<Button>(R.uuid.positive_button)
//        accept.performClick()
//
//        Thread.sleep(100)
//
//        val playlists = runBlocking { UserPrefs.playlists.openSubscription().firstOrNull() }
//        expect(playlists?.firstOrNull()).notToBeNull {
//            isA<Playlist> {
//                property(subject::owner).toBe(MessageReceiverService.selfUser)
//                property(subject::name).toBe("My First Playlist")
//                expect(runBlocking { subject.tracks.first() }).toBe(listOf(track1, track2))
//            }
//        }
//    }
}