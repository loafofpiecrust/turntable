package com.loafofpiecrust.turntable.song

//import com.loafofpiecrust.turntable.service.MusicService2
import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.DividerItemDecoration
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
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
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.sync.PlayerAction
import com.loafofpiecrust.turntable.ui.BaseFragment
import com.loafofpiecrust.turntable.ui.UIComponent
import com.loafofpiecrust.turntable.ui.replaceMainContent
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.refreshableRecyclerView
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import org.jetbrains.anko.AnkoContext
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
//        fun onPlaylist(uuid: UUID, playlist: Playlist, sideIdx: Int): SongsFragment {
//            return SongsFragmentStarter.newInstance(Category.Playlist(uuid, sideIdx)).apply {
//                songs = playlist.tracks
//            }
//        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!::songs.isInitialized) {
            val cat = category
            when (cat) {
                is Category.All -> songs = Library.instance.songsMap
                    .openSubscription()
                    .map { it.values.sortedBy { it.id } }
                    .replayOne()
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
    startRefreshing: Boolean = true,
    block: RecyclerView.() -> Unit = {}
) = swipeRefreshLayout {
    val scope = ViewScope(this)

    isEnabled = false

    scope.launch {
        songs.consume {
            if (isEmpty) {
                if (startRefreshing) {
                    isRefreshing = true
                }
                for (e in this) {
                    isRefreshing = false
                }
            }
        }
    }


//    val adapter = SongsAdapter(songs.openSubscription(), category) { songs, idx ->
//        MusicService.offer(PlayerAction.PlaySongs(songs, idx))
//    }
    val adapter = SongsOnDiscAdapter(
        songs.openSubscription().map {
            it.groupBy { "Disc ${it.disc}" }
        }
    ) { song ->
        val songs = songs.openSubscription().first()
        val idx = songs.indexOf(song)
        MusicService.offer(PlayerAction.PlaySongs(songs, idx))
    }

    val recycler = if (category is SongsFragment.Category.All) {
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

        addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
            setDrawable(context.getDrawable(R.drawable.song_divider))
        })


        block.invoke(this)
    }
}


sealed class SongsUI: UIComponent() {
    protected open val useFastScroll get() = false
    abstract val songs: BroadcastChannel<List<Song>>

    override fun Menu.prepareOptions(context: Context) {
        menuItem(R.string.show_history).onClick {
            context.replaceMainContent(
                SongsFragment(SongsFragment.Category.History(null))
            )
        }
    }
    override fun CoroutineScope.render(ui: AnkoContext<Any>) = ui.refreshableRecyclerView {
        channel = songs.openSubscription()

        val adapter = SongsAdapter(songs.openSubscription()) { songs, idx ->
            MusicService.offer(PlayerAction.PlaySongs(songs, idx))
        }

        contentView {
            if (useFastScroll) {
                fastScrollRecycler {
                    turntableStyle()
                }
            } else {
                recyclerView {
                    turntableStyle()
                }
            }.apply {
                clipToPadding = false

                val linear = LinearLayoutManager(context)
                layoutManager = linear
                this.adapter = adapter

                addItemDecoration(DividerItemDecoration(context, linear.orientation).apply {
                    setDrawable(context.getDrawable(R.drawable.song_divider))
                })
            }
        }
    }

    @Parcelize
    class All: SongsUI(), Parcelable {
        override val useFastScroll get() = true
        override val songs: BroadcastChannel<List<Song>> =
            Library.instance.songsMap.openSubscription().map {
                it.values.sortedBy { it.id }
            }.broadcast(CONFLATED)
    }

    class Custom(
        override val songs: BroadcastChannel<List<Song>>
    ): SongsUI()
}