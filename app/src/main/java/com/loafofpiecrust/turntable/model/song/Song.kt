package com.loafofpiecrust.turntable.model.song

//import com.devbrackets.android.playlistcore.manager.IPlaylistItem
//import com.devbrackets.android.playlistcore.manager.ListPlaylistManager
//import com.loafofpiecrust.turntable.service.PlaylistManager
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.v7.graphics.Palette
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.SavableMusic
import com.loafofpiecrust.turntable.model.album.loadPalette
import com.loafofpiecrust.turntable.playlist.PlaylistPickerDialog
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.util.menuItem
import com.loafofpiecrust.turntable.util.onClick
import com.loafofpiecrust.turntable.util.subSequenceView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import java.util.concurrent.TimeUnit

// All possible music status':
// - Local: has an id, can be played. May be partial album (if album)
// - Not local, not downloading yet (queued in sync / DL'd from search)
// - Not local, downloading from torrent (queued in sync / DL'd from search)
// - Not local, downloading from youtube (queued in sync / DL'd from search)
// - Not local, downloading from p2p/udp (queued in sync)
// - Not local, downloading from larger collection torrent (song from album, song/album from artist)


// All possible statuses of a song:
// Remote:
// - Unknown: no remoteInfo confirmed
// - Partial: metadata confirmed, no stream urls
// - Resolved: confirmed metadata and stream urls

fun CharSequence.withoutArticle(): CharSequence = when {
    startsWith("the ", true) -> subSequenceView(4)
    startsWith("a ", true) -> subSequenceView(2)
    else -> this
}


data class HistoryEntry(
    val song: Song,
    val timestamp: Long = System.currentTimeMillis()
)


@Parcelize
data class Song(
    val id: SongId,
    val track: Int,
    val disc: Int,
    val duration: Int, // milliseconds
    val year: Int?,
    @Transient
    val platformId: PlatformId? = null
): SavableMusic, Parcelable {
    override val displayName get() = id.displayName

    /**
     * Comparable/sortable value for disc and track
     * Assumes that a disc will never have over 999 tracks.
     * Example: Disc 2, Track 27 => 2027
     */
    val discTrack: Int get() = disc * 1000 + track


    override fun optionsMenu(context: Context, menu: Menu) = menu.run {
        menuItem(R.string.add_to_playlist).onClick {
            PlaylistPickerDialog.forItem(this@Song).show(context)
        }

//        menuItem("Go to album").onClick(BG_POOL) {
//            val album = Library.instance.findAlbumOfSong(this@Song).first()
//            withContext(UI) {
//                if (album != null) {
//                    context.replaceMainContent(
//                        DetailsFragment(album.id), true
//                    )
//                }
//            }
//        }
//        menuItem("Go to artist").onClick {
//            val artist = if (local is Song.LocalDetails.Downloaded) {
//                Library.instance.findArtist(id.artist).first()
//            } else {
//                Repository.find(id.artist) ?: run {
//                    context.toast("No remote artist for '${this@Song.id}'")
//                    return@onClick
//                }
//            }
//
//            if (artist != null) {
//                context.replaceMainContent(
//                    ArtistDetailsFragment.fromArtist(artist), true
//                )
//            }
//        }

//        menuItem("Recommend").onClick {
//            FriendPickerDialogStarter.newInstance(
//                SyncService.Message.Recommendation(this@Song.id),
//                "Send Recommendation"
//            ).show(context)
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


//@Parcelize
//data class Song(
//    var local: LocalDetails?,
//    val remote: RemoteDetails?,
////    val name: String,
////    var album: String,
////    val artist: String,
//    val id: SongId,
//    val track: Int,
//    var disc: Int,
//    val duration: Int, // milliseconds
//    val year: Int? = null,
//    var artworkUrl: String? = null
//) : Music, Parcelable {
//    /**
//     * Comparable/sortable value for disc and track
//     * Assumes that a disc will never have over 999 tracks.
//     * Example: Disc 2, Track 27 => 2027
//     */
//    val discTrack: Int get() = disc * 1000 + track
//
//    override fun optionsMenu(ctx: Context, popupMenu: Menu) = with(popupMenu) {
//        menuItem("Add to playlist").onClick {
//            PlaylistPickerDialog.forItem(this@Song).show(ctx)
//        }
//
//        menuItem("Go to album").onClick(BG_POOL) {
//            val album = Library.instance.findAlbumOfSong(this@Song).first()
//            withContext(UI) {
//                if (album != null) {
//                    ctx.replaceMainContent(
//                        DetailsFragmentStarter.newInstance(album.id), true
//                    )
//                }
//            }
//            // We need to provide for songs having different artist than albums
////                    val cat = if (song.id.album != 0L) {
////                        AlbumActivity.Category.Local(song.id.album)
////                    } else {
////                        val album = Album.findOnline(song.album, song.artist, song.year)
////                        if (album != null) {
////                            AlbumActivity.Category.Remote(album)
////                        } else {
////                            v.context.toast("No remote album for '${song.name}'")
////                            return@onClick
////                        }
////                    }
////                    AlbumActivityStarter.start(v.context, cat)
//        }
//        menuItem("Go to artist").onClick {
//            val artist = if (local is Song.LocalDetails.Downloaded) {
//                Library.instance.findArtist(id.artist).first()
//            } else {
//                Repository.find(id.artist) ?: run {
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
//
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
//    }
//
//    /// For serialization libraries
//    constructor(): this(
//        null, null,
//        SongId("", "", ""), 1, 1, 0
//    )
//
//    companion object {
//        val MIN_DURATION = TimeUnit.SECONDS.toMillis(5)
//        val MAX_DURATION = TimeUnit.MINUTES.toMillis(60)
//
//        fun justForSearch(title: String, album: String, artist: String, duration: Int = 0) = Song(
//            null, null,
//            SongId(title, AlbumId(album, ArtistId(artist))),
//            0, 0, duration
//        )
//        fun justForSearch(id: SongId, duration: Int = 0) = Song(
//            null, null, id,
//            0, 0, duration
//        )
//    }
//
//    data class RemoteDetails(
//        val id: String?,
//        val albumId: String?,
//        val artistId: String?,
//        val normalStream: String? = null, // Usually 128 kb/s
//        val hqStream: String? = null, // Usually 160 kb/s
//        val start: Int = -1 // Position to start the stream from
//    ): Serializable
//
//    sealed class LocalDetails: Serializable {
//        data class Downloaded(
//            val path: String,
//            val id: Long,
//            val albumId: Long,
//            val artistId: Long
//        ): LocalDetails()
//
//        data class Downloading(
//            val id: Long
//        ): LocalDetails()
//    }
//
//    data class Media(val url: String, val start: Int = 0, val end: Int = 0)
//
//
//    override val simpleName: String get() = id.displayName
//    val searchKey get() = (id.name + id.album.sortName + id.album.artist.sortName).toLowerCase()
//
//    private val hasData get() = (local is LocalDetails.Downloaded || remote != null)
//
////    val id: Int get() = searchKey.hashCode()
//
//    val localMedia: Media? get() = run {
//        val local = local
//        if (local is LocalDetails.Downloaded) {
//            Media(local.path)
//        } else null
//    }
//
//    suspend fun remoteMedia(): Media? {
//        val local = local
//        return if (local !is Song.LocalDetails.Downloaded) {
//            val existing = Library.instance.findSongFuzzy(this@Song).first()
//
//            val internetStatus = App.instance.internetStatus.first()
//            if (internetStatus == App.InternetStatus.OFFLINE && existing?.local == null) {
//                return null
//            }
//
//            val mode = UserPrefs.HQStreamingMode.valueOf(UserPrefs.hqStreamingMode.value)
//            val hqStreaming = when (mode) {
//                UserPrefs.HQStreamingMode.ONLY_UNMETERED -> internetStatus == App.InternetStatus.UNLIMITED
//                UserPrefs.HQStreamingMode.ALWAYS -> true
//                UserPrefs.HQStreamingMode.NEVER -> false
//            }
//            if (remote?.normalStream != null) {
//                val stream = if (hqStreaming) {
//                    remote.hqStream ?: remote.normalStream
//                } else remote.normalStream
//                Media(stream, remote.start, remote.start + this.duration)
//            } else {
//                // This is a remote song, find the stream id
//                // Well, we might have a song that's pretty much the same. Check first.
//                // (Last line of defense against minor typos/discrepancies/album versions)
//                if (existing != null && existing != this@Song && existing.hasData) {
//                    val local = existing.local
//                    when {
//                        local is Song.LocalDetails.Downloaded -> {
//                            Media(local.path)
//                        }
//                        existing.remote != null -> {
//                            val stream = if (hqStreaming) {
//                                existing.remote.hqStream ?: existing.remote.normalStream
//                            } else existing.remote.normalStream
//                            stream?.let {
//                                Media(it, existing.remote.start, existing.remote.start + existing.duration)
//                            }
//                        }
//                        else -> return null // unreachable last case given existing.hasData == true
//                    }
//                } else {
//                    val (track, yt) = OnlineSearchService.instance.getSongStreams(this@Song)
//                    if (yt is OnlineSearchService.StreamStatus.Available) {
//                        val stream = if (hqStreaming) {
//                            yt.hqStream ?: yt.stream
//                        } else yt.stream
//                        stream?.let { url ->
//                            val start = track.remote?.start ?: 0
//                            Media(url, start, start + track.duration)
//                        }
//                    } else null
//                }
//            }
//        } else Media(local.path)
//    }
//
//    suspend fun download() {
//        try {
//            given(OnlineSearchService.instance.getSongStreams(this)) { (song, streams) ->
//                if (streams !is OnlineSearchService.StreamStatus.Available) {
//                    return@given null
//                }
//                // lq is .m4a, hq is .webm
//                val (downloadUrl, ext) = streams.stream to "m4a"
//
//                val req = DownloadManager.Request(Uri.parse(downloadUrl))
//                req.setTitle("${song.id.name} | ${song.id.artist}")
//
////                                    req.allowScanningByMediaScanner()
//
//                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
//                req.setDestinationInExternalPublicDir(
//                    Environment.DIRECTORY_MUSIC,
//                    "${song.id.filePath}.$ext"
//                )
//
//                val id = App.instance.downloadManager.enqueue(req)
//                this.local = LocalDetails.Downloading(id)
//
//                OnlineSearchService.instance.addDownload(
//                    OnlineSearchService.SongDownload(song, 0.0, id)
//                )
//            }
//        } catch (e: Exception) {
//            task(UI) { e.printStackTrace() }
//        }
//    }
//
//    fun loadCover(req: RequestManager, cb: (Palette?, Palette.Swatch?) -> Unit = { a, b -> }): ReceiveChannel<RequestBuilder<Drawable>?> = run {
////        val album = Album.justForSearch(id.album)
////        album.loadCover(req).map {
////            it?.listener(loadPalette(id.album, cb))
////        }
//        Library.instance.findAlbumExtras(id.album).switchMap {
//            if (it != null) {
//                // This album cover is either local or cached.
//                produceTask {
//                    req.load(it.artworkUri)
//                        .apply(Library.ARTWORK_OPTIONS)
//                        .listener(loadPalette(id, cb))
//                }
//            } else {
//                when {
//                    // The song happens to know its artwork url.
//                    artworkUrl != null -> produceTask {
//                        req.load(artworkUrl)
//                            .apply(Library.ARTWORK_OPTIONS)
//                            .listener(loadPalette(id, cb))
//                    }
//
////                    remote != null -> {
////                        // TODO: Find the album cover from just the name.
////                        val prefix = "http://coverartarchive.org/release-group/${remote.albumId}"
////                        req.load(if (remote.albumId != null) "$prefix/front-500" else null)
////                            .apply(Library.ARTWORK_OPTIONS)
////                            .thumbnail(req.load("$prefix/front-250"))
////                    }
//                    else -> LocalAlbum(id.album, emptyList()).loadCover(req).map {
//                        it?.listener(loadPalette(id.album, cb))
//                    }
////                    else -> {
////                        if (Repository.find(Album.justForSearch(id.album))?.artworkUrl)
////                    }
//                }
//            }
//        }
//    }
//
//    fun minimize(): Song = this.copy(local = null, remote = null, artworkUrl = null)
//    fun minimizeForSync(): Song = this.copy(local = null, artworkUrl = null)
//}

fun String.toFileName(): String {
    val builder = StringBuilder(this.length)
    forEach { c ->
        val valid = !("|\\?*<\":>/".contains(c))

        if (valid) {
            builder.append(c)
        } else {
            builder.append(' ')
        }
    }
    return builder.toString()
}
