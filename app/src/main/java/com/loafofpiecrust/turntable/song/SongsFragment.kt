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
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.album.Album
import com.loafofpiecrust.turntable.album.AlbumId
import com.loafofpiecrust.turntable.artist.ArtistId
import com.loafofpiecrust.turntable.fastScrollRecycler
import com.loafofpiecrust.turntable.menuItem
import com.loafofpiecrust.turntable.onClick
import com.loafofpiecrust.turntable.player.MusicService
import com.loafofpiecrust.turntable.playlist.CollaborativePlaylist
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.service.SyncService
import com.loafofpiecrust.turntable.service.library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.BG_POOL
import com.loafofpiecrust.turntable.util.replayOne
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Deferred
import kotlinx.coroutines.experimental.channels.BroadcastChannel
import kotlinx.coroutines.experimental.channels.ReceiveChannel
import kotlinx.coroutines.experimental.channels.first
import kotlinx.coroutines.experimental.channels.map
import kotlinx.coroutines.experimental.runBlocking
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.ctx
import java.util.*

open class SongsFragment: BaseFragment() {
    /// TODO: Separate display type from category!
    sealed class Category: Parcelable {
        @Parcelize class All: Category()
        @Parcelize data class History(val limit: Int? = null): Category()
        @Parcelize data class ByArtist(val artist: ArtistId): Category()
        @Parcelize data class OnAlbum(val album: AlbumId, val isPartial: Boolean = false): Category()
//        @Parcelize data class Custom(val songs: List<Song>): Category()
        @Parcelize data class Playlist(val id: UUID, val sideIdx: Int = 0): Category()
    }

    @Arg lateinit var category: Category

    private var retrieveTask: Deferred<List<Song>>? = null

    lateinit var songs: BroadcastChannel<List<Song>>

    companion object {
        fun all(): SongsFragment {
            return SongsFragmentStarter.newInstance(Category.All()).apply {
                category = Category.All()
            }
        }
        fun onAlbum(id: AlbumId, album: ReceiveChannel<Album>): SongsFragment {
            return SongsFragmentStarter.newInstance(Category.OnAlbum(id)).apply {
                songs = album.map(BG_POOL) { it.tracks }.replayOne()
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
            if (category is Category.All) {
                songs = Library.instance.songs
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) {
        menu.menuItem(R.string.show_history).onClick {
            ctx.replaceMainContent(
                SongsFragmentStarter.newInstance(
                    Category.History(null)
                )
            )
        }
    }

    override fun ViewManager.createView(): View = with(this) {
        val cat = category
        val adapter = SongsAdapter(cat) { songs, idx ->
            MusicService.enact(SyncService.Message.PlaySongs(songs, idx))
        }

        val recycler = if (cat is Category.All) {
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
                helper.attachToRecyclerView(this)
            }

            addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                setDrawable(ctx.getDrawable(R.drawable.song_divider))
            })


            adapter.subscribeData(songs.openSubscription())
        }
    }
}