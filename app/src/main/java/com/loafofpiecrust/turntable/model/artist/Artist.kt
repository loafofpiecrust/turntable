package com.loafofpiecrust.turntable.model.artist

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.view.Menu
import android.view.View
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.artist.BiographyFragment
import com.loafofpiecrust.turntable.artist.RelatedArtistsFragment
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.browse.Repository
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.queue.RadioQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.toast

@Parcelize
data class PartialArtist(
    val id: ArtistId
): SavableMusic, Parcelable {
    override val displayName: String
        get() = id.displayName

    suspend fun resolve(): Artist? = Repository.find(id)
    override fun optionsMenu(context: Context, menu: Menu) {}
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

    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.instance.loadArtistImage(req, id).map {
            it?.apply(RequestOptions().signature(ObjectKey("${id}thumbnail")))
        }

    fun loadArtwork(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.instance.loadArtistImage(req, id)
            send(localArt.receive() ?: req.load(Repository.fullArtwork(this@Artist, true)))

            localArt.filterNotNull().redirectTo(channel)
        }


    override fun optionsMenu(context: Context, menu: Menu) = with(menu) {
        menuItem(R.string.artist_show_similar).onClick {
            context.replaceMainContent(
                RelatedArtistsFragment(id),
                true
            )
        }

        menuItem(R.string.recommend).onClick {
            FriendPickerDialog(
                Message.Recommendation(toPartial()),
                context.getString(R.string.recommend)
            ).show(context)
        }

        // TODO: Sync with radios...
        // TODO: Sync with any type of queue!
        menuItem(R.string.radio_start).onClick(Dispatchers.Default) {
            MusicService.enact(PlayerAction.Pause(), false)

            val radio = RadioQueue.fromSeed(listOf(this@Artist))
            if (radio != null) {
//                MusicService.enact(SyncService.Message.ReplaceQueue(radio))
                MusicService.enact(PlayerAction.Play())
            } else {
                context.toast(context.getString(R.string.radio_no_data, id.displayName))
            }
        }

        menuItem(R.string.artist_biography).onClick(Dispatchers.Default) {
            BiographyFragment.fromChan(produceSingle(this@Artist)).show(context)
        }
    }

}

fun Artist.loadPalette(vararg views: View)
    = loadPalette(id, views)