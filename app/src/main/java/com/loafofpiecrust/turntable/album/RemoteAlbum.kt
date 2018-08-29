package com.loafofpiecrust.turntable.album

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.util.ALT_BG_POOL
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.consumeEach
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.toast


class RemoteAlbum(
    override val id: AlbumId,
    val remoteId: Album.RemoteDetails, // Discogs, Spotify, or MusicBrainz ID
    override val type: Album.Type = Album.Type.LP,
    override val year: Int? = null
): Album {
    override val tracks: List<Song> by lazy {
        runBlocking {
            // grab tracks from online
            remoteId.resolveTracks(id)
        }
    }

    override fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> {
        return remoteId.thumbnailUrl?.let {
            produce(BG_POOL) { send(req.load(it)) }
        }?.map { it.apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(id))) }
            ?: Library.instance.loadAlbumCover(req, id)
    }

    override fun optionsMenu(ctx: Context, menu: Menu) {
        super.optionsMenu(ctx, menu)

        menu.menuItem(ctx.getString(R.string.download), R.drawable.ic_cloud_download, showIcon=false).onClick(ALT_BG_POOL) {
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

        menu.menuItem(ctx.getString(R.string.add_to_library), R.drawable.ic_turned_in_not, showIcon = true) {
            ctx.library.findAlbum(id).consumeEach(UI) { existing ->
                if (existing != null) {
                    icon = ctx.getDrawable(R.drawable.ic_turned_in)
                    onClick {
                        // Remove remote album from library
                        ctx.library.removeRemoteAlbum(existing)
                        ctx.toast("Removed album to library")
                    }
                } else {
                    icon = ctx.getDrawable(R.drawable.ic_turned_in_not)
                    onClick {
                        ctx.library.addRemoteAlbum(this@RemoteAlbum)
                        ctx.toast("Added album to library")
                    }
                }
            }
        }
    }
}
