package com.loafofpiecrust.turntable.artist

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Parcelable
import android.view.Menu
import android.view.View
import com.bumptech.glide.RequestBuilder
import com.bumptech.glide.RequestManager
import com.bumptech.glide.signature.ObjectKey
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.album.loadPalette
import com.loafofpiecrust.turntable.browse.SearchApi
import com.loafofpiecrust.turntable.given
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.radio.RadioQueue
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.song.Music
import com.loafofpiecrust.turntable.song.MusicId
import com.loafofpiecrust.turntable.song.SongId
import com.loafofpiecrust.turntable.song.withoutArticle
import com.loafofpiecrust.turntable.sync.FriendPickerDialogStarter
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.produceSingle
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.map
import org.jetbrains.anko.toast
import java.util.*


@Parcelize
data class ArtistId(
    override val name: String,
    val altName: String? = null,
    var features: List<ArtistId> = listOf()
): MusicId, Parcelable, Comparable<ArtistId> {
    override fun compareTo(other: ArtistId): Int
        = Library.ARTIST_COMPARATOR.compare(this, other)

    private constructor(): this("")

    override fun toString() = displayName
    override fun equals(other: Any?) = given(other as? ArtistId) { other ->
        this.sortName.equals(other.sortName, true)
    } ?: false
    override fun hashCode() = Objects.hash(
        sortName.toLowerCase()//,
//        altName?.toLowerCase(),
//        features
    )

    @delegate:Transient
    override val displayName: String by lazy {
        val feat = SongId.FEATURE_PAT.matcher(name)
        if (feat.find()) {
            val res = feat.replaceFirst("").trim()
            features = feat.group(2).split(',', '&').mapNotNull {
                val s = it.trim()
                    .removeSuffix("&")
                    .removeSuffix(",")
                    .trimEnd()
                if (s.isNotEmpty()) {
                    ArtistId(s)
                } else null
            }
            res
        } else name
    }

    fun forAlbum(album: String) = AlbumId(album, this)
    val sortName: String get() = displayName.withoutArticle()

    val featureList: String get() = if (features.isNotEmpty()) {
        " (ft. " + features.joinToString(", ") + ")"
    } else ""
}

//@Parcelize
//data class Artist(
//    val id: ArtistId,
//    val remote: RemoteDetails?,
//    val albums: List<Album>,
//    val artworkUrl: String? = null,
//    val disambiguation: String? = null,
//    val startYear: Int? = null,
//    val endYear: Int? = null
//) : Music {
//    data class Member(
//        val name: String,
//        val id: String,
//        val active: Boolean = true
//    )
//
//    /// For serialization libraries
//    constructor(): this(ArtistId(""), null, listOf())
//
//    interface RemoteDetails: Parcelable {
//        suspend fun resolveAlbums(): List<Album>
//        val description: String? get() = null
//    }
//
//    suspend fun resolveAlbums(includeLocals: Boolean = true): List<Album> {
//        // Find any local albums concurrently
//        val localAlbums = Library.instance.albumsByArtist(id).first()
//        // TODO: Have SearchApi.find(...) do caching so it's unified...
//        val cached = Library.instance.findCachedRemoteArtist(this@Artist).first()?.albums
//            ?: SearchApi.find(id)?.albums
//
//        return when {
//            cached != null -> if (includeLocals) {
//                (cached + localAlbums)
//            } else {
//                cached.filter { a ->
//                    localAlbums.find { b ->
//                        a.id.displayName.equals(b.id.displayName, true)
//                    } == null
//                }
//            }.dedupMerge(
//                { a, b -> a.id.displayName.equals(b.id.displayName, true) && a.type == b.type },
//                { a, b ->
//                    if (a.year != null && a.year!! > 0 && (b.year == null || b.year!! <= 0)) {
//                        // FIXME: Abstract album
////                        b.year = a.year
//                    }
//                    b
//                }
//            )
//            includeLocals -> localAlbums
//            else -> listOf()
//        }
//    }
//
//    companion object: AnkoLogger by AnkoLogger<Artist>() {
////        @JvmField val CREATOR = PaperParcelArtist.CREATOR
//
//        fun justForSearch(name: String) = Artist(
//            ArtistId(name),
//            null,
//            listOf(),
//            null
//        )
//
//
////        suspend fun findOnline(id: ArtistId): Artist? {
////            val res = Http.get("https://musicbrainz.org/ws/2/artist/", params = mapOf(
////                "fmt" to "json",
////                "query" to "artist:\"${id.displayName}\"",
////                "limit" to "2"
////            )).gson.obj
////
////            if (!res.has("artists")) return null // no dice
////
////            val artist = res["artists"][0].obj
////            val mbid = artist["id"].string
////            val name = artist["name"].string
////
////            return Artist(ArtistId(name), null, listOf(), null, mbid)
////        }
//
//        suspend fun search(nameQuery: String): List<Artist>
//            = SearchApi.searchArtists(nameQuery)
//    }
//
//    override val simpleName: String get() = id.displayName
//
//
//    fun loadArtwork(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
//        if (artworkUrl != null) {
//            produce(BG_POOL) { send(req.load(artworkUrl).apply(Library.ARTWORK_OPTIONS)) }
//        } else {
//            Library.instance.loadArtistImage(req, id)
//        }
//
//    fun loadPalette(vararg views: View)
//        = loadPalette(id, views)
//
//    fun minimize(): Artist = if (albums.isNotEmpty()) {
//        copy(albums = listOf())
//    } else this
//
//
//
//    override fun optionsMenu(menu: Menu) = with(menu) {
//        val ctx = MainActivity.latest.ctx
//        menuItem("Similar Artists", R.drawable.ic_people, showIcon=false).onClick {
//            ctx.replaceMainContent(
//                RelatedArtistsFragmentStarter.newInstance(this@Artist),
//                true
//            )
//        }
//
//        menuItem("Recommend", showIcon=false).onClick {
//            FriendPickerDialog().apply {
//                onAccept = {
//                    SyncService.send(
//                        SyncService.Message.Recommendation(minimize()),
//                        SyncService.Mode.OneOnOne(it)
//                    )
//                }
//            }.show()
//        }
//
//        // TODO: Sync with radios...
//        // TODO: Sync with any type of queue!
//        menuItem("Start Radio", showIcon=false).onClick(BG_POOL) {
//            MusicService.enact(SyncService.Message.Pause(), false)
//
//            val radio = RadioQueue.fromSeed(listOf(this@Artist))
//            if (radio != null) {
//                MusicService.enact(SyncService.Message.ReplaceQueue(radio))
//                MusicService.enact(SyncService.Message.Play())
//            } else {
//                ctx.toast("Not enough data on '${id.displayName}'")
//            }
//        }
//
//        menuItem("Biography", showIcon=false).onClick(BG_POOL) {
//            ctx.replaceMainContent(BiographyFragmentStarter.newInstance(this@Artist.minimize()))
//        }
//    }
//}


interface Artist: Music {
    val id: ArtistId
    val albums: List<Album>
    val startYear: Int?
    val endYear: Int?
    val biography: String?

    override val simpleName get() = id.displayName

    data class Member(
        val name: String,
        val id: String,
        val active: Boolean
    )
    // Each API implements whether they have any of this info already
    // or if it's all lazy or exists at all or what
    interface RemoteDetails: Parcelable {
        val albums: List<Album>
        val biography: String
//        val members: List<Artist.Member>
    }


    fun loadThumbnail(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> = loadArtwork(req)
    fun loadArtwork(req: RequestManager): ReceiveChannel<RequestBuilder<Drawable>?> =
        Library.instance.loadArtistImage(req, id).map {
            (it ?: given(SearchApi.fullArtwork(this, true)) {
                req.load(it)
            })?.apply(Library.ARTWORK_OPTIONS
                .signature(ObjectKey("${id}full"))
            )
        }

    fun loadPalette(vararg views: View)
        = loadPalette(id, views)


    override fun optionsMenu(ctx: Context, menu: Menu) = with(menu) {
        menuItem("Similar Artists", R.drawable.ic_people, showIcon=false).onClick {
            ctx.replaceMainContent(
                ArtistsFragment.relatedTo(id),
                true
            )
        }

        menuItem("Recommend", showIcon=false).onClick {
            FriendPickerDialogStarter.newInstance(
                SyncService.Message.Recommendation(this@Artist.id),
                "Send Recommendation"
            ).show(ctx)
        }

        // TODO: Sync with radios...
        // TODO: Sync with any type of queue!
        menuItem("Start Radio", showIcon=false).onClick(BG_POOL) {
            MusicService.enact(SyncService.Message.Pause(), false)

            val radio = RadioQueue.fromSeed(listOf(this@Artist))
            if (radio != null) {
//                MusicService.enact(SyncService.Message.ReplaceQueue(radio))
                MusicService.enact(SyncService.Message.Play())
            } else {
                ctx.toast("Not enough data on '${id.displayName}'")
            }
        }

        menuItem("Biography", showIcon=false).onClick(BG_POOL) {
            BiographyFragment.fromChan(produceSingle(this@Artist)).show(ctx)
        }
    }
}