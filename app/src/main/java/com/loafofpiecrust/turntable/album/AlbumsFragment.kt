package com.loafofpiecrust.turntable.album

//import org.musicbrainz.controller.Controller

import android.content.Context
import android.os.Parcelable
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.*
import com.loafofpiecrust.turntable.*
import com.loafofpiecrust.turntable.artist.*
import com.loafofpiecrust.turntable.browse.LocalApi
import com.loafofpiecrust.turntable.browse.Repository
import com.loafofpiecrust.turntable.model.album.Album
import com.loafofpiecrust.turntable.model.album.AlbumId
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
import kotlinx.coroutines.experimental.launch
import org.jetbrains.anko.AnkoContext
import org.jetbrains.anko.dimen
import org.jetbrains.anko.padding
import org.jetbrains.anko.recyclerview.v7.recyclerView
import org.jetbrains.anko.support.v4.swipeRefreshLayout
import kotlin.Comparator


sealed class AlbumsUI(
    private val splitByType: Boolean = false,
    private val sortBy: SortBy? = null,
    private val columnCount: Int = 3
) : UIComponent() {
    abstract val albums: BroadcastChannel<List<Album>>

    override fun AnkoContext<Any>.render() = swipeRefreshLayout {
        val scope = ViewScope(this)

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
                AlbumSectionAdapter(goToAlbum).apply {
                    setLayoutManager(grid)
                }
            } else {
                AlbumsAdapter(this@AlbumsUI is ByArtist, goToAlbum)
            }

        val recycler = if (this@AlbumsUI is All) {
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

            val sub = albums.openSubscription().map {
                if (sortBy != null) {
                    it.sortedWith(sortBy.comparator)
                } else it
            }

            if (adapter is AlbumsAdapter) {
                adapter.subscribeData(sub)
            } else if (adapter is AlbumSectionAdapter) {
                scope.launch {
                    sub.consumeEach {
                        adapter.replaceData(it)
                    }
                }
            }
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


    @Parcelize
    class All : AlbumsUI(), Parcelable {
        override val albums get() =
            Library.instance.albumsMap.openSubscription().map {
                it.values.sortedBy { it.id }
            }.replayOne()
    }
    // TODO: Add OnPlaylist type.
    @Parcelize
    class ByArtist(
        val artist: ArtistId,
        val mode: ArtistDetailsFragment.Mode
    ): AlbumsUI(sortBy = SortBy.YEAR), Parcelable {
        @IgnoredOnParcel
        var artistChannel: ReceiveChannel<Artist>? = null

        override val albums get() =
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

    class Custom(
        override val albums: BroadcastChannel<List<Album>>,
        splitByType: Boolean = false,
        sortBy: SortBy? = null
    ): AlbumsUI(splitByType, sortBy)


    enum class SortBy(val comparator: Comparator<Album>) {
        YEAR(compareByDescending { it.year }),
        TITLE(compareBy { it.id });
    }
}