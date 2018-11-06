package com.loafofpiecrust.turntable.model.album

import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.design.widget.CollapsingToolbarLayout
import android.support.v7.graphics.Palette
import android.support.v7.widget.CardView
import android.support.v7.widget.Toolbar
import android.view.View
import android.widget.TextView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.signature.ObjectKey
import com.github.florent37.glidepalette.GlidePalette
import com.loafofpiecrust.turntable.model.Music
import com.loafofpiecrust.turntable.model.MusicId
import com.loafofpiecrust.turntable.model.song.HasTracks
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.repository.Repositories
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.util.lazy
import com.loafofpiecrust.turntable.util.sendFrom
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.filterNotNull
import kotlinx.coroutines.channels.map
import kotlinx.coroutines.channels.produce
import org.jetbrains.anko.backgroundColor
import org.jetbrains.anko.colorAttr
import org.jetbrains.anko.textColor
import java.io.Serializable


/**
 * Album from any source made up of a list of tracks
 */
interface Album: Music, HasTracks {
    override val id: AlbumId

    /**
     * Year this album was originally published.
     *
     * Any value <= 0 is considered invalid, meaning we don't know what year this was published.
     */
    val year: Int

    /**
     * The order of [Type] variants determines the order of album types in a discography.
     */
    enum class Type {
        /// A full album, or LP
        LP,
        /// A shorter release, or EP
        EP,
        /// A single, maybe with a B-side or some remixes
        SINGLE,
        /// A recording of a live show
        LIVE,
        /// A collection or compilation that wasn't an original release
        COMPILATION,
        /// Something else altogether
        OTHER
    }

    /**
     * The type of this album, often determined by naming and length.
     *
     * An album's type determines how sets of albums are grouped
     * and how tracks on the album are searched for.
     */
    val type: Type


    fun loadCover(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        GlobalScope.produce {
            val localArt = Library.loadAlbumCover(req, this@Album.id)
            send(localArt.receive() ?: req.load(Repositories.fullArtwork(this@Album, true)))

            sendFrom(localArt.filterNotNull())
        }

    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.loadAlbumCover(req, this.id).map {
            it?.apply(RequestOptions().signature(ObjectKey("${this.id}thumbnail")))
        }


    interface RemoteDetails: Serializable, Parcelable {
        val thumbnailUrl: String?
        val artworkUrl: String?

        suspend fun resolveTracks(album: AlbumId): List<Song>
    }
}

/// Whether this album has any track discontinuities
/// For example, if we have Track 1 then Track 3 but no Track 2, then there's a gap.
val Album.hasTrackGaps: Boolean get() =
    tracks.lazy.zipWithNext().any { (lastSong, song) ->
        song.disc == lastSong.disc && (song.track - lastSong.track > 1)
    }

fun Album.loadPalette(vararg views: View)
    = loadPalette(this.id, views)

fun loadPalette(id: MusicId, views: Array<out View>) =
    loadPalette(id) { palette, swatch ->
        val color = if (palette == null || swatch == null) {
            val textColor = views[0].context.colorAttr(android.R.attr.textColor)
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

fun loadPalette(id: MusicId, cb: (Palette?, Palette.Swatch?) -> Unit) =
    GlidePalette.with(id.displayName).intoCallBack { palette ->
        if (palette == null) {
            cb(null, null)
        } else {
            val vibrant = palette.vibrantSwatch
            val vibrantDark = palette.darkVibrantSwatch
            val muted = palette.mutedSwatch
            val mutedDark = palette.darkMutedSwatch
            val dominant = palette.dominantSwatch
            cb(palette, vibrant ?: muted ?: vibrantDark ?: mutedDark ?: dominant)
        }
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
