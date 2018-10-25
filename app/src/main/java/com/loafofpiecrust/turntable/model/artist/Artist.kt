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
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.createFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.util.produceSingle
import com.loafofpiecrust.turntable.util.redirectTo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.produce
import org.jetbrains.anko.toast

interface Artist: Music {
    override val id: ArtistId
    val albums: List<Album>
    val startYear: Int?
    val endYear: Int?
    val biography: String?

    data class Member(
        val name: String,
        val id: String,
        val active: Boolean
    )

    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.instance.loadArtistImage(req, this.id).map {
            (it ?: SearchCache.fullArtwork(this)?.let { req.load(it) })
                ?.apply(RequestOptions().signature(ObjectKey("${this.id}thumbnail")))
        }

    fun loadArtwork(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.instance.loadArtistImage(req, this@Artist.id)
            send(localArt.receive() ?: req.load(Repository.fullArtwork(this@Artist, true)))

            localArt.filterNotNull().redirectTo(channel)
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
        MusicService.offer(PlayerAction.Pause(), false)

        val radio = RadioQueue.fromSeed(listOf(artist))
        if (radio != null) {
//                MusicService.offer(Sync.Message.ReplaceQueue(radio))
            MusicService.offer(PlayerAction.Play())
        } else {
            context.toast(context.getString(R.string.radio_no_data, artist.id.displayName))
        }
    }

    menuItem(R.string.artist_biography).onClick(Dispatchers.Default) {
        BiographyFragment.fromChan(produceSingle(artist)).show(context)
    }
}