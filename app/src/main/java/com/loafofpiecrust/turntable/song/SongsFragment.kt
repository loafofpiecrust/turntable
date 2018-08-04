package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import activitystarter.Arg
import android.os.Parcelable
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.artist.Artist
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.playlist.MixTape
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.MainActivity
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.channels.consumeEach
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.channels.produce
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx
import java.util.*
import kotlin.coroutines.experimental.coroutineContext

//@EFragment
open class SongsFragment: BaseFragment() {
    sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize data class History(val limit: Int? = null): Category()
        @Parcelize data class ByArtist(val artist: Artist): Category()
        @Parcelize data class OnAlbum(val album: Album, val isPartial: Boolean = false): Category()
        @Parcelize data class Custom(val songs: List<Song>): Category()
        @Parcelize data class Playlist(val id: UUID, val sideIdx: Int = 0): Category()
    }

    @Arg lateinit var category: Category

    private var retrieveTask: Deferred<List<Song>>? = null

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.menuItem("History").onClick {
            MainActivity.replaceContent(
                SongsFragmentStarter.newInstance(
                    Category.Custom(UserPrefs.history.value.asReversed().map { it.song })
                )
            )
        }
    }

    override fun makeView(ui: ViewManager): View = with(ui) {
        val cat = category
        val adapter = SongsAdapter(cat) { songs, idx ->
            MusicService.enact(SyncService.Message.PlaySongs(songs, idx))
        }

        val recycler = if (cat is Category.All) {
            fastScrollRecycler {
                turntableStyle(jobs)
            }
        } else {
            recyclerView {
                turntableStyle(jobs)
            }
        }

        recycler.apply {
            clipToPadding = false

            val linear = LinearLayoutManager(context)
            layoutManager = linear
            this.adapter = adapter
            if (cat is Category.Playlist) {
                val playlist = runBlocking { ctx.library.findPlaylist(cat.id).first() }
                val helper = ItemTouchHelper(object: ItemTouchHelper.SimpleCallback(
                    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
                    ItemTouchHelper.START or ItemTouchHelper.END
                ) {
                    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
//                                    ctx.music.shiftQueueItem(viewHolder.adapterPosition, target.adapterPosition)
//                                adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
                        when (playlist) {
                            is CollaborativePlaylist -> playlist.move(
                                viewHolder.adapterPosition,
                                target.adapterPosition
                            )
                        }
                        return true
                    }

                    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
//                                adapter.onItemDismiss(viewHolder.adapterPosition)
//                                    ctx.music.removeFromQueue(viewHolder.adapterPosition)
                        when (playlist) {
                            is CollaborativePlaylist -> playlist.remove(
                                viewHolder.adapterPosition
                            )
                        }
                    }

                    override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder?)
                        = 0.2f

                    override fun isLongPressDragEnabled() = true
                    override fun isItemViewSwipeEnabled() = true
                })
                helper.attachToRecyclerView(this@apply)
            }


            addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                setDrawable(resources.getDrawable(R.drawable.song_divider))
            })

            println("songs cat: $cat")
            task(BG_POOL + jobs) {
                when (cat) {
                    is Category.All -> Library.instance.songs.openSubscription()
                    is Category.History -> UserPrefs.history.openSubscription().map {
                        val rev = it.asReversed()
                        if (cat.limit != null) {
                            rev.take(cat.limit)
                        } else {
                            rev
                        }.map { it.song }
                    }
                    is Category.ByArtist -> Library.instance.songsByArtist(cat.artist.id)
                    is Category.OnAlbum -> {
                        Library.instance.findAlbum(cat.album).combineLatest(
//                                App.instance.internetStatus,
                            Library.instance.findCachedRemoteAlbum(cat.album)
                        ).switchMap { (local/*, internet*/, cached) ->
                            if (local != null) {
                                val localTracks = local.tracks
                                if (!local.hasTrackGaps /*|| internet == App.InternetStatus.OFFLINE*/) {
                                    produceTask { localTracks }
                                } else {
                                    if (cached != null) {
                                        produceTask { localTracks + cached.tracks }
                                    } else {
                                        produce(BG_POOL) {
                                            send(localTracks)
                                            send(localTracks + cat.album.resolveTracks())
                                        }
                                    }.map {
                                        it.sortedBy { it.disc * 1000 + it.track }.dedupMerge(
                                            { a, b -> a.disc == b.disc && (a.id == b.id ||
                                                (a.track == b.track)) /*&& FuzzySearch.ratio(a.id.name.toLowerCase(), b.id.name.toLowerCase()) > 90)*/
                                            },
                                            { a, b -> if (a.local != null) a else b }
                                        )
                                    }
                                }
                            } else if (cached != null) {
                                produceTask { cached.tracks }
                            } else /*if (internet != App.InternetStatus.OFFLINE)*/ {
                                produceTask { tryOr(listOf()) { cat.album.resolveTracks() } }
                            } /*else {
                                // TODO: Add message above list: 'No Internet Connection'
                                task(UI) { toast("No Internet Connection") }
                                produce {  }
                            }*/
//                            else {
//                                val obs = BehaviorSubject.create<List<Song>>()
//                                if (existing != null) {
//                                    obs.onNext(existing.tracks)
//                                }
//                                task {
//                                    val res = JsonParser().parse(get("http://ws.audioscrobbler.com/2.0", params = mapOf(
//                                        "api_key" to SearchFragment.LASTFM_KEY, "format" to "json",
//                                        "method" to "album.getinfo",
//                                        "album" to cat.album.name,
//                                        "artist" to cat.album.artist
//                                    )).text).obj
//
//                                    if (res.has("album")) {
//                                        val album = res["album"].obj
//                                        val albumMbid = if (res.has("id")) res["id"].string else null
//                                        val tracks = album["tracks"]["track"].array
//                                        val cover = if (res.has("image")) album["image"][4]["#text"].string else null
//                                        obs.onNext((0 until tracks.size()).map { tracks[it].obj }.map {
//                                            Song(
//                                                null,
//                                                Song.RemoteDetails(
//                                                    null, albumMbid, null
//                                                ),
//                                                it["id"].string,
//                                                cat.album.name,
//                                                cat.album.artist,
//                                                track = it["@attr"]["rank"].string.toInt(),
//                                                disc = 1,
//                                                duration = it["duration"].string.toInt() * 1000,
//                                                year = null,
//                                                artworkUrl = cover
//                                            )
//                                        })
//
//                                        if (local != null) {
//                                            Library.instance.cacheRemoteAlbum(local.copy(tracks = obs.value))
//                                        }
//                                    }
//
//                                    obs.onComplete()
//                                }
//
//                                obs
//                            }
                        }
                    }
                    is Category.Custom -> produceTask { cat.songs }
//                is SongsFragment.Category.Playlist -> Library.instance.findPlaylist(cat.name)!!.tracks
                    is Category.Playlist -> {
                        given(ctx.library.findPlaylist(cat.id).first() ?: ctx.library.findCachedPlaylist(cat.id).first()) { pl ->
                            if (pl is MixTape) {
                                pl.tracksOnSide(cat.sideIdx)
                            } else {
                                pl.tracks
                            }
                        } ?: produce(coroutineContext) {}
                    }
                }.consumeEach {
                    adapter.updateData(it)
                }
            }
        }
    }
}