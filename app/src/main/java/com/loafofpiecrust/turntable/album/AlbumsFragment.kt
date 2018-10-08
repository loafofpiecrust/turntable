package com.loafofpiecrust.turntable.album

//import org.musicbrainz.controller.Controller

import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.*
import com.loafofpiecrust.turntable.browse.LocalApi
import com.loafofpiecrust.turntable.browse.Repository
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.LocalArtist
import com.loafofpiecrust.turntable.model.artist.MergedArtist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.IO
import kotlinx.coroutines.experimental.android.UI
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.dimen
import org.jetbrains.anko.info
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.act
import org.jetbrains.anko.support.v4.swipeRefreshLayout

class AlbumsFragment : BaseFragment() {
    sealed class Category: Parcelable {
        abstract val channel: BroadcastChannel<List<Album>>

        @Parcelize object All : Category() {
            override val channel get() =
                Library.instance.albumsMap.openSubscription().map {
                    it.values.sortedBy { it.id }
                }.replayOne()
        }
        @Parcelize data class ByArtist(
            val artist: ArtistId,
            val mode: ArtistDetailsFragment.Mode
        ): Category() {
            @IgnoredOnParcel
            var artistChannel: ReceiveChannel<Artist>? = null

            override val channel get() =
                (artistChannel ?: Library.instance.findArtist(artist)
                    .map(Dispatchers.IO) {
                        if (it is LocalArtist) {
                            when (mode) {
                                ArtistDetailsFragment.Mode.LIBRARY -> it
                                ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE ->
                                    Repository.find(artist)?.let { remote -> MergedArtist(it, remote) } ?: it
                                ArtistDetailsFragment.Mode.REMOTE -> it // use case?
                            }
                        } else {
                            when (mode) {
                                ArtistDetailsFragment.Mode.LIBRARY -> it
                                ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE ->
                                    LocalApi.find(artist)?.let { local -> MergedArtist(it!!, local) } ?: it
                                ArtistDetailsFragment.Mode.REMOTE -> it // use case?
                            }
                        }
                    })
                    .map(Dispatchers.IO) { it?.albums ?: emptyList() }
                    .replayOne()
        }
    }

    enum class SortBy {
        NONE,
        YEAR,
        TITLE
    }

    // TODO: Customize parameters
    private var category: Category by arg()
    private var sortBy: SortBy by arg(SortBy.NONE)
    private var columnCount: Int by arg(3)
//    @State var listState: Parcelable? = null

    lateinit var albums: BroadcastChannel<List<Album>>
//    val gridSize by objFilePref(3)

    companion object {
        fun all() = AlbumsFragment().apply {
            category = Category.All
        }

        fun fromChan(
            channel: ReceiveChannel<List<Album>>,
            sortBy: SortBy? = null,
            columnCount: Int? = null
        ) = AlbumsFragment().apply {
            category = Category.All
            if (sortBy != null) {
                this.sortBy = sortBy
            }
            if (columnCount != null) {
                this.columnCount = columnCount
            }
            albums = channel.broadcast(CONFLATED)
        }

        fun byArtist(artistId: ArtistId, artist: ReceiveChannel<Artist>) = AlbumsFragment().apply {
            category = Category.ByArtist(artistId, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE).apply {
                artistChannel = artist
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (!::albums.isInitialized) {
            info { "Grabbing new albums channel $category" }
            albums = category.channel
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater?) = menu.run {
        menuItem(R.string.search, R.drawable.ic_search, showIcon =true).onClick(UI) {
            act.replaceMainContent(
                SearchFragment.newInstance(SearchFragment.Category.Albums()),
                true
            )
        }

        subMenu(R.string.set_grid_size) {
            group(0, true, true) {
                val items = (1..4).map { idx ->
                    menuItem(idx.toString()).apply {
                        onClick(UI) { UserPrefs.albumGridColumns puts idx }
                    }
                }

                UserPrefs.albumGridColumns.consumeEach(UI) { count ->
                    items.forEach { it.isChecked = false }
                    items[count - 1].isChecked = true
                }
            }
        }
    }

    fun byArtist(vm: ViewManager, artistId: ArtistId, artist: ReceiveChannel<Artist>) {
        category = Category.ByArtist(artistId, ArtistDetailsFragment.Mode.LIBRARY_AND_REMOTE).apply {
            this.artistChannel = artist
        }
        albums = category.channel
        vm.createView()
    }

    // override fun ViewManager.createView() = frameLayout {
    //     id = R.id.container
    //     val cat = category
    //     val grid = GridLayoutManager(context, 3)

    //     val adapter = /*if (cat is Category.ByArtist) {
    //         AlbumSectionAdapter { view, album ->
    //             listState = grid.onSaveInstanceState()
    //             view.itemView.context.replaceMainContent(
    //                 DetailsFragment.fromAlbum(album),
    //                 true,
    //                 view.transitionViews
    //             )
    //         }.apply {
    //             setLayoutManager(grid)
    //         }
    //     } else {*/
    //         AlbumsAdapter(false) { view, album ->
    //             listState = grid.onSaveInstanceState()
    //             view.itemView.context.replaceMainContent(
    //                 DetailsFragment.fromAlbum(album),
    //                 true,
    //                 view.transitionViews
    //             )
    //         }
    //     //}
    override fun ViewManager.createView() =
        albumList(albums, category, sortBy, columnCount) {
            id = R.id.gridRecycler
        }
}

fun ViewManager.albumList(
    albums: BroadcastChannel<List<Album>>,
    category: AlbumsFragment.Category,
    sortBy: AlbumsFragment.SortBy,
    columnCount: Int,
    block: RecyclerView.() -> Unit = {}
) = swipeRefreshLayout {
    val scope = ViewScope(this)

    isEnabled = false

    val grid = GridLayoutManager(context, 3)

    val goToAlbum = { item: RecyclerItem, album: Album ->
        item.itemView.context.replaceMainContent(
            DetailsFragment.fromAlbum(album),
            true,
            item.transitionViews
        )
    }
    val adapter: RecyclerView.Adapter<*> =
//        if (category is AlbumsFragment.Category.ByArtist) {
//            AlbumSectionAdapter(goToAlbum).apply {
//                setLayoutManager(grid)
//            }
//        } else {
            AlbumsAdapter(category is AlbumsFragment.Category.ByArtist, goToAlbum)
//        }

    val recycler = if (category is AlbumsFragment.Category.All) {
        fastScrollRecycler {
            turntableStyle()
        }
    } else recyclerView {
        turntableStyle()
    }


    recycler.apply {
        this.adapter = adapter

        // if (listState != null) {
        //     grid.onRestoreInstanceState(listState)
        // }

        layoutManager = grid
        padding = dimen(R.dimen.grid_gutter)

        if (columnCount > 0) {
            grid.spanCount = columnCount
        } else {
            scope.launch {
                UserPrefs.albumGridColumns.openSubscription()
                    .distinctSeq()
                    .consumeEach { grid.spanCount = it }
            }
        }

        addItemDecoration(ItemOffsetDecoration(dimen(R.dimen.grid_gutter)))

        val sub = albums.openSubscription()//.map {
//                if (cat !is Category.All) {
//                    when (sortBy) {
//                        SortBy.NONE -> it
//                        SortBy.TITLE -> it.sortedWith(compareByIgnoreCase({ it.id.sortName }))
//                        SortBy.YEAR -> it.sortedByDescending {
//                            it.year ?: 0
//                        }.sortedBy { it.type.ordinal }
//                    }
//                } else it
//            }

        if (adapter is AlbumsAdapter) {
            adapter.subscribeData(sub)
        } else if (adapter is AlbumSectionAdapter) {
            scope.launch {
                sub.consumeEach {
                    adapter.replaceData(it)
                }
            }
        }

        block()
    }

    scope.launch {
        albums.consume {
            if (isEmpty) {
                isRefreshing = true
                receive()
                isRefreshing = false
            }
        }
    }
}
