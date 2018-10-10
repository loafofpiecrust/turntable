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
import com.loafofpiecrust.turntable.browse.Repository
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.model.song.HasTracks
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.PlaylistPicker
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.sync.Message
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.showDialog
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.textColor
import java.io.Serializable

@Parcelize
data class PartialAlbum(
    override val id: AlbumId,
    override val year: Int?,
    override val type: Album.Type
): Album, SavableMusic, Parcelable {
    override val musicId: MusicId get() = id

    @Transient
    private val resolved = GlobalScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
        Repository.find(id)
    }
    suspend fun resolve(): Album? = resolved.await()

    override val tracks: List<Song>
        get() = runBlocking { resolve()?.tracks } ?: emptyList()
}

interface Album: Music, HasTracks {
    val id: AlbumId
    override val musicId: MusicId
        get() = id
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

    fun toPartial() = PartialAlbum(id, year, type)


    fun loadCover(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.instance.loadAlbumCover(req, id)
            send(localArt.receive() ?: req.load(Repository.fullArtwork(this@Album, true)))

            localArt.filterNotNull().redirectTo(channel)
        }

    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.instance.loadAlbumCover(req, id).map {
            it?.apply(RequestOptions().signature(ObjectKey("${id}thumbnail")))
        }


    interface RemoteDetails: Serializable, Parcelable {
        val thumbnailUrl: String?
        val artworkUrl: String?

        suspend fun resolveTracks(album: AlbumId): List<Song>

        /// Priority of this entry, on a rough scale of [0, 100]
        /// where 100 is the best match possible.
    }
}

val Album.hasTrackGaps: Boolean get() =
    tracks.lazy.zipWithNext().any { (lastSong, song) ->
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
