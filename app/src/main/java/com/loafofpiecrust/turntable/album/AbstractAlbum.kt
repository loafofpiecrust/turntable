package com.loafofpiecrust.turntable.album

import android.graphics.drawable.Drawable
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.Song
import com.loafofpiecrust.turntable.ui.AlbumEditorActivityStarter
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.ALT_BG_POOL
import com.loafofpiecrust.turntable.util.BG_POOL
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.ctx
import org.jetbrains.anko.toast

data class Genre(val name: String)

//interface Song {
//   val year: Int?
//}


@Parcelize
data class LocalAlbum(
    override val id: AlbumId,
    override val tracks: List<Song>
): Album {
    override fun mergeWith(other: Album): Album {
        return if (other is LocalAlbum) {
            LocalAlbum(
                id = other.id.copy(name = other.id.name.commonPrefixWith(id.name, true)),
                tracks = (tracks + other.tracks)
                    .sortedBy { it.disc * 1000 + it.track }
                    .dedupMergeSorted(
                        { a, b -> a.disc == b.disc && a.id.displayName.equals(b.id.displayName, true) },
                        { a, b -> if (a.local != null) a else b }
                    )
            )
        } else other.mergeWith(this)
    }

    override val year: Int?
        get() = tracks.find { it.year != null }?.year

    override val type by lazy {
        when {
            id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
            tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
            tracks.size <= 7 -> Album.Type.EP
            id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
            else -> Album.Type.LP
        }
    }

    override fun optionsMenu(menu: Menu) {
        super.optionsMenu(menu)

        val ctx = MainActivity.latest.ctx
        menu.menuItem("Edit Tags").onClick {
            AlbumEditorActivityStarter.start(ctx, this@LocalAlbum)
        }
    }
}

@Parcelize
open class RemoteAlbum(
    override val id: AlbumId,
    open val remoteId: Album.RemoteDetails, // Discogs, Spotify, or MusicBrainz ID
    override val type: Album.Type = Album.Type.LP,
    override val year: Int? = null
): Album {

    override val tracks: List<Song> by lazy {
        runBlocking {
//            val cached = Library.instance.findCachedRemoteAlbum(id).first()
//            cached?.tracks ?: given(remoteId.resolveTracks(id)) { tracks ->
//                tracks.forEach {
//                    it.artworkUrl = it.artworkUrl
//                        ?: Library.instance.findAlbumExtras(id).first()?.artworkUri
//                }
//                Library.instance.cacheRemoteAlbum(copy(tracks = tracks))
//                tracks
//            } ?: listOf()

            // grab tracks from online
            remoteId.resolveTracks(id)
        }
    }

    override fun mergeWith(other: Album): Album {
        return MergedAlbum(
            other.id.copy(name = other.id.name.commonPrefixWith(id.name, true)),
            remoteId,
            tracks = (tracks + other.tracks)
                .sortedBy { it.disc * 1000 + it.track }
                .dedupMergeSorted(
                    { a, b -> a.disc == b.disc && a.id.displayName.equals(b.id.displayName, true) },
                    { a, b -> if (a.local != null) a else b }
                ),
            type = when {
                other.id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
                other.tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
                other.tracks.size <= 7 -> Album.Type.EP
                other.id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
                else -> Album.Type.LP
            },
            year = year ?: other.year
        )
    }

    override fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> {
        return given (remoteId.thumbnailUrl) {
            produce(BG_POOL) { send(req.load(it)) }
        }?.map { it.apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(id))) }
            ?: Library.instance.loadAlbumCover(req, id)
    }

    override fun optionsMenu(menu: Menu) {
        super.optionsMenu(menu)

        val ctx = MainActivity.latest.ctx
        menu.menuItem("Download", R.drawable.ic_cloud_download, showIcon=false).onClick(ALT_BG_POOL) {
            if (App.instance.hasInternet) {
                given(ctx.library.findCachedAlbum(id).first()?.tracks) { tracks ->
                    tracks.filter {
                        ctx.library.findSong(it.id).first()?.local == null
                    }.forEach { it.download() }
                }
            } else {
                ctx.toast("No internet connection")
            }
        }
    }
}


@Parcelize
data class MergedAlbum(
    override val id: AlbumId,
    override val remoteId: Album.RemoteDetails,
    override val tracks: List<Song>,
    override val type: Album.Type,
    override val year: Int?
): RemoteAlbum(id, remoteId, type, year) {
    override fun mergeWith(other: Album): Album {
        return MergedAlbum(
            other.id.copy(name = other.id.name.commonPrefixWith(id.name, true)),
            remoteId,
            tracks = (tracks + other.tracks)
                .sortedBy { it.disc * 1000 + it.track }
                .dedupMergeSorted(
                    { a, b -> a.disc == b.disc && a.id.displayName.equals(b.id.displayName, true) },
                    { a, b -> if (a.local != null) a else b }
                ),
            type = when {
                other.id.name.contains(Regex("\\bEP\\b", RegexOption.IGNORE_CASE)) -> Album.Type.EP
                other.tracks.size <= 3 -> Album.Type.SINGLE // A-side, B-side, extra
                other.tracks.size <= 7 -> Album.Type.EP
                other.id.name.contains(Regex("\\b(Collection|Compilation|Best of|Greatest hits)\\b", RegexOption.IGNORE_CASE)) -> Album.Type.COMPILATION
                else -> Album.Type.LP
            },
            year = year ?: other.year
        )
    }

}

