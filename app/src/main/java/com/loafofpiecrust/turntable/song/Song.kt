package com.loafofpiecrust.turntable.song

//import com.devbrackets.android.playlistcore.manager.IPlaylistItem
//import com.devbrackets.android.playlistcore.manager.ListPlaylistManager
//import com.loafofpiecrust.turntable.service.PlaylistManager
import android.app.DownloadManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.support.v7.graphics.Palette
import android.view.Menu
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.loafofpiecrust.turntable.App
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.album.DetailsFragmentStarter
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.artist.ArtistDetailsFragmentStarter
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.playlist.PlaylistPickerDialogStarter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.OnlineSearchService
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceTask
import com.loafofpiecrust.turntable.util.switchMap
import com.loafofpiecrust.turntable.util.task
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.withContext
import me.xdrop.fuzzywuzzy.FuzzySearch
import org.jetbrains.anko.ctx
import org.jetbrains.anko.downloadManager
import org.jetbrains.anko.toast
import java.io.Serializable
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

// All possible music status':
// - Local: has an id, can be played. May be partial album (if album)
// - Not local, not downloading yet (queued in sync / DL'd from search)
// - Not local, downloading from torrent (queued in sync / DL'd from search)
// - Not local, downloading from youtube (queued in sync / DL'd from search)
// - Not local, downloading from p2p/udp (queued in sync)
// - Not local, downloading from larger collection torrent (song from album, song/album from artist)

interface Music: Parcelable {
    val simpleName: String
    fun optionsMenu(menu: Menu)
}


// All possible statuses of a song:
// Remote:
// - Unknown: no details confirmed
// - Partial: metadata confirmed, no stream urls
// - Resolved: confirmed metadata and stream urls

fun String.withoutArticle(): String = when {
    startsWith("the ", true) -> drop(4)
    startsWith("a ", true) -> drop(2)
    else -> this
}


interface MusicId {
    val name: String
    val displayName: String

    /// Provides unique transition names
    /// for all instances of any type of MusicId.
    fun transitionFor(elem: Any): String
        = elem.toString() + javaClass.simpleName + displayName.toLowerCase()
    val nameTransition get() = transitionFor("name")
    val imageTransition get() = transitionFor("art")
}

@Parcelize
data class SongId(
    override val name: String,
    val album: AlbumId,
    val artist: String = album.artist.displayName,
    var features: List<ArtistId> = listOf()
): MusicId, Parcelable {
    private constructor(): this("", "", "")
    constructor(title: String, album: String, artist: String, songArtist: String = artist):
        this(title, AlbumId(album, ArtistId(artist)), songArtist)

    companion object {
        val FEATURE_PAT by lazy {
            Pattern.compile("\\(?\\b(ft|feat|featuring|features)\\.?\\s+(([^,&)]+,?\\s*&?\\s*)*)\\)?$", Pattern.CASE_INSENSITIVE)!!
        }
    }

    override fun toString() = "$displayName | $album"
    override fun equals(other: Any?) = given(other as? SongId) { other ->
        this.displayName.equals(other.displayName, true)
            && this.album == other.album
            && this.artist.equals(other.artist, true)
    } ?: false
    fun fuzzyEquals(other: SongId)
        = FuzzySearch.ratio(name, other.name) >= 88
        && FuzzySearch.ratio(album.name, other.album.name) >= 88
        && FuzzySearch.ratio(album.artist.name, other.album.artist.name) >= 88

    override fun hashCode() = Objects.hash(
        displayName.toLowerCase(),
        album,
        artist.toLowerCase()
    )

    val dbKey: String get() = "$name~${album.sortTitle}~$artist".toLowerCase()
    val filePath: String get() = "${album.artist.name.toFileName()}/${album.displayName.toFileName()}/${name.toFileName()}"


    @delegate:Transient
    override val displayName by lazy {
        // remove features!
        val m = FEATURE_PAT.matcher(name)
        if (m.find()) {
            val res = m.replaceFirst("").trim()
            features = m.group(2).split(",").mapNotNull {
                val s = it.trim().removeSuffix("&").removeSuffix(",").trimEnd()
                if (s.isNotEmpty()) {
                    ArtistId(s)
                } else null
            }
            res
        } else name
    }
}


data class HistoryEntry(
    val song: Song,
    val timestamp: Long = System.currentTimeMillis()
)

//@Parcel(Parcel.Serialization.BEAN)
@Parcelize
data class Song(
    var local: LocalDetails?,
    val remote: RemoteDetails?,
//    val name: String,
//    var album: String,
//    val artist: String,
    val id: SongId,
    val track: Int,
    var disc: Int,
    val duration: Int, // milliseconds
    val year: Int? = null,
    var artworkUrl: String? = null
) : Music {
    override fun optionsMenu(menu: Menu) = with(menu) {
        val ctx = MainActivity.latest.ctx

        menuItem("Add to playlist").onClick {
            PlaylistPickerDialogStarter.newInstance(this@Song).show()
        }

        menuItem("Go to album").onClick(BG_POOL) {
            val album = Library.instance.findAlbumOfSong(this@Song).first()
            withContext(UI) {
                if (album != null) {
                    MainActivity.replaceContent(
                        DetailsFragmentStarter.newInstance(album), true
                    )
                }
            }
            // We need to provide for songs having different artist than albums
//                    val cat = if (song.id.album != 0L) {
//                        AlbumActivity.Category.Local(song.id.album)
//                    } else {
//                        val album = Album.findOnline(song.album, song.artist, song.year)
//                        if (album != null) {
//                            AlbumActivity.Category.Remote(album)
//                        } else {
//                            v.context.toast("No remote album for '${song.name}'")
//                            return@onClick
//                        }
//                    }
//                    AlbumActivityStarter.start(v.context, cat)
        }
        menuItem("Go to artist").onClick {
            val artist = if (local is Song.LocalDetails.Downloaded) {
                Library.instance.findArtist(ArtistId(id.artist)).first()
            } else {
                val search = Artist.justForSearch(id.artist)
                search.copy(remote = SearchApi.find(search) ?: run {
                    ctx.toast("No remote artist for '${this@Song.id}'")
                    return@onClick
                })
            }

            MainActivity.replaceContent(
                ArtistDetailsFragmentStarter.newInstance(artist), true
            )
        }

        menuItem("Recommend").onClick {
            FriendPickerDialog().apply {
                onAccept = {
                    SyncService.send(
                        SyncService.Message.Recommendation(minimizeForSync()),
                        SyncService.Mode.OneOnOne(it)
                    )
                }
            }.show(MainActivity.latest.supportFragmentManager, "friends")
        }

        menuItem("Clear Streams").onClick {
            OnlineSearchService.instance.reloadSongStreams(this@Song.id)
            OnlineSearchService.instance.reloadAlbumStreams(this@Song.id.album)
        }
    }

    /// For serialization libraries
    constructor(): this(
        null, null,
        SongId("", "", ""), 1, 1, 0
    )

    companion object {
        val MIN_DURATION = TimeUnit.SECONDS.toMillis(5)
        val MAX_DURATION = TimeUnit.MINUTES.toMillis(60)

        fun justForSearch(title: String, album: String, artist: String, duration: Int = 0) = Song(
            null, null,
            SongId(title, AlbumId(album, ArtistId(artist))),
            0, 0, duration
        )
    }

    data class RemoteDetails(
        val id: String?,
        val albumId: String?,
        val artistId: String?,
        val normalStream: String? = null, // Usually 128 kb/s
        val hqStream: String? = null, // Usually 160 kb/s
        val start: Int = -1 // Position to start the stream from
    ): Serializable

    sealed class LocalDetails: Serializable {
        data class Downloaded(
            val path: String,
            val id: Long,
            val albumId: Long,
            val artistId: Long
        ): LocalDetails()

        data class Downloading(
            val id: Long
        ): LocalDetails()
    }

    data class Media(val url: String, val start: Int = 0, val end: Int = 0)


    override val simpleName: String get() = id.displayName
    val searchKey get() = (id.name + id.album.sortTitle + id.album.artist.sortName).toLowerCase()

    private val hasData get() = (local is LocalDetails.Downloaded || remote != null)

//    val id: Int get() = searchKey.hashCode()

    val localMedia: Media? get() = run {
        val local = local
        if (local is LocalDetails.Downloaded) {
            Media(local.path)
        } else null
    }

    suspend fun remoteMedia(): Media? {
        val local = local
        return if (local !is Song.LocalDetails.Downloaded) {
            val existing = Library.instance.findSongFuzzy(this@Song).first()

            val internetStatus = App.instance.internetStatus.first()
            if (internetStatus == App.InternetStatus.OFFLINE && existing?.local == null) {
                return null
            }

            val mode = UserPrefs.HQStreamingMode.valueOf(UserPrefs.hqStreamingMode.value)
            val hqStreaming = when (mode) {
                UserPrefs.HQStreamingMode.ONLY_UNMETERED -> internetStatus == App.InternetStatus.UNLIMITED
                UserPrefs.HQStreamingMode.ALWAYS -> true
                UserPrefs.HQStreamingMode.NEVER -> false
            }
            if (remote?.normalStream != null) {
                val stream = if (hqStreaming) {
                    remote.hqStream ?: remote.normalStream
                } else remote.normalStream
                Media(stream, remote.start, remote.start + this.duration)
            } else {
                // This is a remote song, find the stream id
                // Well, we might have a song that's pretty much the same. Check first.
                // (Last line of defense against minor typos/discrepancies/album versions)
                if (existing != null && existing != this@Song && existing.hasData) {
                    val local = existing.local
                    when {
                        local is Song.LocalDetails.Downloaded -> {
                            Media(local.path)
                        }
                        existing.remote != null -> {
                            val stream = if (hqStreaming) {
                                existing.remote.hqStream ?: existing.remote.normalStream
                            } else existing.remote.normalStream
                            given(stream) {
                                Media(it, existing.remote.start, existing.remote.start + existing.duration)
                            }
                        }
                        else -> return null // unreachable last case given existing.hasData == true
                    }
                } else {
                    val (track, yt) = OnlineSearchService.instance.getSongStreams(this@Song)
                    if (yt is OnlineSearchService.StreamStatus.Available) {
                        val stream = if (hqStreaming) {
                            yt.hqStream ?: yt.stream
                        } else yt.stream
                        given(stream) { url ->
                            val start = track.remote?.start ?: 0
                            Media(url, start, start + track.duration)
                        }
                    } else null
                }
            }
        } else Media(local.path)
    }

    suspend fun download() {
        try {
            given(OnlineSearchService.instance.getSongStreams(this)) { (song, streams) ->
                if (streams !is OnlineSearchService.StreamStatus.Available) {
                    return@given null
                }
                // lq is .m4a, hq is .webm
                val (downloadUrl, ext) = streams.stream to "m4a"

                val req = DownloadManager.Request(Uri.parse(downloadUrl))
                req.setTitle("${song.id.name} | ${song.id.artist}")

//                                    req.allowScanningByMediaScanner()

                req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                req.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_MUSIC,
                    "${song.id.filePath}.$ext"
                )

                val id = App.instance.downloadManager.enqueue(req)
                this.local = LocalDetails.Downloading(id)

                OnlineSearchService.instance.addDownload(
                    OnlineSearchService.SongDownload(song, 0.0, id)
                )
            }
        } catch (e: Exception) {
            task(UI) { e.printStackTrace() }
        }
    }

    fun loadCover(req: RequestManager, cb: (Palette?, Palette.Swatch?) -> Unit = { a, b -> }): ReceiveChannel<RequestBuilder<Drawable>?> = run {
//        val album = Album.justForSearch(id.album)
//        album.loadCover(req).map {
//            it?.listener(loadPalette(id.album, cb))
//        }
        Library.instance.findAlbumExtras(id.album).switchMap {
            if (it != null) {
                // This album cover is either local or cached.
                produceTask {
                    req.load(it.artworkUri)
                        .apply(Library.ARTWORK_OPTIONS)
                        .listener(loadPalette(id, cb))
                }
            } else {
                when {
                    // The song happens to know its artwork url.
                    artworkUrl != null -> produceTask {
                        req.load(artworkUrl)
                            .apply(Library.ARTWORK_OPTIONS)
                            .listener(loadPalette(id, cb))
                    }

//                    remote != null -> {
//                        // TODO: Find the album cover from just the name.
//                        val prefix = "http://coverartarchive.org/release-group/${remote.albumId}"
//                        req.load(if (remote.albumId != null) "$prefix/front-500" else null)
//                            .apply(Library.ARTWORK_OPTIONS)
//                            .thumbnail(req.load("$prefix/front-250"))
//                    }
                    else -> Album.justForSearch(id.album).loadCover(req).map {
                        it?.listener(loadPalette(id.album, cb))
                    }
//                    else -> {
//                        if (SearchApi.find(Album.justForSearch(id.album))?.artworkUrl)
//                    }
                }
            }
        }
    }

    fun minimize(): Song = this.copy(local = null, remote = null, artworkUrl = null)
    fun minimizeForSync(): Song = this.copy(local = null, artworkUrl = null)
}

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
