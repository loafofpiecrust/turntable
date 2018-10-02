package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import android.os.Parcelable
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.support.v7.widget.helper.ItemTouchHelper
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewManager
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.song.Song
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.model.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.sync.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.swipeRefreshLayout
import java.util.*

open class SongsFragment(): BaseFragment() {
    constructor(category: Category): this() {
        this.category = category
    }
    constructor(category: Category, channel: BroadcastChannel<List<Song>>): this(category) {
        this.songs = channel
    }

    /// TODO: Separate display type from category!
    sealed class Category: Parcelable {
        @Parcelize object All : Category()
        @Parcelize data class History(val limit: Int? = null): Category()
        @Parcelize data class ByArtist(val artist: ArtistId): Category()
        @Parcelize data class OnAlbum(val album: AlbumId, val isPartial: Boolean = false): Category()
//        @Parcelize data class Custom(val songs: List<Song>): Category()
        @Parcelize data class Playlist(val id: UUID, val sideIdx: Int = 0): Category()
    }

    private var category: Category by arg()

    private var retrieveTask: Deferred<List<Song>>? = null

    lateinit var songs: BroadcastChannel<List<Song>>

    companion object {
        fun all(): SongsFragment {
            return SongsFragment(Category.All).apply {
                category = Category.All
            }
        }
        fun onAlbum(id: AlbumId, album: ReceiveChannel<Album>): SongsFragment {
            return SongsFragment(Category.OnAlbum(id)).apply {
                songs = album.map(Dispatchers.IO) { it.tracks }.replayOne()
            }
        }
//        fun onPlaylist(id: UUID, playlist: Playlist, sideIdx: Int): SongsFragment {
//            return SongsFragmentStarter.newInstance(Category.Playlist(id, sideIdx)).apply {
//                songs = playlist.tracks
//            }
//        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!::songs.isInitialized) {
            val cat = category
            when (cat) {
                is Category.All -> songs = Library.instance.songs
//                is Category.OnAlbum -> songs =
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.menuItem(R.string.show_history).onClick {
            context?.replaceMainContent(
                SongsFragment(Category.History(null))
            )
        }
    }

    override fun ViewManager.createView(): View = songsList(category, songs)
}


fun ViewManager.songsList(
    category: SongsFragment.Category,
    songs: BroadcastChannel<List<Song>>,
    block: RecyclerView.() -> Unit = {}
) = swipeRefreshLayout {
    val scope = ViewScope(this)

    isEnabled = false
    scope.launch {
        songs.consume {
            if (isEmpty) {
                isRefreshing = true
                receive()
                isRefreshing = false
            }
        }
    }

    val cat = category
    val adapter = SongsAdapter(cat) { songs, idx ->
        MusicService.enact(SyncService.Message.PlaySongs(songs, idx))
    }

    val recycler = if (cat is SongsFragment.Category.All) {
        fastScrollRecycler {
            turntableStyle()
        }
    } else {
        recyclerView {
            turntableStyle()
        }
    }

    recycler.apply {
        clipToPadding = false

        val linear = LinearLayoutManager(context)
        layoutManager = linear
        this.adapter = adapter
        if (cat is SongsFragment.Category.Playlist) {
            val playlist = runBlocking { context.library.findPlaylist(cat.id).first() }
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

                override fun getMoveThreshold(viewHolder: RecyclerView.ViewHolder)
                    = 0.2f

                override fun isLongPressDragEnabled() = true
                override fun isItemViewSwipeEnabled() = true
            })
            helper.attachToRecyclerView(this)
        }

        addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
            setDrawable(context.getDrawable(R.drawable.song_divider))
        })


        adapter.subscribeData(songs.openSubscription())
        block.invoke(this)
    }
}