package com.loafofpiecrust.turntable.model.artist

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.view.Menu
import android.view.View
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.artist.ArtistsFragment
import com.loafofpiecrust.turntable.artist.BiographyFragment
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.queue.RadioQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.model.song.Music
import com.loafofpiecrust.turntable.model.song.SaveableMusic
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceSingle
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.toast

@Parcelize
class PartialArtist(
    val id: ArtistId
): SaveableMusic, Parcelable {
    override val displayName: String
        get() = id.displayName

    suspend fun toFull(): Artist? = SearchApi.find(id)
    override fun optionsMenu(ctx: Context, menu: Menu) {}
}

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

    fun toPartial() = PartialArtist(id)

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
        menuItem(R.string.artist_show_similar).onClick {
            ctx.replaceMainContent(
                ArtistsFragment.relatedTo(id),
                true
            )
        }

        menuItem(R.string.recommend).onClick {
            FriendPickerDialog(
                SyncService.Message.Recommendation(toPartial()),
                ctx.getString(R.string.recommend)
            ).show(ctx)
        }

        // TODO: Sync with radios...
        // TODO: Sync with any type of queue!
        menuItem(R.string.radio_start).onClick(BG_POOL) {
            MusicService.enact(SyncService.Message.Pause(), false)

            val radio = RadioQueue.fromSeed(listOf(this@Artist))
            if (radio != null) {
//                MusicService.enact(SyncService.Message.ReplaceQueue(radio))
                MusicService.enact(SyncService.Message.Play())
            } else {
                ctx.toast(ctx.getString(R.string.radio_no_data, id.displayName))
            }
        }

        menuItem(R.string.artist_biography).onClick(BG_POOL) {
            BiographyFragment.fromChan(produceSingle(this@Artist)).show(ctx)
        }
    }

}

fun Artist.loadPalette(vararg views: View)
    = loadPalette(id, views)