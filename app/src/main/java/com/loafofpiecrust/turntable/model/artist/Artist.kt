package com.loafofpiecrust.turntable.model.artist

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.View
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.artist.BiographyFragment
import com.loafofpiecrust.turntable.artist.RelatedArtistsUI
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.repository.local.SearchCache
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.model.queue.RadioQueue
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.universal.createFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.produce
import org.jetbrains.anko.toast

interface Artist: Music {
    override val id: ArtistId

    /// Discography
    val albums: List<Album>

    /// First year the artist is active
    val startYear: Int?

    /// Last year active, or null if still active
    val endYear: Int?

    /// Personal biographical info
    /// Specific to remote artists
    /// TODO: Move biography to somewhere involved with [RemoteArtist]
    val biography: String?


    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.loadArtistImage(req, this.id).map {
            (it ?: SearchCache.fullArtwork(this)?.let { req.load(it) })
                ?.apply(RequestOptions().signature(ObjectKey("${this.id}thumbnail")))
        }

    fun loadArtwork(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.loadArtistImage(req, this@Artist.id)
            send(localArt.receive() ?: req.load(Repositories.fullArtwork(this@Artist, true)))

            sendFrom(localArt.filterNotNull())
        }


//    override fun optionsMenu(context: Context, menu: Menu) = with(menu) {
//        menuItem(R.string.artist_show_similar).onClick {
//            context.replaceMainContent(
//                RelatedArtistsUI(id).createFragment()
//            )
//        }
//
//        menuItem(R.string.recommend).onClick {
//            FriendPickerDialog(
//                Message.Recommend(toPartial()),
//                context.getString(R.string.recommend)
//            ).show(context)
//        }
//
//        // TODO: Sync with radios...
//        // TODO: Sync with any type of queue!
//        menuItem(R.string.radio_start).onClick(Dispatchers.Default) {
//            MusicService.offer(PlayerAction.Pause(), false)
//
//            val radio = RadioQueue.fromSeed(listOf(this@Artist))
//            if (radio != null) {
////                MusicService.offer(Sync.Message.ReplaceQueue(radio))
//                MusicService.offer(PlayerAction.Play())
//            } else {
//                context.toast(context.getString(R.string.radio_no_data, id.displayName))
//            }
//        }
//
//        menuItem(R.string.artist_biography).onClick(Dispatchers.Default) {
//            BiographyFragment.fromChan(produceSingle(this@Artist)).show(context)
//        }
//    }

}

fun Artist.loadPalette(vararg views: View)
    = loadPalette(this.id, views)

fun Menu.artistOptions(context: Context, artist: Artist) {
    menuItem(R.string.artist_show_similar).onClick {
        context.replaceMainContent(
            RelatedArtistsUI(artist.id).createFragment()
        )
    }

    menuItem(R.string.recommend).onClick {
        FriendPickerDialog(
            Message.Recommend(artist.id),
            context.getString(R.string.recommend)
        ).show(context)
    }

    // TODO: Sync with radios...
    // TODO: Sync with any type of queue!
    menuItem(R.string.radio_start).onClick(Dispatchers.Default) {
        MusicService.offer(PlayerAction.Pause, false)

        val radio = RadioQueue.fromSeed(listOf(artist))
        if (radio != null) {
//                MusicService.offer(Sync.Message.ReplaceQueue(radio))
            MusicService.offer(PlayerAction.Play)
        } else {
            context.toast(context.getString(R.string.radio_no_data, artist.id.displayName))
        }
    }

    menuItem(R.string.artist_biography).onClick(Dispatchers.Default) {
        BiographyFragment.fromChan(produceSingle(artist)).show(context)
    }
}