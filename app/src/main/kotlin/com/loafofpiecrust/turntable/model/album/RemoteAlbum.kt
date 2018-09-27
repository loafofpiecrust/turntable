package com.loafofpiecrust.turntable.model.album

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.browse.Spotify
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.toast


@Parcelize
class RemoteAlbum(
    override val id: AlbumId,
    val remoteId: Album.RemoteDetails, // Discogs, Spotify, or MusicBrainz ID
    override val type: Album.Type = Album.Type.LP,
    override val year: Int? = null
): Album, Parcelable {
    private constructor(): this(AlbumId("", ArtistId("")), Spotify.AlbumDetails(""))

    @IgnoredOnParcel
    @delegate:Transient
    override val tracks: List<Song> by lazy {
        runBlocking {
            // grab tracks from online
            remoteId.resolveTracks(id)
        }
    }

    override fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> {
        return remoteId.thumbnailUrl?.let {
            produce(BG_POOL) { send(req.load(it)) }
        }?.map { it.apply(RequestOptions().signature(ObjectKey(id))) }
            ?: Library.instance.loadAlbumCover(req, id)
    }

    override fun optionsMenu(ctx: Context, menu: Menu) {
        super.optionsMenu(ctx, menu)

        menu.menuItem(R.string.download, R.drawable.ic_cloud_download, showIcon = false).onClick(ALT_BG_POOL) {
            if (App.instance.hasInternet) {
                ctx.library.findCachedAlbum(id).first()?.tracks?.let { tracks ->
//                    tracks.filter {
//                        ctx.library.findSong(it.id).first()?.local == null
//                    }.forEach { it.download() }
                }
            } else {
                ctx.toast(R.string.no_internet)
            }
        }

        menu.menuItem(R.string.add_to_library, R.drawable.ic_turned_in_not, showIcon = true) {
            ctx.library.findAlbum(id).consumeEach(UI) { existing ->
                if (existing != null) {
                    icon = ctx.getDrawable(R.drawable.ic_turned_in)
                    onClick {
                        // Remove remote album from library
                        ctx.library.removeRemoteAlbum(existing)
                        ctx.toast(R.string.album_removed_library)
                    }
                } else {
                    icon = ctx.getDrawable(R.drawable.ic_turned_in_not)
                    onClick {
                        ctx.library.addRemoteAlbum(this@RemoteAlbum)
                        ctx.toast(R.string.album_added_library)
                    }
                }
            }
        }
    }
}