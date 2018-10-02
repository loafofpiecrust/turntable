package com.loafofpiecrust.turntable.model.song

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.v7.graphics.Palette
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.DetailsFragment
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.playlist.PlaylistPickerDialog
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.GlobalScope
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.launch
import kotlinx.coroutines.experimental.withContext
import java.util.*
import java.util.concurrent.TimeUnit


@Parcelize
data class Song(
    val id: SongId,
    val track: Int,
    val disc: Int,
    val duration: Int, // milliseconds
    val year: Int?,
    @Transient
    val platformId: PlatformId? = null
): SaveableMusic, Parcelable {
    override val displayName get() = id.displayName

    /**
     * Comparable/sortable value for disc and track
     * Assumes that a disc will never have over 999 tracks.
     * Example: Disc 2, Track 27 => 2027
     */
    val discTrack: Int get() = disc * 1000 + track


    override fun optionsMenu(ctx: Context, menu: Menu) = menu.run {
        menuItem(R.string.add_to_playlist).onClick {
            PlaylistPickerDialog.forItem(this@Song).show(ctx)
        }

//        menuItem("Go to album").onClick(BG_POOL) {
//            val album = Library.instance.findAlbumOfSong(this@Song).first()
//            withContext(UI) {
//                if (album != null) {
//                    ctx.replaceMainContent(
//                        DetailsFragment(album.id), true
//                    )
//                }
//            }
//        }
//        menuItem("Go to artist").onClick {
//            val artist = if (local is Song.LocalDetails.Downloaded) {
//                Library.instance.findArtist(id.artist).first()
//            } else {
//                SearchApi.find(id.artist) ?: run {
//                    ctx.toast("No remote artist for '${this@Song.id}'")
//                    return@onClick
//                }
//            }
//
//            if (artist != null) {
//                ctx.replaceMainContent(
//                    ArtistDetailsFragment.fromArtist(artist), true
//                )
//            }
//        }

//        menuItem("Recommend").onClick {
//            FriendPickerDialogStarter.newInstance(
//                SyncService.Message.Recommendation(this@Song.id),
//                "Send Recommendation"
//            ).show(ctx)
//        }
//
//        menuItem("Clear Streams").onClick {
//            OnlineSearchService.instance.reloadSongStreams(this@Song.id)
//            OnlineSearchService.instance.reloadAlbumStreams(this@Song.id.album)
//        }

    }

    suspend fun loadMedia(): Media? {
        val existing = Library.instance.sourceForSong(id)
        if (existing != null) {
            return Media(existing)
        }

        val internetStatus = App.instance.internetStatus.first()
        if (internetStatus == App.InternetStatus.OFFLINE) {
            return null
        }

        val mode = UserPrefs.HQStreamingMode.valueOf(UserPrefs.hqStreamingMode.value)
        val hqStreaming = when (mode) {
            UserPrefs.HQStreamingMode.ONLY_UNMETERED -> internetStatus == App.InternetStatus.UNLIMITED
            UserPrefs.HQStreamingMode.ALWAYS -> true
            UserPrefs.HQStreamingMode.NEVER -> false
        }

        // This is a remote song, find the stream id
        // Well, we might have a song that's pretty much the same. Check first.
        // (Last line of defense against minor typos/discrepancies/album versions)
        val res = OnlineSearchService.instance.getSongStreams(this)
        return if (res.status is OnlineSearchService.StreamStatus.Available) {
            val url = if (hqStreaming) {
                res.status.hqStream ?: res.status.stream
            } else res.status.stream
            Media(url, res.start, res.end)
        } else null
    }

    fun loadCover(req: RequestManager, cb: (Palette?, Palette.Swatch?) -> Unit = { a, b -> }): ReceiveChannel<RequestBuilder<Drawable>?> = run {
        Library.instance.findAlbumExtras(id.album).map {
            //if (it != null) {
                // This album cover is either local or cached.
//                GlobalScope.produceSingle {
                    req.load(it?.artworkUri)
                        .listener(loadPalette(id, cb))
//                }
            /*} else when {
                // The song happens to know its artwork url.
                artworkUrl != null -> produceTask {
                    req.load(artworkUrl)
                        .apply(Library.ARTWORK_OPTIONS)
                        .listener(loadPalette(info.id, cb))
                }
                else -> produceTask { null }
            }*/
        }
    }

    data class Media(val url: String, val start: Int = 0, val end: Int = 0)
    interface PlatformId: Parcelable

    companion object {
        val MIN_DURATION = TimeUnit.SECONDS.toMillis(5)
        val MAX_DURATION = TimeUnit.MINUTES.toMillis(60)
    }
}

@Parcelize
data class LocalSongId(
    val id: Long,
    val albumId: Long,
    val artistId: Long
): Song.PlatformId, Parcelable

@Parcelize
data class RemoteSongId(
    val id: String?,
    val albumId: String?,
    val artistId: String?
//        val normalStream: String? = null, // Usually 128 kb/s
//        val hqStream: String? = null, // Usually 160 kb/s
//        val start: Int = -1 // Position to start the stream from
): Song.PlatformId, Parcelable