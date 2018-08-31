package com.loafofpiecrust.turntable.artist

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Menu
import android.view.View
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.radio.RadioQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Music
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceSingle
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.toast

interface Artist: Music {
    val id: ArtistId
    val albums: List<Album>
    val startYear: Int?
    val endYear: Int?
    val biography: String?

    override val displayName: String get() = id.displayName

    data class Member(
        val name: String,
        val id: String,
        val active: Boolean
    )


    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> = loadArtwork(req)
    fun loadArtwork(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.instance.loadArtistImage(req, id).map {
            (it ?: SearchApi.fullArtwork(this, true)?.let {
                req.load(it)
            })?.apply(Library.ARTWORK_OPTIONS
                .signature(ObjectKey("${id}full"))
            )
        }


    override fun optionsMenu(ctx: Context, menu: Menu) = with(menu) {
        menuItem("Similar Artists", R.drawable.ic_people, showIcon=false).onClick {
            ctx.replaceMainContent(
                ArtistsFragment.relatedTo(id),
                true
            )
        }

        menuItem("Recommend", showIcon=false).onClick {
            FriendPickerDialog(
                SyncService.Message.Recommendation(this@Artist.id),
                "Send Recommendation"
            ).show(ctx)
        }

        // TODO: Sync with radios...
        // TODO: Sync with any type of queue!
        menuItem("Start Radio", showIcon=false).onClick(BG_POOL) {
            MusicService.enact(SyncService.Message.Pause(), false)

            val radio = RadioQueue.fromSeed(listOf(this@Artist))
            if (radio != null) {
//                MusicService.enact(SyncService.Message.ReplaceQueue(radio))
                MusicService.enact(SyncService.Message.Play())
            } else {
                ctx.toast("Not enough data on '${id.displayName}'")
            }
        }

        menuItem("Biography", showIcon=false).onClick(BG_POOL) {
            BiographyFragment.fromChan(produceSingle(this@Artist)).show(ctx)
        }
    }

}

fun Artist.loadPalette(vararg views: View)
    = loadPalette(id, views)