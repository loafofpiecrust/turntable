package com.loafofpiecrust.turntable.album

//import com.loafofpiecrust.turntable.model.PaperParcelAlbum
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.support.v7.graphics.Palette
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.View
import android.widget.TextView
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.ObjectKey
import com.github.florent37.glidepalette.GlidePalette
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.browse.Discogs
import com.loafofpiecrust.turntable.browse.MusicBrainz
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.player.MusicPlayer
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.PlaylistPickerDialogStarter
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.song.*
import com.loafofpiecrust.turntable.sync.FriendPickerDialog
import com.loafofpiecrust.turntable.ui.AlbumEditorActivityStarter
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.ALT_BG_POOL
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.success
import com.loafofpiecrust.turntable.util.task
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.produce
import org.jetbrains.anko.*
import java.io.Serializable
import java.util.*
import java.util.regex.Pattern
import kotlin.coroutines.experimental.coroutineContext


// Remote never has: id, artistId
// Local could have: mbid


@Parcelize
data class AlbumId(
    override val name: String,
    val artist: ArtistId
): MusicId, Parcelable {
    private constructor(): this("", ArtistId(""))

    companion object {
        private val TYPE_SUFFIX_PAT by lazy {
            Pattern.compile("\\b\\s*[-]?\\s*[(\\[]?(EP|Single|LP)[)\\]]?$", Pattern.CASE_INSENSITIVE)
        }
        private val EDITION_SUFFIX_PAT by lazy {
            Pattern.compile(
                "\\s+([(\\[][\\w\\s]*(Edition|Version|Deluxe|Release|Reissue|Mono|Stereo|Extended)[\\w\\s]*[)\\]])|(\\w+\\s+(Edition|Version|Release)$)",
                Pattern.CASE_INSENSITIVE
            )
        }
        val SIMPLE_EDITION_PAT by lazy {
            Regex("\\b(Deluxe|Expansion)\\b", RegexOption.IGNORE_CASE)
        }
        private val DISC_SUFFIX_PAT by lazy {
            Pattern.compile(
                "\\s*[(\\[]?\\s*(Disc|Disk|CD)\\s*(\\d+)\\s*[)\\]]?$",
                Pattern.CASE_INSENSITIVE
            )
        }
    }

    override fun toString() = "$displayName | $artist"
    override fun equals(other: Any?) = given(other as? AlbumId) { other ->
        this.displayName.equals(other.displayName, true)
            && this.artist == other.artist
    } ?: false
    override fun hashCode() = Objects.hash(displayName.toLowerCase(), artist)

    fun forSong(song: String) = SongId(song, this)

    val sortTitle: String get() = displayName.withoutArticle()
    val dbKey: String get() = "$displayName~${artist.sortName}".toLowerCase()

    val selfTitledAlbum: Boolean get() = sortTitle.equals(artist.sortName, true)

    @delegate:Transient
    val discNumber: Int by lazy {
        val discM = DISC_SUFFIX_PAT.matcher(name)
        if (discM.find()) {
            discM.group(2).toIntOrNull() ?: 1
        } else 1
    }

    /// Cut out versions and types at the end for a CLEAN id
    /// Examples:
    /// Whoa - Single => Whoa
    /// Whoa - Fine & Single => Whoa - Fine & Single
    /// I'm Still Single => I'm Still Single
    /// What's Going On (Deluxe Edition) => What's Going On
    /// Whatever (Maxi Edition) - EP => Whatever
    /// What We... (Deluxe Version) => What We...
    /// It's a Deluxe Edition => It's a Deluxe Edition
    @delegate:Transient
    override val displayName: String by lazy {
        // First, remove " - $type" suffix
        var name = this.name.trim()
        val typeM = TYPE_SUFFIX_PAT.matcher(name)
        name = if (typeM.find()) {
            typeM.replaceFirst("")
        } else name
        // Then, remove "(Deluxe Edition)" suffix
        val editionM = EDITION_SUFFIX_PAT.matcher(name)
        name = if (editionM.find()) {
            editionM.replaceFirst("")
        } else name
        // Finally, remove "(Disc 1)" suffix and set the disc # for all tracks.
        val discM = DISC_SUFFIX_PAT.matcher(name)
        name = if (discM.find()) {
            discM.replaceFirst("")
        } else name

        val res = if (name.isNotEmpty()) {
            name
        } else {
            this.artist.name
        }.replace("“", "\"").replace("”", "\"").replace("‘", "\'").replace("’", "\'")

        res
    }
}

//@PaperParcel
@Parcelize
data class Album(
    val local: LocalDetails?,
    val remote: RemoteDetails?,
    val id: AlbumId,
    var tracks: List<Song> = listOf(),
    var year: Int? = null,
    var type: Type = Type.LP
) : Music {
    /// For serialization libraries
    private constructor(): this(null, null, AlbumId("", ArtistId("")))

    enum class Type {
        LP, // A full album, or LP
        EP, // A shorter release, or EP
        SINGLE, // A single, maybe with a B-side or some remixes nowadays
        LIVE,
        COMPILATION, // A collection or compilation that wasn't an original release
        OTHER // Something else altogether
    }

    abstract class RemoteDetails(
        open val thumbnailUrl: String? = null,
        open val artworkUrl: String? = null
    ): Serializable, Parcelable {
        abstract suspend fun resolveTracks(album: AlbumId): List<Song>

        /// Priority of this entry, on a rough scale of [0, 100]
        /// where 100 is the best match possible.
    }



    sealed class LocalDetails: Serializable {
        data class Downloaded(
            val id: Long,
            val artistId: Long
        ): LocalDetails()
        data class Downloading(
            val download: AlbumDownload
        ): LocalDetails()
    }

    companion object: AnkoLogger by AnkoLogger<Album>() {
        fun justForSearch(id: AlbumId, tracks: List<Song> = listOf()) = Album(
            null, null,
            id,
            tracks,
            null
        )

        suspend fun search(titleQuery: String): List<Album> {
            val mb = task(coroutineContext) {
                MusicBrainz.searchAlbums(titleQuery)
            }
            val discogs = task(coroutineContext) {
                Discogs.searchAlbums(titleQuery)
            }

            return (mb.await() + discogs.await()).dedupMerge(
                { a, b -> a.id == b.id },
                { a, b -> b }
            )
        }
    }

    override val simpleName: String get() = id.displayName

    fun minimize(sync: Boolean = false): Album = if (sync) {
        if (local == null) this else copy(local = null)
    } else {
        if (local == null && remote == null) {
            this
        } else copy(local = null, remote = null)
    }

    val hasTrackGaps: Boolean get() {
        var lastTrack = 0
        return tracks.any { song ->
            (song.track - lastTrack > 1).also {
                lastTrack = song.track
            }
        }
    }


    suspend fun resolveTracks(): List<Song> {
        val cached = Library.instance.findCachedRemoteAlbum(this@Album).first()
        val remote = remote ?: SearchApi.find(this)
        return cached?.tracks ?: given(remote?.resolveTracks(id)) { tracks ->
            tracks.forEach {
                it.artworkUrl = it.artworkUrl
                    ?: Library.instance.findAlbumExtras(id).first()?.artworkUri
            }
            Library.instance.cacheRemoteAlbum(this@Album.copy(tracks = tracks))
            tracks
        } ?: listOf()
    }

    fun loadCover(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?>
        = Library.instance.loadAlbumCover(req, id).map {
            (
                it ?: given(SearchApi.fullArtwork(this, true)) {
                    req.load(it)
//                    .thumbnail(req.load(remote?.thumbnailUrl).apply(Library.ARTWORK_OPTIONS))
                } ?: given(remote?.thumbnailUrl) { req.load(it) }
            )?.apply(Library.ARTWORK_OPTIONS
                .signature(ObjectKey("${id}full"))
            )
        }

    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> = run {
        given (remote?.thumbnailUrl) {
            produce(BG_POOL) { send(req.load(it)) }
        }?.map { it.apply(Library.ARTWORK_OPTIONS.signature(ObjectKey(id))) }
            ?: Library.instance.loadAlbumCover(req, id)
    }

    fun loadPalette(view: View, titleView: List<TextView>? = null)
        = loadPalette(id, view, titleView)


    override fun optionsMenu(menu: Menu) {
        val ctx = MainActivity.latest.ctx

        menu.menuItem("Shuffle", R.drawable.ic_shuffle, showIcon=false).onClick {
            task {
                Library.instance.songsOnAlbum(this).first()
            }.success { tracks ->
                given(tracks) {
                    if (it.isNotEmpty()) {
                        MusicService.enact(SyncService.Message.PlaySongs(it, mode = MusicPlayer.OrderMode.SHUFFLE))
                    }
                }
            }
        }

        if (remote != null || hasTrackGaps) {
            menu.menuItem("Download", R.drawable.ic_cloud_download, showIcon=false).onClick(ALT_BG_POOL) {
                if (App.instance.hasInternet) {
                    given(ctx.library.findCachedAlbum(this@Album).first()?.tracks) { tracks ->
                        tracks.filter {
                            ctx.library.findSong(it.id).first()?.local == null
                        }.forEach { it.download() }
                    }
                } else {
                    ctx.toast("No internet connection")
                }
            }
        }

        if (local != null) {
            menu.menuItem("Edit Tags").onClick {
                AlbumEditorActivityStarter.start(ctx, this)
            }
        }

        menu.menuItem("Recommend").onClick {
            FriendPickerDialog().apply {
                onAccept = {
                    SyncService.send(
                        SyncService.Message.Recommendation(this@Album.minimize(true)),
                        SyncService.Mode.OneOnOne(it)
                    )
                }
            }.show()
        }

        menu.menuItem("Add to Collection").onClick {
            PlaylistPickerDialogStarter.newInstance(this@Album).show()
        }
    }
}

fun loadPalette(id: MusicId, view: View, titles: List<TextView>? = null) =
    loadPalette(id) { palette, swatch ->
        view.backgroundColor = if (swatch == null) {
            val textColor = view.resources.getColor(R.color.text)
            if (view is Toolbar) {
                view.setTitleTextColor(textColor)
            } else titles?.forEach {
                it.textColor = textColor
            }
//            view.resources.getColor(R.color.primary)
            UserPrefs.primaryColor.value
        } else {
            if (view is Toolbar) {
                println("library: putting palette into toolbar")
                view.setTitleTextColor(swatch.titleTextColor)
                view.setSubtitleTextColor(swatch.titleTextColor)
            }
            titles?.forEach {
                it.textColor = swatch.titleTextColor
            }
            swatch.rgb
        }
    }

fun loadPalette(id: MusicId, cb: (Palette?, Palette.Swatch?) -> Unit) = GlidePalette.with(id.displayName)
//    .use(BitmapPalette.Profile.VIBRANT)
    .intoCallBack { palette ->
        if (palette == null) {
            cb(null, null)
            return@intoCallBack
        }
//        val darkThreshold = 0.6f
        val vibrant = palette.vibrantSwatch
        val vibrantDark = palette.darkVibrantSwatch
        val muted = palette.mutedSwatch
        val mutedDark = palette.darkMutedSwatch
        val dominant = palette.dominantSwatch
        cb(palette, vibrant ?: muted ?: vibrantDark ?: mutedDark ?: dominant)
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
