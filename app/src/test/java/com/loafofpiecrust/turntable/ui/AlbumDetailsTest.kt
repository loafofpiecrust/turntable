package com.loafofpiecrust.turntable.ui

//import ch.tutteli.atrium.api.cc.en_GB.isA
import android.content.res.Configuration
import android.os.Bundle
import ch.tutteli.atrium.api.cc.en_GB.isA
import ch.tutteli.atrium.api.cc.en_GB.isNotSameAs
import ch.tutteli.atrium.api.cc.en_GB.notToBeNull
import ch.tutteli.atrium.api.cc.en_GB.toBe
import ch.tutteli.atrium.verbs.expect
import com.loafofpiecrust.turntable.album.DetailsFragment
import com.loafofpiecrust.turntable.isA
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.LocalAlbum
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.model.song.SongId
import kotlinx.coroutines.experimental.channels.consume
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner


@RunWith(RobolectricTestRunner::class)
class AlbumDetailsTest {
    val artistId = ArtistId("Cashmere Cat")
    val albumId = AlbumId("9", artistId)
    val album = LocalAlbum(
        albumId,
        listOf(
            Song(
                SongId("Night Night (feat. Kehlani)", albumId.copy()),
                track = 1,
                disc = 1,
                duration = 60000 + 45000,
                year = 2017
            )
        )
    )

    @Test fun `main activity`() {
        var controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        var activity = controller.get()
        expect(activity.currentFragment).notToBeNull {
            isA<LibraryFragment>()
        }

        activity.replaceMainContent(DetailsFragment.fromAlbum(album))
        activity.supportFragmentManager.executePendingTransactions()

        var topFragment = activity.currentFragment
        expect(topFragment).notToBeNull {
            isA<DetailsFragment>()
        }

        val state = Bundle()
        controller.saveInstanceState(state)
        activity.finishAndRemoveTask()
    }

    @Test fun `orientation change`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val originalActivity = controller.get()
        originalActivity.replaceMainContent(DetailsFragment.fromAlbum(album))
        controller.configurationChange(Configuration().apply {
            setToDefaults()
            orientation = Configuration.ORIENTATION_LANDSCAPE
        })

        expect(controller.get()).isNotSameAs(originalActivity)
        expect(controller.get().currentFragment).notToBeNull {
            isA<DetailsFragment> {
//                val album = runBlocking { subject.album.openSubscription().consume { receive() } }
//                expect(album.id).toBe(albumId)
            }
        }
    }
}