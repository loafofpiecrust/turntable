package com.loafofpiecrust.turntable.album

//import org.musicbrainz.controller.Controller

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.Menu
import com.loafofpiecrust.turntable.R
import com.loafofpiecrust.turntable.artist.ArtistDetailsUI
import com.loafofpiecrust.turntable.repository.local.LocalApi
import com.loafofpiecrust.turntable.repository.Repository
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.artist.Artist
import com.loafofpiecrust.turntable.model.artist.ArtistId
import com.loafofpiecrust.turntable.model.artist.LocalArtist
import com.loafofpiecrust.turntable.model.artist.MergedArtist
import com.loafofpiecrust.turntable.prefs.UserPrefs
import com.loafofpiecrust.turntable.puts
import com.loafofpiecrust.turntable.service.Library
import com.loafofpiecrust.turntable.style.turntableStyle
import com.loafofpiecrust.turntable.ui.*
import com.loafofpiecrust.turntable.util.*
import com.loafofpiecrust.turntable.views.ItemOffsetDecoration
import com.loafofpiecrust.turntable.views.RecyclerItem
import kotlinx.android.parcel.IgnoredOnParcel
import kotlinx.android.parcel.Parcelize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.CONFLATED
import kotlinx.coroutines.launch
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.dimen
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.swipeRefreshLayout


sealed class AlbumsUI(
    private val splitByType: Boolean = false,
    private val sortBy: SortBy? = null,
    private val columnCount: Int = 3
) : UIComponent() {
    abstract val albums: ReceiveChannel<List<Album>>
    private val displayAlbums by lazy {
        if (sortBy != null) {
            albums.map { it.sortedWith(sortBy.comparator) }
        } else {
            albums
        }.broadcast(CONFLATED)
    }

    override fun CoroutineScope.render(ui: AnkoContext<Any>) = ui.swipeRefreshLayout {
        isEnabled = false

        val grid = GridLayoutManager(context, 3)

        val goToAlbum = { item: RecyclerItem, album: Album ->
            item.itemView.context.replaceMainContent(
                AlbumDetailsUI.Resolved(album).createFragment(),
                true,
                item.transitionViews
            )
        }
        val adapter: RecyclerView.Adapter<*> =
            if (splitByType) {
                AlbumAdapterSectioned(displayAlbums.openSubscription(), goToAlbum).apply {
                    layoutManager = grid
                }
            } else {
                AlbumsAdapter(displayAlbums.openSubscription(), this@AlbumsUI is ByArtist, goToAlbum)
            }

        val recycler =
//            if (this@AlbumsUI is All) {
//                fastScrollRecycler {
//                    turntableStyle()
//                }
//            } else
            recyclerView {
                turntableStyle()
            }


        recycler.apply {
            layoutManager = grid
            this.adapter = adapter

            // if (listState != null) {
            //     grid.onRestoreInstanceState(listState)
            // }

            padding = dimen(R.dimen.grid_gutter)

            if (columnCount > 0) {
                grid.spanCount = columnCount
            } else {
                launch {
                    UserPrefs.albumGridColumns.openSubscription()
                        .distinctSeq()
                        .consumeEach { grid.spanCount = it }
                }
            }

            addItemDecoration(ItemOffsetDecoration(dimen(R.dimen.grid_gutter)))
        }

        launch {
            val chan = displayAlbums.openSubscription()
            if (chan.isEmpty) {
                isRefreshing = true
            }
            chan.consumeEach {
                isRefreshing = false
            }
        }
    }

    override fun Menu.prepareOptions(context: Context) {
        menuItem(R.string.search, R.drawable.ic_search, showIcon =true).onClick {
            context.replaceMainContent(
                SearchFragment.newInstance(SearchFragment.Category.Albums()),
                true
            )
        }

        subMenu(R.string.set_grid_size) {
            group(0, true, true) {
                val items = (1..4).map { idx ->
                    menuItem(idx.toString()).apply {
                        onClick(Dispatchers.Main) { UserPrefs.albumGridColumns puts idx }
                    }
                }

                UserPrefs.albumGridColumns.consumeEach(Dispatchers.Main) { count ->
                    items.forEach { it.isChecked = false }
                    items[count - 1].isChecked = true
                }
            }
        }
    }


    @Parcelize
    class All : AlbumsUI(), Parcelable {
        override val albums get() =
            Library.instance.albumsMap.openSubscription().map {
                it.values.sortedBy { it.id }
            }
    }
    // TODO: Add OnPlaylist type.
    @Parcelize
    class ByArtist(
        val artist: ArtistId,
        val mode: ArtistDetailsUI.Mode
    ): AlbumsUI(sortBy = SortBy.YEAR), Parcelable {
        @IgnoredOnParcel
        var artistChannel: ReceiveChannel<Artist>? = null

        override val albums get() =
            (artistChannel ?: Library.instance.findArtist(artist)
                .map(Dispatchers.IO) {
                    if (it is LocalArtist) {
                        when (mode) {
                            ArtistDetailsUI.Mode.LIBRARY -> it
                            ArtistDetailsUI.Mode.LIBRARY_AND_REMOTE ->
                                Repository.find(artist)?.let { remote -> MergedArtist(it, remote) } ?: it
                            ArtistDetailsUI.Mode.REMOTE -> it // use case?
                        }
                    } else {
                        when (mode) {
                            ArtistDetailsUI.Mode.LIBRARY -> it
                            ArtistDetailsUI.Mode.LIBRARY_AND_REMOTE ->
                                LocalApi.find(artist)?.let { local -> MergedArtist(it!!, local) } ?: it
                            ArtistDetailsUI.Mode.REMOTE -> it // use case?
                        }
                    }
                })
                .map(Dispatchers.IO) { it?.albums ?: emptyList() }
    }

    class Custom(
        override val albums: ReceiveChannel<List<Album>>,
        splitByType: Boolean = false,
        sortBy: SortBy? = null
    ): AlbumsUI(splitByType, sortBy)


    enum class SortBy(val comparator: Comparator<Album>) {
        YEAR(compareByDescending { it.year }),
        TITLE(compareBy { it.id });
    }
}