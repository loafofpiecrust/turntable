package com.loafofpiecrust.turntable.model.album

//import com.loafofpiecrust.turntable.model.PaperParcelAlbum
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.design.widget.CollapsingToolbarLayout
import android.support.v7.graphics.Palette
import android.support.v7.widget.CardView
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.View
import android.widget.TextView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.github.florent37.glidepalette.GlidePalette
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.model.song.Music
import com.loafofpiecrust.turntable.model.song.MusicId
import com.loafofpiecrust.turntable.model.song.SaveableMusic
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.util.getColorCompat
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.PlaylistPickerDialog
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.util.task
import com.loafofpiecrust.turntable.util.then
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import java.io.Serializable

@Parcelize
class PartialAlbum(
    override val id: AlbumId,
    override val year: Int?,
    override val type: Album.Type
): Album, SaveableMusic, Parcelable {
    @IgnoredOnParcel
    @delegate:Transient
    private val remoteAlbum: Album? by lazy {
        runBlocking { SearchApi.find(id) }
    }

    override val tracks: List<Song>
        get() = remoteAlbum?.tracks ?: emptyList()
}

interface Album: Music {
    val id: AlbumId
    val year: Int?

    enum class Type {
        LP, // A full album, or LP
        EP, // A shorter release, or EP
        SINGLE, // A single, maybe with a B-side or some remixes nowadays
        LIVE,
        COMPILATION, // A collection or compilation that wasn't an original release
        OTHER // Something else altogether
    }
    val type: Type
    val tracks: List<Song>

    override val displayName get() = id.displayName

    fun toPartial() = PartialAlbum(id, year, type)

    /// TODO: Can we pull this out of the class...?
    override fun optionsMenu(ctx: Context, menu: Menu) {
        menu.menuItem(R.string.album_shuffle, R.drawable.ic_shuffle, showIcon =false).onClick {
            task {
                Library.instance.songsOnAlbum(id).first()
            }.then { tracks ->
                tracks?.let {
                    if (it.isNotEmpty()) {
                        MusicService.enact(SyncService.Message.PlaySongs(it, mode = MusicPlayer.OrderMode.SHUFFLE))
                    }
                }
            }
        }

        menu.menuItem(R.string.recommend).onClick {
            FriendPickerDialog(
                SyncService.Message.Recommendation(toPartial()),
                "Send Recommendation"
            ).show(ctx)
        }

        menu.menuItem(R.string.add_to_playlist).onClick {
            PlaylistPickerDialog.forItem(toPartial()).show(ctx)
        }
    }


    fun loadCover(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?>
        = Library.instance.loadAlbumCover(req, id).map {
            (it ?: SearchApi.fullArtwork(this, true)?.let {
                req.load(it)
//                    .thumbnail(req.load(remote?.thumbnailUrl).apply(Library.ARTWORK_OPTIONS))
            })?.apply(
                RequestOptions().signature(ObjectKey("${id}full"))
            )
        }

    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.instance.loadAlbumCover(req, id)


    interface RemoteDetails: Serializable, Parcelable {
        val thumbnailUrl: String?
        val artworkUrl: String?

        suspend fun resolveTracks(album: AlbumId): List<Song>

        /// Priority of this entry, on a rough scale of [0, 100]
        /// where 100 is the best match possible.
    }
}

val Album.hasTrackGaps: Boolean get() =
    tracks.asSequence().zipWithNext().any { (lastSong, song) ->
        (song.track - lastSong.track > 1)
    }

fun Album.loadPalette(vararg views: View)
    = loadPalette(id, views)

fun loadPalette(id: MusicId, views: Array<out View>) =
    loadPalette(id) { palette, swatch ->
        val color = if (palette == null || swatch == null) {
            val textColor = views[0].context.getColorCompat(R.color.text)
            views.forEach {
                when (it) {
                    is Toolbar -> it.setTitleTextColor(textColor)
                    is TextView -> it.textColor = textColor
                }
            }
//            view.resources.getColor(R.color.primary)
            UserPrefs.primaryColor.value
        } else {
            views.forEach {
                if (it is Toolbar) {
                    it.setTitleTextColor(swatch.titleTextColor)
                    it.setSubtitleTextColor(swatch.titleTextColor)
                } else if (it is TextView) {
                    it.textColor = swatch.titleTextColor
                }
            }
            swatch.rgb
        }
        views.forEach {
            when (it) {
                is CardView -> it.setCardBackgroundColor(color)
                is CollapsingToolbarLayout -> it.setContentScrimColor(color)
                !is TextView -> it.backgroundColor = color
            }
        }
    }

fun loadPalette(id: MusicId, cb: (Palette?, Palette.Swatch?) -> Unit) = GlidePalette.with(id.displayName)
    .intoCallBack { palette ->
        if (palette == null) {
            cb(null, null)
            return@intoCallBack
        }
        val vibrant = palette.vibrantSwatch
        val vibrantDark = palette.darkVibrantSwatch
        val muted = palette.mutedSwatch
        val mutedDark = palette.darkMutedSwatch
        val dominant = palette.dominantSwatch
        cb(palette, vibrant ?: muted ?: vibrantDark ?: mutedDark ?: dominant)
//        val darkThreshold = 0.6f
//        cb(palette, if (vibrant != null && vibrant.hsl[2] <= darkThreshold) {
//            vibrant
//        } else if (vibrantDark != null && vibrantDark.hsl[2] <= darkThreshold) {
//            vibrantDark
//        } else if (muted != null && muted.hsl[2] <= darkThreshold) {
//            muted
//        } else if (dominant != null && dominant.hsl[2] <= darkThreshold) {
//            dominant
//        } else {
//            mutedDark
//        })
    }!!
